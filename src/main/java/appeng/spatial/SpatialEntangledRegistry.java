package appeng.spatial;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import appeng.api.networking.IGridNode;
import appeng.tile.spatial.TileSpatialLinkChamber;
import appeng.tile.spatial.TileSpatialNetworkRelay;

/**
 * Registry used to pair an overworld endpoint tile with one or more spatial storage dimensions.
 *
 * We keep a lightweight mapping from Spatial Storage dimension id -> grid storage id of the owning grid.
 */
public final class SpatialEntangledRegistry {

    private SpatialEntangledRegistry() {}

    private static final Multimap<Integer, IGridNode> DIM_TO_GRID_SLAVES = HashMultimap.create();
    private static final Map<Integer, IGridNode> DIM_TO_GRID_HOST = new ConcurrentHashMap<>();

    public static void registerSlave(final int storageDim, final IGridNode node) {
        if (storageDim == 0 || node == null) {
            return;
        }
        DIM_TO_GRID_SLAVES.put(storageDim, node);
    }

    public static void unregisterSlave(final int storageDim, final IGridNode node) {
        if (storageDim == 0 || node == null) {
            return;
        }
        DIM_TO_GRID_SLAVES.remove(storageDim, node);
    }

    public static void updateSlaves(final int dimension) {
        for (Iterator<IGridNode> iterator = DIM_TO_GRID_SLAVES.get(dimension).iterator(); iterator.hasNext();) {
            IGridNode slaveNode = iterator.next();
            try {
                if (slaveNode.getMachine() instanceof TileSpatialNetworkRelay anchor) {
                    anchor.updateStatus();
                } else {
                    iterator.remove();
                }
            } catch (final Exception e) {
                // Ignore, will retry later.
            }
        }
    }

    public static void bindHost(final int storageDim, final IGridNode node) {
        if (storageDim == 0 || node == null) {
            return;
        }

        DIM_TO_GRID_HOST.put(storageDim, node);
        updateSlaves(storageDim);
    }

    public static void unbindHost(final int storageDim) {
        DIM_TO_GRID_HOST.remove(storageDim);
        updateSlaves(storageDim);
    }

    public static void teleportPlayerOut(EntityPlayerMP playerMP, int storageDim) {
        if (storageDim == 0) {
            return;
        }
        IGridNode hostNode = findHostNode(storageDim);
        if (hostNode != null && hostNode.getMachine() instanceof TileSpatialLinkChamber anchor) {
            anchor.teleportOutside(playerMP);
        }

    }

    public static @Nullable IGridNode findHostNode(final int storageDim) {
        return DIM_TO_GRID_HOST.get(storageDim);
    }
}
