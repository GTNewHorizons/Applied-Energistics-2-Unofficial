/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.Level;

import com.google.common.base.Preconditions;

import appeng.api.exceptions.ExistingConnectionException;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IReadOnlyCollection;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.me.pathfinding.IPathItem;
import appeng.util.Platform;
import appeng.util.ReadOnlyCollection;

public class GridConnection implements IGridConnection, IPathItem {

    private static final String EXISTING_CONNECTION_MESSAGE = "Connection between node [machine=%s, %s] and [machine=%s, %s] on [%s] already exists.";

    private static final MENetworkChannelsChanged EVENT = new MENetworkChannelsChanged();
    /**
     * Will be modified during pathing and should not be exposed outside of that purpose.
     */
    int usedChannels = 0;
    /**
     * Finalized version of {@link #usedChannels} once pathing is done.
     */
    private int lastUsedChannels = 0;
    private Object visitorIterationNumber = null;
    /**
     * Note that in grids with a controller, following this side will always lead down the closest path towards the
     * controller.
     */
    private GridNode sideA;
    private ForgeDirection fromAtoB;
    private GridNode sideB;

    @Override
    public IGridNode getOtherSide(final IGridNode gridNode) {
        if (gridNode == this.sideA) {
            return this.sideB;
        }
        if (gridNode == this.sideB) {
            return this.sideA;
        }

        throw new GridException("Invalid Side of Connection");
    }

    @Override
    public ForgeDirection getDirection(final IGridNode side) {
        if (this.fromAtoB == ForgeDirection.UNKNOWN) {
            return this.fromAtoB;
        }

        if (this.sideA == side) {
            return this.fromAtoB;
        } else {
            return this.fromAtoB.getOpposite();
        }
    }

    @Override
    public void destroy() {
        if (AEConfig.instance.debugPathFinding) {
            final String aCoordinates = sideA.getGridBlock().getLocation().toString();
            final String bCoordinates = sideB.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by destroying connection from [%s] to [%s]", aCoordinates, bCoordinates);
            AELog.printStackTrace(Level.INFO);
        }

        // a connection was destroyed RE-PATH!!
        final IPathingGrid p = this.sideA.getInternalGrid().getCache(IPathingGrid.class);
        p.repath();

        this.sideA.removeConnection(this);
        this.sideB.removeConnection(this);

        this.sideA.validateGrid();
        this.sideB.validateGrid();
    }

    @Override
    public GridNode a() {
        return this.sideA;
    }

    @Override
    public GridNode b() {
        return this.sideB;
    }

    @Override
    public boolean hasDirection() {
        return this.fromAtoB != ForgeDirection.UNKNOWN;
    }

    @Override
    public int getUsedChannels() {
        return lastUsedChannels;
    }

    @Override
    public void setAdHocChannels(int channels) {
        this.usedChannels = channels;
    }

    @Override
    public IPathItem getControllerRoute() {
        return this.sideA;
    }

    @Override
    public void setControllerRoute(final IPathItem fast) {
        this.usedChannels = 0;

        if (this.sideB == fast) {
            final GridNode tmp = this.sideA;
            this.sideA = this.sideB;
            this.sideB = tmp;
            this.fromAtoB = this.fromAtoB.getOpposite();
        }
    }

    @Override
    public IReadOnlyCollection<IPathItem> getPossibleOptions() {
        return new ReadOnlyCollection<>(this.a(), this.b());
    }

    @Override
    public void incrementChannelCount(final int usedChannels) {
        this.usedChannels += usedChannels;
    }

    @Override
    public EnumSet<GridFlags> getFlags() {
        return EnumSet.noneOf(GridFlags.class);
    }

    @Override
    public boolean hasFlag(GridFlags flag) {
        return false;
    }

    public int propagateChannelsUpwards() {
        if (this.sideB.getControllerRoute() == this) { // Check that we are in B's route
            this.usedChannels = this.sideB.usedChannels;
        } else {
            this.usedChannels = 0;
        }
        return this.usedChannels;
    }

    @Override
    public void finalizeChannels() {
        if (this.lastUsedChannels != this.usedChannels) {
            this.lastUsedChannels = this.usedChannels;

            if (this.sideA.getInternalGrid() != null) {
                this.sideA.getInternalGrid().postEventTo(this.sideA, EVENT);
            }

            if (this.sideB.getInternalGrid() != null) {
                this.sideB.getInternalGrid().postEventTo(this.sideB, EVENT);
            }
        }
    }

    Object getVisitorIterationNumber() {
        return this.visitorIterationNumber;
    }

    void setVisitorIterationNumber(final Object visitorIterationNumber) {
        this.visitorIterationNumber = visitorIterationNumber;
    }

