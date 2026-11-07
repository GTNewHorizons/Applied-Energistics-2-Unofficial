package appeng.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public final class NetworkChannelAvailability {

    private final ControllerState controllerState;
    private final boolean booting;
    private final boolean adHocChannelsUnavailable;
    private final Map<GridNode, Integer> upstreamRemaining = new IdentityHashMap<>();

    public NetworkChannelAvailability(IGrid grid) {
        final IPathingGrid pathing = grid.getCache(IPathingGrid.class);
        this.controllerState = pathing.getControllerState();
        this.booting = pathing.isNetworkBooting();
        boolean unavailable = false;
        if (this.controllerState == ControllerState.NO_CONTROLLER) {
            for (IGridNode node : grid.getNodes()) {
                if (node.hasFlag(GridFlags.REQUIRE_CHANNEL) && !node.meetsChannelRequirements()) {
                    // Failed ad-hoc allocations finalize every connection with zero used channels.
                    unavailable = true;
                    break;
                }
            }
        }
        this.adHocChannelsUnavailable = unavailable;
    }

    public int getFreeChannels(IGridConnection connection) {
        if (!AEConfig.instance.isFeatureEnabled(AEFeature.Channels) || booting) return -1;

        if (controllerState == ControllerState.NO_CONTROLLER) {
            // An ad-hoc network has eight channels shared by all of its connections, even dense ones.
            return adHocChannelsUnavailable ? 0
                    : Math.min(spare(connection), Math.max(0, 8 - connection.getUsedChannels()));
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
        Set<GridNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining;
        while (true) {
            Integer cached = upstreamRemaining.get(node);
            if (cached != null) {
                remaining = cached;
                break;
            }

            // Item updates can run after a topology change but before the server-end repath.
            if (!visited.add(node) || node.getConnections().isEmpty()) return -1;
            pending.add(node);
            GridConnection parentRoute = (GridConnection) node.getControllerRoute();
            if (parentRoute.b() != node) return -1;
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
        int capacity = Math.min(capacity(connection.a()), capacity(connection.b()));
        return Math.max(0, capacity - connection.getUsedChannels());
    }

    private static int capacity(IGridNode node) {
        // Controllers cannot carry channels internally, but each outgoing face supplies 32.
        return isController(node) ? 32 : ((GridNode) node).getMaxChannels();
    }

    private static boolean isController(IGridNode node) {
        return node.getMachine() instanceof TileController || node.getMachine() instanceof TileCreativeEnergyController;
    }
}
