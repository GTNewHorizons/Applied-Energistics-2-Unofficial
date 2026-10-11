package appeng.items.tools;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.tile.networking.TileController;
import appeng.tile.networking.TileCreativeEnergyController;

/** Calculates how many more devices a connection can supply along its route to the controller. */
final class NetworkChannelAvailability {

    private final ControllerState controllerState;
    private final Map<GridNode, Integer> upstreamRemaining = new IdentityHashMap<>();

    NetworkChannelAvailability(IGrid grid) {
        this.controllerState = ((IPathingGrid) grid.getCache(IPathingGrid.class)).getControllerState();
    }

    int getFreeChannels(IGridConnection connection) {
        if (!AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) return -1;

        if (controllerState == ControllerState.NO_CONTROLLER) {
            // An ad-hoc network has eight channels shared by all of its connections, even dense ones.
            return Math.max(0, 8 - connection.getUsedChannels());
        }
        if (controllerState != ControllerState.CONTROLLER_ONLINE) return -1;

        GridConnection routed = (GridConnection) connection;
        if (routed.b().getControllerRoute() != routed) {
            // A non-tree connection carries no channels; its used count cannot describe available routes.
            return -1;
        }

        int remaining = spare(connection);
        GridNode parent = (GridNode) routed.getControllerRoute();
        return Math.min(remaining, remainingUpstream(parent, routed));
    }

    private int remainingUpstream(GridNode node, GridConnection route) {
        if (isController(node)) return remainingOnFace(node, route);

        List<GridNode> pending = new ArrayList<>();
        int remaining;
        while (true) {
            Integer cached = upstreamRemaining.get(node);
            if (cached != null) {
                remaining = cached;
                break;
            }

            pending.add(node);
            GridConnection parentRoute = (GridConnection) node.getControllerRoute();
            node = (GridNode) parentRoute.getControllerRoute();
            if (isController(node)) {
                remaining = remainingOnFace(node, parentRoute);
                break;
            }
        }

        for (int i = pending.size() - 1; i >= 0; i--) {
            GridNode current = pending.get(i);
            GridConnection parentRoute = (GridConnection) current.getControllerRoute();
            remaining = Math.min(remaining, Math.max(0, current.getMaxChannels() - current.getUsedChannels()));
            remaining = Math.min(remaining, spare(parentRoute));
            upstreamRemaining.put(current, remaining);
        }
        return remaining;
    }

    private static int remainingOnFace(GridNode controller, GridConnection route) {
        int used = 0;
        for (IGridConnection connection : controller.getConnections()) {
            if (connection.getDirection(controller) == route.getDirection(controller)) {
                used += connection.getUsedChannels();
            }
        }
        return Math.max(0, 32 - used);
    }

    private static int spare(IGridConnection connection) {
        int capacity = connection.a().hasFlag(GridFlags.DENSE_CAPACITY)
                && connection.b().hasFlag(GridFlags.DENSE_CAPACITY) ? 32 : 8;
        return Math.max(0, capacity - connection.getUsedChannels());
    }

    private static boolean isController(IGridNode node) {
        return node.getMachine() instanceof TileController || node.getMachine() instanceof TileCreativeEnergyController;
    }
}
