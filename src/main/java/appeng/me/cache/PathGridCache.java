/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import org.apache.logging.log4j.Level;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkChannelChanged;
import appeng.api.networking.events.MENetworkControllerChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.stats.Achievements;
import appeng.me.pathfinding.AdHocChannelUpdater;
import appeng.me.pathfinding.ChannelFinalizer;
import appeng.me.pathfinding.ControllerValidator;
import appeng.me.pathfinding.PathingCalculation;
import appeng.tile.networking.TileController;
import appeng.util.Platform;

public class PathGridCache implements IPathingGrid {

    private final Set<TileController> controllers = new HashSet<>();
    private final Set<IGridNode> nodesNeedingChannels = new HashSet<>();
    private final Set<IGridNode> cannotCarryCompressedNodes = new HashSet<>();
    private final IGrid myGrid;
    private int channelsInUse = 0;
    private int channelsByBlocks = 0;
    private double channelPowerUsage = 0.0;
    private boolean recalculateControllerNextTick = true;
    private boolean updateNetwork = true;
    private boolean booting = false;
    private ControllerState controllerState = ControllerState.NO_CONTROLLER;
    private int lastChannels = 0;

    public PathGridCache(final IGrid g) {
        this.myGrid = g;
    }

    @Override
    public void onUpdateTick() {
        if (this.recalculateControllerNextTick) {
            this.recalcController();
        }

        if (this.updateNetwork) {
            this.updateNetwork = false;

            // Preserve the illusion that the network is booting for a while before channel assignment completes.
            this.booting = true;
            this.myGrid.postEvent(new MENetworkBootingStatusChange(true));

            this.channelsInUse = 0;

            // updateControllerState / postBootingStatusChange called above can cause the grid to be destroyed,
            // and the pivot to become null.
            if (this.myGrid.isEmpty()) {
                return;
            }

            if (this.controllerState == ControllerState.NO_CONTROLLER) {
                // Returns 0 if there's an error
                this.channelsInUse = this.calculateAdHocChannels();

                final int nodes = this.myGrid.getNodes().size();

                this.channelsByBlocks = nodes * this.channelsInUse;
                this.setChannelPowerUsage(this.channelsByBlocks / 128.0);

                this.myGrid.getPivot().beginVisit(new AdHocChannelUpdater(this.channelsInUse));
            } else if (this.controllerState == ControllerState.CONTROLLER_CONFLICT) {
                this.myGrid.getPivot().beginVisit(new AdHocChannelUpdater(0));
                this.channelsInUse = 0;
                this.channelsByBlocks = 0;
            } else {
                var calculation = new PathingCalculation(myGrid);
                calculation.compute();
                this.channelsInUse = calculation.getChannelsInUse();
                this.channelsByBlocks = calculation.getChannelsByBlocks();
            }

            // check for achievements
            this.achievementPost();

            this.booting = false;
            this.setChannelPowerUsage(this.channelsByBlocks / 128.0);
            // Notify of channel changes AFTER we set booting to false, this ensures that any activeness check will
            // properly return true.
            this.myGrid.getPivot().beginVisit(new ChannelFinalizer());
            this.myGrid.postEvent(new MENetworkBootingStatusChange(this.booting));

        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by removing a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (machine instanceof TileController) {
            this.controllers.remove(machine);
            this.recalculateControllerNextTick = true;
        }

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.remove(gridNode);
        }

        if (gridNode.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.cannotCarryCompressedNodes.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by adding a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (machine instanceof TileController) {
            this.controllers.add((TileController) machine);
            this.recalculateControllerNextTick = true;
        }

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.add(gridNode);
        }

        if (gridNode.hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
            this.cannotCarryCompressedNodes.add(gridNode);
        }

        this.repath();
    }

    @Override
    public void onSplit(final IGridStorage storageB) {}

    @Override
    public void onJoin(final IGridStorage storageB) {}

    @Override
    public void populateGridStorage(final IGridStorage storage) {}

