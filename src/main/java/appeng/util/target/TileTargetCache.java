package appeng.util.target;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import appeng.util.Platform;

/**
 * Resolves the tile adjacent to one side of a part, and keeps returning the same {@link TileTarget} while it stays
 * valid. Owned by one part.
 * <p>
 * Neither neighbor block updates nor chunk loads are observed here. The owner reports neighbor updates with
 * {@link #markStale()}. An owner that must notice a chunk load keeps calling {@link #get} while
 * {@link #isWaitingForChunk()} is true.
 * <p>
 * An absent target in a loaded chunk is cached as well. A tile that appears without a neighbor update, for example
 * through {@code setBlock} without flag 1 or {@code World#setTileEntity}, is found only after the next
 * {@link #markStale()}.
 */
public final class TileTargetCache {

    private @Nullable World world;
    private int x, y, z;
    private @Nullable TileTarget target;
    private boolean resolved;
    private boolean waitingForChunk;

    /**
     * Returns the current target, resolving it only when needed. The returned instance is the same as before unless the
     * tile, its double chest pair, or its chunk changed.
     */
    public @Nullable TileTarget get(TileEntity host, ForgeDirection side) {
        final World world = host.getWorldObj();
        if (world == null || host.isInvalid()) return null;
        final int x = host.xCoord + side.offsetX;
        final int y = host.yCoord + side.offsetY;
        final int z = host.zCoord + side.offsetZ;
        if (this.world != world || this.x != x || this.y != y || this.z != z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = null;
            this.resolved = false;
        }
        if (this.resolved && this.isCurrent()) return this.target;

        final TileEntity tile = Platform.getLoadedTileEntity(world, x, y, z);
        final TileTarget previous = this.target;
        if (previous != null && previous.tile() == tile && previous.isCompositionCurrent()) {
            // Keep the target, so that everything built on it stays valid.
            this.resolved = true;
            this.waitingForChunk = false;
            return previous;
        }
        this.target = TileTarget.capture(tile);
        this.waitingForChunk = this.target == null && Platform.getLoadedChunk(world, x, z) == null;
        this.resolved = !this.waitingForChunk;
        return this.target;
    }

    /**
     * True if the last {@link #get} found no target because the target's chunk is not loaded. The next {@link #get}
     * looks again.
     */
    public boolean isWaitingForChunk() {
        return this.waitingForChunk;
    }

    /**
     * False when the target returned by the last {@link #get} has expired or its double chest pair changed. A pending
     * {@link #markStale()} does not make this false.
     */
    public boolean isCurrent() {
        return this.target == null || this.target.isCompositionCurrent();
    }

    /** Resolves again on the next {@link #get}. An unchanged target keeps its identity. */
    public void markStale() {
        this.resolved = false;
    }
}
