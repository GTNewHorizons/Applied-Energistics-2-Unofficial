package appeng.util.target;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import appeng.util.Platform;

/**
 * A tile entity resolved from a loaded chunk, together with the chunks it depends on. For a vanilla chest this also
 * fixes the other half of a double chest.
 * <p>
 * Once a target is found invalid, it is never valid again. Holders must resolve a new target instead.
 */
public final class TileTarget {

    private static final ForgeDirection[] CHEST_SIDES = { ForgeDirection.EAST, ForgeDirection.WEST,
            ForgeDirection.SOUTH, ForgeDirection.NORTH };
    private static final int NO_TICK = Integer.MIN_VALUE;

    private final TileEntity tile;
    private final @Nullable TileEntityChest otherChest;
    private final @Nullable IInventory chestInventory;
    private final Chunk[] chunks;
    private boolean expired;
    /** The server tick at which the double chest pair was last confirmed. */
    private int compositionCheckedTick = NO_TICK;

    private TileTarget(TileEntity tile, Chunk own, @Nullable TileEntityChest otherChest, @Nullable Chunk otherChunk) {
        this.tile = tile;
        this.otherChest = otherChest;
        this.chunks = otherChunk == null || otherChunk == own ? new Chunk[] { own } : new Chunk[] { own, otherChunk };
        if (tile instanceof TileEntityChest chest) {
            this.compositionCheckedTick = currentServerTick(chest);
            // Same slot order as vanilla's double chest: the half at the lower coordinate comes first.
            this.chestInventory = otherChest == null ? chest
                    : otherChest.xCoord < chest.xCoord || otherChest.zCoord < chest.zCoord
                            ? new InventoryLargeChest("", otherChest, chest)
                            : new InventoryLargeChest("", chest, otherChest);
        } else {
            this.chestInventory = null;
        }
    }

    /**
     * Resolves a tile that is currently part of a loaded chunk, without loading any chunk.
     *
     * @return null if the tile is invalid, detached from its world, or its chunk is not loaded
     */
    public static @Nullable TileTarget capture(@Nullable TileEntity tile) {
        if (tile == null || tile.isInvalid()) return null;
        final World world = tile.getWorldObj();
        if (world == null) return null;
        final Chunk own = Platform.getLoadedChunk(world, tile.xCoord, tile.zCoord);
        if (own == null || Platform.getLoadedTileEntity(world, tile.xCoord, tile.yCoord, tile.zCoord) != tile) {
            return null;
        }
        if (!(tile instanceof TileEntityChest chest)) return new TileTarget(tile, own, null, null);
        final TileEntityChest other = findOtherChest(chest);
        final Chunk otherChunk = other == null ? null : Platform.getLoadedChunk(world, other.xCoord, other.zCoord);
        return new TileTarget(tile, own, other, otherChunk);
    }

    /**
     * Finds the other half of a double chest in the same order as vanilla's chest selection. A neighbor whose chunk is
     * not loaded is treated as absent. Once it loads, {@link #isCompositionCurrent()} detects the new pair.
     * <p>
     * Vanilla's adjacency cache is not used: it can retain a tile from an unloaded chunk, and rebuilding it can load
     * neighboring chunks.
     */
    private static @Nullable TileEntityChest findOtherChest(TileEntityChest chest) {
        final World world = chest.getWorldObj();
        for (ForgeDirection side : CHEST_SIDES) {
            final int x = chest.xCoord + side.offsetX;
            final int z = chest.zCoord + side.offsetZ;
            final Chunk chunk = Platform.getLoadedChunk(world, x, z);
            if (chunk == null || chunk.getBlock(x & 15, chest.yCoord, z & 15) != chest.getBlockType()) continue;
            if (Platform.getLoadedTileEntity(world, x, chest.yCoord, z) instanceof TileEntityChest other) {
                return other;
            }
        }
        return null;
    }

    public TileEntity tile() {
        return this.tile;
    }

    /** The full inventory of a vanilla chest, including the other half of a double chest. Null for other tiles. */
    public @Nullable IInventory chestInventory() {
        return this.chestInventory;
    }

    /** Cheap enough for every inventory access. Does not detect a change in the double chest pair. */
    public boolean isValid() {
        if (this.expired) return false;
        if (this.tile.isInvalid() || this.otherChest != null && this.otherChest.isInvalid()) {
            this.expired = true;
            return false;
        }
        for (Chunk chunk : this.chunks) {
            if (!chunk.isChunkLoaded) {
                this.expired = true;
                return false;
            }
        }
        return true;
    }

    /**
     * Also checks the double chest pair. Use it at an owner's work boundary, not for every slot access.
     * <p>
     * On a server the pair is looked up at most once per server tick, however many owners share this target. A pair
     * formed later in the same tick is detected on the next tick; until then the captured half stays a valid single
     * chest. A half that is removed or unloaded is detected immediately by {@link #isValid()}.
     */
    public boolean isCompositionCurrent() {
        if (!this.isValid()) return false;
        if (this.tile instanceof TileEntityChest chest) {
            final int tick = currentServerTick(chest);
            if (tick == NO_TICK || tick != this.compositionCheckedTick) {
                if (findOtherChest(chest) != this.otherChest) {
                    this.expired = true;
                    return false;
                }
                this.compositionCheckedTick = tick;
            }
        }
        return true;
    }

    /** The tick of the server that owns the chest, or {@link #NO_TICK} on a client, where nothing is memoized. */
    private static int currentServerTick(TileEntityChest chest) {
        if (chest.getWorldObj().isRemote) return NO_TICK;
        final MinecraftServer server = MinecraftServer.getServer();
        return server == null ? NO_TICK : server.getTickCounter();
    }
}
