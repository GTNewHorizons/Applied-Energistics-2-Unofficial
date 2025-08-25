/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me;

import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridNotification;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridConnectionVisitor;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridVisitor;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IReadOnlyCollection;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.worlddata.WorldData;
import appeng.hooks.TickHandler;
import appeng.me.cache.CraftingGridCache;
import appeng.me.pathfinding.IPathItem;
import appeng.tile.networking.TileController;
import appeng.util.IWorldCallable;
import appeng.util.ReadOnlyCollection;

public class GridNode implements IGridNode, IPathItem {

    private static final MENetworkChannelsChanged EVENT = new MENetworkChannelsChanged();
    private static final int[] CHANNEL_COUNT = { 0, 8, 32, Integer.MAX_VALUE };

    private final List<GridConnection> connections = new LinkedList<>();
    private final IGridBlock gridProxy;
    // old power draw, used to diff
    private double previousDraw = 0.0;
    private long lastSecurityKey = -1;
    private int playerID = -1;
    private GridStorage myStorage = null;
    private Grid myGrid;
    private Object visitorIterationNumber = null;
    // connection criteria
    private int compressedData = 0;
    /**
     * Will be modified during pathing and should not be exposed outside of that purpose.
     */
    int usedChannels = 0;
    /**
     * Finalized version of {@link #usedChannels} once pathing is done.
     */
    private int lastUsedChannels = 0;
    /**
     * The nearest ancestor of this node which restricts the number of maximum available channels for its subtree. It is
     * {@code null} if the next node is a controller.
     * <p>
     * Used to quickly walk the path to the controller when checking channel assignability, based on the observation
     * that the max channel count increases as we get to the controller, and that we only need to check the highest node
     * of each max channel count.
     * <p>
     * For example, on the following path:
     * {@code controller - dense cable 1 - dense cable 2 - dense cable 3 - cable 1 - cable 2 - cable 3 - device}, we
     * need to check that {@code dense cable 1} can accept the additional channel. If this is true then dense cables
     * {@code 2} and {@code 3} can always accept it. Same for regular cables, so it is enough to check that
     * {@code dense cable 1} and {@code cable 1} can accept it, massively speeding up the assignment for large trees.
     */
    @Nullable
    private GridNode highestSimilarAncestor = null;
    private int subtreeMaxChannels;
    private boolean subtreeAllowsCompressedChannels;

    public GridNode(final IGridBlock what) {
        this.gridProxy = what;
    }

    IGridBlock getGridProxy() {
        return this.gridProxy;
    }

    Grid getMyGrid() {
        return this.myGrid;
    }

    public int usedChannels() {
        return this.lastUsedChannels;
    }

    Class<? extends IGridHost> getMachineClass() {
        return this.getMachine().getClass();
    }

    void addConnection(final IGridConnection gridConnection) {
        this.connections.add((GridConnection) gridConnection);
        if (gridConnection.hasDirection()) {
            this.gridProxy.onGridNotification(GridNotification.ConnectionsChanged);
        }
    }

    void removeConnection(final IGridConnection gridConnection) {
        this.connections.remove(gridConnection);
        if (gridConnection.hasDirection()) {
            this.gridProxy.onGridNotification(GridNotification.ConnectionsChanged);
        }
    }

    boolean hasConnection(final IGridNode otherSide) {
        for (final IGridConnection gc : this.connections) {
            if (gc.a() == otherSide || gc.b() == otherSide) {
                return true;
            }
        }
        return false;
    }

    void validateGrid() {
        final GridSplitDetector gsd = new GridSplitDetector(this.getInternalGrid().getPivot());
        this.beginVisit(gsd);
        if (!gsd.isPivotFound()) {
            final IGridVisitor gp = new GridPropagator(new Grid(this));
            this.beginVisit(gp);
        }
    }

    public Grid getInternalGrid() {
        if (this.myGrid == null) {
            this.myGrid = new Grid(this);
        }

        return this.myGrid;
    }

    @Override
    public void beginVisit(final IGridVisitor g) {
        final Object tracker = new Object();

        CraftingGridCache.pauseRebuilds();

        LinkedList<GridNode> nextRun = new LinkedList<>();
        nextRun.add(this);

        this.visitorIterationNumber = tracker;

        if (g instanceof IGridConnectionVisitor gcv) {
            final LinkedList<IGridConnection> nextConn = new LinkedList<>();

            while (!nextRun.isEmpty()) {
                while (!nextConn.isEmpty()) {
                    gcv.visitConnection(nextConn.poll());
                }

                final Iterable<GridNode> thisRun = nextRun;
                nextRun = new LinkedList<>();

                for (final GridNode n : thisRun) {
                    n.visitorConnection(tracker, g, nextRun, nextConn);
                }
            }
        } else {
            while (!nextRun.isEmpty()) {
                final Iterable<GridNode> thisRun = nextRun;
                nextRun = new LinkedList<>();

                for (final GridNode n : thisRun) {
                    n.visitorNode(tracker, g, nextRun);
                }
            }
        }

        CraftingGridCache.unpauseRebuilds();
    }

