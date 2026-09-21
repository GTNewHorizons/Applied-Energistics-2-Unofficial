/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.pathfinding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileCreativeEnergyController;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/**
 * Calculation to assign channels starting from the controllers. The full computation is split in two steps, each linear
 * time.
 * <p>
 * First, a BFS is performed starting from the controllers. This establishes a tree that connects all path items to a
 * controller. As nodes that require channels are visited, they are assigned a channel if possible. This is done by
 * checking the controller face and a few key nodes along the path.
 * <p>
 * Second, a DFS is performed to propagate the channel count upwards.
 */
public class PathingCalculation {

    private static final int CONTROLLER_FACE_CHANNELS = 32;

    private final IGrid grid;
    private final int controllerFaceCapacity;
    /**
     * Path items that are part of a multiblock that was already granted a channel.
     */
    private final Set<GridNode> multiblocksWithChannel = new HashSet<>();
    /**
     * The BFS queues: all the path items that need to be visited on the next tick. Dense queue is prioritized to have
     * the behavior of dense cables extending the controller faces, then cables, then normal devices.
     */
    private final Queue<IPathItem>[] queues = new Queue[] { new ArrayDeque<>(), // 0: dense cable queue
            new ArrayDeque<>(), // 1: normal cable queue
            new ArrayDeque<>() // 2: non-cable queue
    };
    /**
     * Path items that are either in a queue, or have been processed already.
     */
    private final Set<IPathItem> visited = new HashSet<>();
    /**
     * Tracks the number of channels assigned to each path item during the BFS pass. Only a few key nodes along any path
     * are checked and updated.
     */
    private final Reference2IntOpenHashMap<GridNode> channelBottlenecks = new Reference2IntOpenHashMap<>();
    /**
     * Associates every path item with the controller face that supplies its channels.
     */
    private final Map<IPathItem, ControllerFace> controllerFaces = new IdentityHashMap<>();
    /**
     * Nodes that have been granted a channel during the BFS pass.
     */
    private final Set<GridNode> channelNodes = new HashSet<>();
    /**
     * Tracks the total number of used channels.
     */
    private int channelsInUse = 0;
    /**
     * Tracks the total number of channels for each path item is using.
     */
    private int channelsByBlocks = 0;

    /**
     * Create a new pathing calculation from the passed grid.
     */
    public PathingCalculation(IGrid grid) {
        this.grid = grid;
        this.controllerFaceCapacity = AEConfig.instance.isFeatureEnabled(AEFeature.Channels) ? CONTROLLER_FACE_CHANNELS
                : Integer.MAX_VALUE;

        // Add every outgoing connection of the controllers (that doesn't point to another controller) to the list.
        for (IGridNode node : grid.getMachines(TileController.class)) {
            visited.add((IPathItem) node);
            final Map<ForgeDirection, ControllerFace> faces = new EnumMap<>(ForgeDirection.class);
            for (var gcc : node.getConnections()) {
                var gc = (GridConnection) gcc;
                if (!(gc.getOtherSide(node).getMachine() instanceof TileController)) {
                    controllerFaces.put(
                            gc,
                            faces.computeIfAbsent(
                                    gc.getDirection(node),
                                    ignored -> new ControllerFace(this.controllerFaceCapacity)));
                    enqueue(gc, 0);
                    gc.setControllerRoute((GridNode) node);
                }
            }
        }
        for (IGridNode node : grid.getMachines(TileCreativeEnergyController.class)) {
            visited.add((IPathItem) node);
            final Map<ForgeDirection, ControllerFace> faces = new EnumMap<>(ForgeDirection.class);
            for (var gcc : node.getConnections()) {
                var gc = (GridConnection) gcc;
                if (!(gc.getOtherSide(node).getMachine() instanceof TileController)) {
                    controllerFaces.put(
                            gc,
                            faces.computeIfAbsent(
                                    gc.getDirection(node),
                                    ignored -> new ControllerFace(this.controllerFaceCapacity)));
                    enqueue(gc, 0);
                    gc.setControllerRoute((GridNode) node);
                }
            }
        }
    }

    private void enqueue(IPathItem pathItem, int queueIndex) {
        visited.add(pathItem);

        int possibleIndex;

        if (pathItem instanceof GridConnection) {
            // Grid connection does not have flags, allow any queue.
            possibleIndex = 0;
        } else if (pathItem.hasFlag(GridFlags.DENSE_CAPACITY)) {
            // Dense queue if possible.
            possibleIndex = 0;
        } else if (pathItem.hasFlag(GridFlags.PREFERRED)) {
            // Cable queue if possible.
            possibleIndex = 1;
        } else {
            possibleIndex = 2;
        }

        int index = Math.max(possibleIndex, queueIndex);
        queues[index].add(pathItem);
    }

    public void compute() {
        // BFS pass
        for (int i = 0; i < 3; ++i) {
            processQueue(queues[i], i);
        }

        // DFS pass
        propagateAssignments();
    }