    /**
     * @throws ExistingConnectionException If the nodes are already connected.
     */
    public GridConnection(IGridNode aNode, IGridNode bNode, @Nullable ForgeDirection fromAtoB)
            throws SecurityConnectionException, ExistingConnectionException {
        Objects.requireNonNull(aNode, "aNode");
        Objects.requireNonNull(bNode, "bNode");
        Preconditions.checkArgument(aNode != bNode, "Cannot connect node to itself");

        if (Platform.securityCheck((GridNode) aNode, (GridNode) bNode)) {
            if (AEConfig.instance.isFeatureEnabled(AEFeature.LogSecurityAudits)) {
                final DimensionalCoord aCoordinates = aNode.getGridBlock().getLocation();
                final DimensionalCoord bCoordinates = bNode.getGridBlock().getLocation();

                AELog.info(
                        "Security audit 1 failed at [%s] belonging to player [id=%d]",
                        aCoordinates.toString(),
                        aNode.getPlayerID());
                AELog.info(
                        "Security audit 2 failed at [%s] belonging to player [id=%d]",
                        bCoordinates.toString(),
                        bNode.getPlayerID());
            }

            throw new SecurityConnectionException();
        }

        var a = (GridNode) aNode;
        var b = (GridNode) bNode;

        if (a.hasConnection(b) || b.hasConnection(a)) {
            final String aMachineClass = a.getGridBlock().getMachine().getClass().getSimpleName();
            final String bMachineClass = b.getGridBlock().getMachine().getClass().getSimpleName();
            final String aCoordinates = a.getGridBlock().getLocation().toString();
            final String bCoordinates = b.getGridBlock().getLocation().toString();
            throw new ExistingConnectionException(
                    String.format(
                            EXISTING_CONNECTION_MESSAGE,
                            aMachineClass,
                            aCoordinates,
                            bMachineClass,
                            bCoordinates,
                            fromAtoB));
        }

        // Create the actual connection
        this.sideA = a;
        this.fromAtoB = fromAtoB == null ? ForgeDirection.UNKNOWN : fromAtoB;
        this.sideB = b;

        mergeGrids(a, b);

        // a connection was destroyed RE-PATH!!
        final IPathingGrid p = this.sideA.getInternalGrid().getCache(IPathingGrid.class);
        if (AEConfig.instance.debugPathFinding) {
            final String aCoordinates = a.getGridBlock().getLocation().toString();
            final String bCoordinates = b.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by adding connection from [%s] to [%s]", aCoordinates, bCoordinates);
            AELog.printStackTrace(Level.INFO);
        }
        p.repath();

        this.sideA.addConnection(this);
        this.sideB.addConnection(this);
    }

    /**
     * Merge the grids of two grid nodes based on both becoming connected. This method assumes that the new connection
     * is NOT yet created, otherwise grid propagation will do more work than needed.
     */
    private static void mergeGrids(GridNode a, GridNode b) {
        // Update both nodes with the new connection.
        var gridA = a.getMyGrid();
        var gridB = b.getMyGrid();
        if (gridA == null && gridB == null) {
            // Neither A nor B has a grid, create a new grid spanning both
            assertNodeIsStandalone(a);
            assertNodeIsStandalone(b);
            var grid = new Grid(a);
            a.setGrid(grid);
            b.setGrid(grid);
        } else if (gridA == null) {
            // Only node B has a grid, propagate it to A
            assertNodeIsStandalone(a);
            a.setGrid(gridB);
        } else if (gridB == null) {
            // Only node A has a grid, propagate it to B
            assertNodeIsStandalone(b);
            b.setGrid(gridA);
        } else if (gridA != gridB) {
            if (isGridABetterThanGridB(gridA, gridB)) {
                // Both A and B have grids, but A's grid is "better" -> propagate it to B and all its connected nodes
                var gp = new GridPropagator(a.getInternalGrid());
                b.beginVisit(gp);
            } else {
                // Both A and B have grids, but B's grid is "better" -> propagate it to A and all its connected nodes
                var gp = new GridPropagator(b.getInternalGrid());
                a.beginVisit(gp);
            }
        }
    }

    private static boolean isGridABetterThanGridB(Grid gridA, Grid gridB) {
        if (gridA.getPriority() != gridB.getPriority()) {
            return gridA.getPriority() > gridB.getPriority();
        }
        return gridA.size() >= gridB.size();
    }

    private static void assertNodeIsStandalone(GridNode node) {
        if (!node.hasNoConnections()) {
            throw new IllegalStateException(
                    "Grid node " + node + " has no grid, but is connected: " + node.getConnections());
        }
    }
}