    @Override
    public void updateState() {
        this.compressedData = getCompressedChannelsIndex();

        this.compressedData |= (this.gridProxy.getGridColor().ordinal() << 3);

        for (final ForgeDirection dir : this.gridProxy.getConnectableSides()) {
            this.compressedData |= (1 << (dir.ordinal() + 8));
        }

        this.FindConnections();
        this.getInternalGrid();
    }

    private int getCompressedChannelsIndex() {
        if (!AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) return 3;
        else if (hasFlag(GridFlags.CANNOT_CARRY)) return 0;
        else if (hasFlag(GridFlags.DENSE_CAPACITY)) return 2;
        return 1;
    }

    @Override
    public IGridHost getMachine() {
        return this.gridProxy.getMachine();
    }

    @Override
    public IGrid getGrid() {
        return this.myGrid;
    }

    void setGrid(final Grid grid) {
        if (this.myGrid == grid) {
            return;
        }

        if (this.myGrid != null) {
            this.myGrid.remove(this);

            if (this.myGrid.isEmpty()) {
                this.myGrid.saveState();

                for (final IGridCache c : grid.getCaches().values()) {
                    c.onJoin(this.myGrid.getMyStorage());
                }
            }
        }

        this.myGrid = grid;
        this.myGrid.add(this);
    }

    @Override
    public void destroy() {

        while (!this.connections.isEmpty()) {
            // not part of this network for real anymore.
            if (this.connections.size() == 1) {
                this.setGridStorage(null);
            }

            final IGridConnection c = this.connections.listIterator().next();
            final GridNode otherSide = (GridNode) c.getOtherSide(this);
            otherSide.getInternalGrid().setPivot(otherSide);
            c.destroy();
        }

        if (this.myGrid != null) {
            this.myGrid.remove(this);
        }
    }

    @Override
    public World getWorld() {
        return this.gridProxy.getLocation().getWorld();
    }

    @Override
    public EnumSet<ForgeDirection> getConnectedSides() {
        final EnumSet<ForgeDirection> set = EnumSet.noneOf(ForgeDirection.class);
        for (final IGridConnection gc : this.connections) {
            set.add(gc.getDirection(this));
        }
        return set;
    }

    @Override
    public IReadOnlyCollection<IGridConnection> getConnections() {
        return new ReadOnlyCollection<>(this.connections);
    }

    public boolean hasNoConnections() {
        return this.connections.isEmpty();
    }

    @Override
    public IGridBlock getGridBlock() {
        return this.gridProxy;
    }

    @Override
    public boolean isActive() {
        final IGrid g = this.getGrid();
        if (g != null) {
            final IPathingGrid pg = g.getCache(IPathingGrid.class);
            final IEnergyGrid eg = g.getCache(IEnergyGrid.class);
            return this.meetsChannelRequirements() && eg.isNetworkPowered() && !pg.isNetworkBooting();
        }
        return false;
    }

    @Override
    public void loadFromNBT(final String name, final NBTTagCompound nodeData) {
        if (this.myGrid == null) {
            final NBTTagCompound node = nodeData.getCompoundTag(name);
            this.playerID = node.getInteger("p");
            this.setLastSecurityKey(node.getLong("k"));

            final long storageID = node.getLong("g");
            final GridStorage gridStorage = WorldData.instance().storageData().getGridStorage(storageID);
            this.setGridStorage(gridStorage);
        } else {
            throw new IllegalStateException("Loading data after part of a grid, this is invalid.");
        }
    }

    @Override
    public void saveToNBT(final String name, final NBTTagCompound nodeData) {
        if (this.myStorage != null) {
            final NBTTagCompound node = new NBTTagCompound();

            node.setInteger("p", this.playerID);
            node.setLong("k", this.getLastSecurityKey());
            node.setLong("g", this.myStorage.getID());

            nodeData.setTag(name, node);
        } else {
            nodeData.removeTag(name);
        }
    }

    @Override
    public boolean meetsChannelRequirements() {
        return !hasFlag(GridFlags.REQUIRE_CHANNEL) || this.getUsedChannels() > 0;
    }