    private void processQueue(Queue<IPathItem> oldOpen, int queueIndex) {
        while (!oldOpen.isEmpty()) {
            IPathItem i = oldOpen.poll();
            for (IPathItem pi : i.getPossibleOptions()) {
                if (!this.visited.contains(pi)) {
                    // Set BFS parent.
                    pi.setControllerRoute(i);
                    this.controllerFaces.put(pi, this.controllerFaces.get(i));

                    if (pi.hasFlag(GridFlags.REQUIRE_CHANNEL)) {
                        if (!this.multiblocksWithChannel.contains(pi)) {
                            // Try to use the channel along the path.
                            boolean worked = tryUseChannel((GridNode) pi);

                            if (worked && pi.hasFlag(GridFlags.MULTIBLOCK)) {
                                var multiblock = (IGridMultiblock) ((IGridNode) pi).getGridBlock();
                                if (multiblock != null) {
                                    var oni = multiblock.getMultiblockNodes();
                                    while (oni.hasNext()) {
                                        final IGridNode otherNodes = oni.next();
                                        if (otherNodes == null) {
                                            // Only a log for now until addons are fixed too. See
                                            // https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/8295
                                            AELog.error(
                                                    "Skipping null node returned by grid multiblock node %s %s",
                                                    multiblock.getMachine().getClass().getName(),
                                                    multiblock.getLocation().toString());
                                        } else if (otherNodes != pi) {
                                            this.multiblocksWithChannel.add((GridNode) otherNodes);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    enqueue(pi, queueIndex);
                }
            }
        }
    }

    /**
     * Try to allocate a channel along the path from {@code start} to the controller.
     *
     * @return true if allocation was successful
     */
    private boolean tryUseChannel(GridNode start) {
        if (start.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !start.getSubtreeAllowsCompressedChannels()) {
            // Don't send a compressed channel through this item.
            return false;
        }

        final ControllerFace controllerFace = this.controllerFaces.get(start);
        if (!controllerFace.canUseChannel()) {
            return false;
        }

        // Check that the allocation is possible.
        GridNode pi = start;
        while (pi != null) {
            if (channelBottlenecks.getOrDefault(pi, 0) >= pi.getMaxChannels()) {
                return false;
            }

            pi = pi.getHighestSimilarAncestor();
        }

        // Allocate the channel along the path.
        pi = start;
        while (pi != null) {
            channelBottlenecks.addTo(pi, 1);
            pi = pi.getHighestSimilarAncestor();
        }

        controllerFace.useChannel();
        channelNodes.add(start);
        return true;
    }

    static final class ControllerFace {

        private final int capacity;
        private int usedChannels;

        ControllerFace(final int capacity) {
            this.capacity = capacity;
        }

        boolean canUseChannel() {
            return this.usedChannels < this.capacity;
        }

        void useChannel() {
            this.usedChannels++;
        }
    }

    private static final Object SUBTREE_END = new Object();

    /**
     * Propagates assignment to all nodes by performing a DFS. The implementation is iterative to avoid stack overflow.
     */
    private void propagateAssignments() {
        List<Object> stack = new ArrayList<>();
        Set<IPathItem> controllerNodes = new HashSet<>();

        for (IGridNode node : grid.getMachines(TileController.class)) {
            controllerNodes.add((IPathItem) node);
            for (var gcc : node.getConnections()) {
                var gc = (GridConnection) gcc;
                if (!(gc.getOtherSide(node).getMachine() instanceof TileController)) {
                    stack.add(gc);
                }
            }
        }
        for (IGridNode node : grid.getMachines(TileCreativeEnergyController.class)) {
            controllerNodes.add((IPathItem) node);
            for (var gcc : node.getConnections()) {
                var gc = (GridConnection) gcc;
                if (!(gc.getOtherSide(node).getMachine() instanceof TileController)) {
                    stack.add(gc);
                }
            }
        }

        while (!stack.isEmpty()) {
            Object current = stack.get(stack.size() - 1);
            if (current == SUBTREE_END) {
                stack.remove(stack.size() - 1);
                IPathItem item = (IPathItem) stack.remove(stack.size() - 1);
                // We have visited the entire subtree and can now propagate channels upwards.
                if (item instanceof GridNode node) {
                    boolean hasChannel = channelNodes.contains(item);
                    channelsByBlocks += node.propagateChannelsUpwards(hasChannel);
                    if (hasChannel) {
                        channelsInUse++;
                    }
                } else {
                    channelsByBlocks += ((GridConnection) item).propagateChannelsUpwards();
                }
            } else {
                stack.add(SUBTREE_END);
                for (var pi : ((IPathItem) current).getPossibleOptions()) {
                    // The neighbor could either be: a child, the parent, or in a different tree if it is closer to
                    // another controller. It is a child if we are its parent.
                    // We need to exclude controller nodes because their getControllerRoute() is nonsense.
                    if (!controllerNodes.contains(pi) && pi.getControllerRoute() == current) {
                        stack.add(pi);
                    }
                }
            }
        }

        // Give a channel to all nodes that are a part of a multiblock that was given a channel before.
        for (var multiblockNode : multiblocksWithChannel) {
            multiblockNode.incrementChannelCount(1);
        }
    }

    public int getChannelsInUse() {
        return channelsInUse;
    }

    public int getChannelsByBlocks() {
        return channelsByBlocks;
    }

}