    private void recalcController() {
        this.recalculateControllerNextTick = false;
        final ControllerState old = this.controllerState;

        if (this.controllers.isEmpty()) {
            this.controllerState = ControllerState.NO_CONTROLLER;
        } else {
            final IGridNode startingNode = this.controllers.iterator().next().getGridNode(ForgeDirection.UNKNOWN);
            if (startingNode == null) {
                this.controllerState = ControllerState.CONTROLLER_CONFLICT;
                return;
            }

            final DimensionalCoord dc = startingNode.getGridBlock().getLocation();
            final ControllerValidator cv = new ControllerValidator(dc.x, dc.y, dc.z);

            startingNode.beginVisit(cv);

            if (cv.isValid() && cv.getFound() == this.controllers.size()) {
                this.controllerState = ControllerState.CONTROLLER_ONLINE;
            } else {
                this.controllerState = ControllerState.CONTROLLER_CONFLICT;
            }
        }

        if (old != this.controllerState) {
            this.myGrid.postEvent(new MENetworkControllerChange());
        }
    }

    private final HashSet<IGridNode> ignoredNode = new HashSet<>();

    private int calculateAdHocChannels() {
        this.ignoredNode.clear();

        final int maxChannels = AEConfig.instance.isFeatureEnabled(AEFeature.Channels) ? 8 : Integer.MAX_VALUE;

        int channels = 0;
        for (final IGridNode node : this.nodesNeedingChannels) {
            if (!this.ignoredNode.contains(node)) {
                // Prevent ad-hoc networks from being connected to the outside and inside node of P2P tunnels at the
                // same time
                // this effectively prevents the nesting of P2P-tunnels in ad-hoc networks.
                if (node.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !this.cannotCarryCompressedNodes.isEmpty()) {
                    return 0;
                }

                channels++;

                // return early if we exceed the max channels
                if (channels > maxChannels) {
                    return 0;
                }

                // Multiblocks only require a single channel. Add the remainder of the multi-block to the ignore-list,
                // to make this method skip them for channel calculation.
                if (node.hasFlag(GridFlags.MULTIBLOCK)) {
                    final IGridMultiblock gmb = (IGridMultiblock) node.getGridBlock();
                    final Iterator<IGridNode> i = gmb.getMultiblockNodes();
                    while (i.hasNext()) {
                        this.ignoredNode.add(i.next());
                    }
                }
            }
        }

        return channels;
    }

    private void achievementPost() {
        if (this.lastChannels != this.channelsInUse && AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) {
            final Achievements currentBracket = this.getAchievementBracket(this.channelsInUse);
            final Achievements lastBracket = this.getAchievementBracket(this.lastChannels);
            if (currentBracket != lastBracket && currentBracket != null) {
                final Set<Integer> players = new HashSet<>();
                for (final IGridNode n : this.nodesNeedingChannels) {
                    players.add(n.getPlayerID());
                }

                for (final int id : players) {
                    Platform.addStat(id, currentBracket.getAchievement());
                }
            }
        }
        this.lastChannels = this.channelsInUse;
    }

    private Achievements getAchievementBracket(final int ch) {
        if (ch < 8) {
            return null;
        }

        if (ch < 128) {
            return Achievements.Networking1;
        }

        if (ch < 2048) {
            return Achievements.Networking2;
        }

        return Achievements.Networking3;
    }

    @MENetworkEventSubscribe
    public void updateNodReq(final MENetworkChannelChanged ev) {
        final IGridNode gridNode = ev.node;

        if (AEConfig.instance.debugPathFinding) {
            final String coordinates = gridNode.getGridBlock().getLocation().toString();
            AELog.info("Repath is triggered by changing a node at [%s]", coordinates);
            AELog.printStackTrace(Level.INFO);
        }

        if (gridNode.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
            this.nodesNeedingChannels.add(gridNode);
        } else {
            this.nodesNeedingChannels.remove(gridNode);
        }

        this.repath();
    }

    @Override
    public boolean isNetworkBooting() {
        return this.booting;
    }

    @Override
    public ControllerState getControllerState() {
        return this.controllerState;
    }

    @Override
    public void repath() {
        this.channelsByBlocks = 0;
        this.updateNetwork = true;
    }

    double getChannelPowerUsage() {
        return this.channelPowerUsage;
    }

    private void setChannelPowerUsage(final double channelPowerUsage) {
        this.channelPowerUsage = channelPowerUsage;
    }
}