    @Override
    public boolean hasFlag(final GridFlags flag) {
        return this.gridProxy.hasFlag(flag);
    }

    @Override
    public int getPlayerID() {
        return this.playerID;
    }

    @Override
    public void setPlayerID(final int playerID) {
        if (playerID >= 0) {
            this.playerID = playerID;
        }
    }

    public int getUsedChannels() {
        return this.lastUsedChannels;
    }

    private void FindConnections() {
        if (!this.gridProxy.isWorldAccessible()) {
            return;
        }

        final EnumSet<ForgeDirection> newSecurityConnections = EnumSet.noneOf(ForgeDirection.class);

        final DimensionalCoord dc = this.gridProxy.getLocation();
        for (final ForgeDirection f : ForgeDirection.VALID_DIRECTIONS) {
            final IGridHost te = this.findGridHost(dc.getWorld(), dc.x + f.offsetX, dc.y + f.offsetY, dc.z + f.offsetZ);
            if (te != null) {
                final GridNode node = (GridNode) te.getGridNode(f.getOpposite());
                if (node == null) {
                    continue;
                }

                final boolean isValidConnection = this.canConnect(node, f) && node.canConnect(this, f.getOpposite());

                IGridConnection con = null; // find the connection for this
                // direction..
                for (final IGridConnection c : this.getConnections()) {
                    if (c.getDirection(this) == f) {
                        con = c;
                        break;
                    }
                }

                if (con != null) {
                    final IGridNode os = con.getOtherSide(this);
                    if (os == node) {
                        // if this connection is no longer valid, destroy it.
                        if (!isValidConnection) {
                            con.destroy();
                        }
                    } else {
                        con.destroy();
                        // throw new GridException( "invalid state found, encountered connection to phantom block." );
                    }
                } else if (isValidConnection) {
                    if (node.getLastSecurityKey() != -1) {
                        newSecurityConnections.add(f);
                    } else {
                        // construct a new connection between these two nodes.
                        try {
                            new GridConnection(node, this, f.getOpposite());
                        } catch (final FailedConnection e) {
                            TickHandler.INSTANCE.addCallable(node.getWorld(), new MachineSecurityBreak(this));

                            return;
                        }
                    }
                }
            }
        }

        for (final ForgeDirection f : newSecurityConnections) {
            final IGridHost te = this.findGridHost(dc.getWorld(), dc.x + f.offsetX, dc.y + f.offsetY, dc.z + f.offsetZ);
            if (te != null) {
                final GridNode node = (GridNode) te.getGridNode(f.getOpposite());
                if (node == null) {
                    continue;
                }

                // construct a new connection between these two nodes.
                try {
                    new GridConnection(node, this, f.getOpposite());
                } catch (final FailedConnection e) {
                    TickHandler.INSTANCE.addCallable(node.getWorld(), new MachineSecurityBreak(this));

                    return;
                }
            }
        }
    }

    private IGridHost findGridHost(final World world, final int x, final int y, final int z) {
        if (world.blockExists(x, y, z)) {
            final TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGridHost) {
                return (IGridHost) te;
            }
        }
        return null;
    }

    private boolean canConnect(final GridNode from, final ForgeDirection dir) {
        if (!this.isValidDirection(dir)) {
            return false;
        }
        return from.getColor().matches(this.getColor());
    }

    private boolean isValidDirection(final ForgeDirection dir) {
        return (this.compressedData & (1 << (8 + dir.ordinal()))) > 0;
    }

    private AEColor getColor() {
        return AEColor.values()[(this.compressedData >> 3) & 0x1F];
    }

    private void visitorConnection(final Object tracker, final IGridVisitor g, final Deque<GridNode> nextRun,
            final Deque<IGridConnection> nextConnections) {
        if (g.visitNode(this)) {
            for (final IGridConnection gc : this.getConnections()) {
                final GridNode gn = (GridNode) gc.getOtherSide(this);
                final GridConnection gcc = (GridConnection) gc;

                if (gcc.getVisitorIterationNumber() != tracker) {
                    gcc.setVisitorIterationNumber(tracker);
                    nextConnections.add(gc);
                }

                if (tracker == gn.visitorIterationNumber) {
                    continue;
                }

                gn.visitorIterationNumber = tracker;

                nextRun.add(gn);
            }
        }
    }

    private void visitorNode(final Object tracker, final IGridVisitor g, final Deque<GridNode> nextRun) {
        if (g.visitNode(this)) {
            for (final IGridConnection gc : this.getConnections()) {
                final GridNode gn = (GridNode) gc.getOtherSide(this);

                if (tracker == gn.visitorIterationNumber) {
                    continue;
                }

                gn.visitorIterationNumber = tracker;

                nextRun.add(gn);
            }
        }
    }

    GridStorage getGridStorage() {
        return this.myStorage;
    }

    void setGridStorage(final GridStorage s) {
        this.myStorage = s;
        this.usedChannels = 0;
        this.lastUsedChannels = 0;
    }

    @Override
    public void setAdHocChannels(int channels) {
        this.usedChannels = channels;
    }

    @Override
    public IPathItem getControllerRoute() {
        if (this.connections.isEmpty()) {
            throw new IllegalStateException(
                    String.format("Node %s has no connections, cannot have a controller route!", this));
        }

        return this.connections.get(0);
    }

    public @Nullable GridNode getHighestSimilarAncestor() {
        return highestSimilarAncestor;
    }

    public boolean getSubtreeAllowsCompressedChannels() {
        return subtreeAllowsCompressedChannels;
    }

    @Override
    public void setControllerRoute(final IPathItem fast) {
        this.usedChannels = 0;

        var nodeParent = (GridNode) fast.getControllerRoute();
        if (nodeParent.getMachine() instanceof TileController) {
            this.highestSimilarAncestor = null;
            this.subtreeMaxChannels = getMaxChannels();
            this.subtreeAllowsCompressedChannels = !hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED);
        } else {
            if (nodeParent.highestSimilarAncestor == null) {
                // Parent is connected to a controller, it is the bottleneck.
                this.highestSimilarAncestor = nodeParent;
            } else if (nodeParent.subtreeMaxChannels == nodeParent.highestSimilarAncestor.subtreeMaxChannels) {
                // Parent is not restricting the number of channels, go as high as possible.
                this.highestSimilarAncestor = nodeParent.highestSimilarAncestor;
            } else {
                // Parent is restricting the number of channels, link to it directly.
                this.highestSimilarAncestor = nodeParent;
            }
            this.subtreeMaxChannels = Math.min(nodeParent.subtreeMaxChannels, getMaxChannels());
            this.subtreeAllowsCompressedChannels = nodeParent.subtreeAllowsCompressedChannels
                    && !hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED);
        }

        GridConnection connection = (GridConnection) fast;

        final int idx = this.connections.indexOf(connection);
        if (idx > 0) {
            this.connections.remove(connection);
            this.connections.add(0, connection);
        }
    }

    public int getMaxChannels() {
        return CHANNEL_COUNT[this.compressedData & 0x3];
    }

    @Override
    public IReadOnlyCollection<IPathItem> getPossibleOptions() {
        return new ReadOnlyCollection<>(this.connections);
    }

    public int propagateChannelsUpwards(boolean consumesChannel) {
        this.usedChannels = 0;
        for (var connection : connections) {
            if (connection.getControllerRoute() == this) {
                this.usedChannels += connection.usedChannels;
            }
        }
        if (consumesChannel) {
            this.usedChannels++;
        }

        if (this.usedChannels > getMaxChannels()) {
            AELog.error(
                    "Internal channel assignment error. Grid node {} has {} channels passing through it but it only supports up to {}. Please open an issue on the AE2 repository.",
                    this,
                    this.usedChannels,
                    getMaxChannels());
        }

        return this.usedChannels;
    }

    @Override
    public void incrementChannelCount(final int usedChannels) {
        this.usedChannels += usedChannels;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return this.gridProxy.getFlags();
    }

    @Override
    public void finalizeChannels() {
        this.highestSimilarAncestor = null;

        if (hasFlag(GridFlags.CANNOT_CARRY)) {
            return;
        }

        if (this.lastUsedChannels != this.usedChannels) {
            this.lastUsedChannels = this.usedChannels;

            if (this.getInternalGrid() != null) {
                this.getInternalGrid().postEventTo(this, EVENT);
            }
        }
    }

    public long getLastSecurityKey() {
        return this.lastSecurityKey;
    }

    public void setLastSecurityKey(final long lastSecurityKey) {
        this.lastSecurityKey = lastSecurityKey;
    }

    public double getPreviousDraw() {
        return this.previousDraw;
    }

    public void setPreviousDraw(final double previousDraw) {
        this.previousDraw = previousDraw;
    }

    private static class MachineSecurityBreak implements IWorldCallable<Void> {

        private final GridNode node;

        public MachineSecurityBreak(final GridNode node) {
            this.node = node;
        }

        @Override
        public Void call(final World world) throws Exception {
            this.node.getMachine().securityBreak();

            return null;
        }
    }
}
