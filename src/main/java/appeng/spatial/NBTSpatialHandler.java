package appeng.spatial;

import java.util.IdentityHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import appeng.api.movable.IMovableHandler;
import appeng.core.AELog;

public class NBTSpatialHandler implements IMovableHandler {

    private final Class<?> tileClass;
    private final DefaultSpatialHandler fallback = new DefaultSpatialHandler();
    private final IdentityHashMap<TileEntity, NBTTagCompound> snapshots = new IdentityHashMap<>();

    public NBTSpatialHandler(final Class<?> tileClass) {
        this.tileClass = tileClass;
    }

    @Override
    public boolean canHandle(final Class<? extends TileEntity> myClass, final TileEntity tile) {
        return this.tileClass.isAssignableFrom(myClass);
    }

    @Override
    public void prepareToMove(final TileEntity tile) {
        final NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        this.snapshots.put(tile, tag);
    }

    @Override
    public void moveTile(final TileEntity tile, final World world, final int x, final int y, final int z) {
        try {
            final TileEntity moved = TileEntity.createAndLoadEntity(this.createMovedTag(tile, x, y, z));
            if (moved == null) {
                throw new IllegalStateException("Unable to restore moved tile entity from NBT: " + tile.getClass());
            }

            moved.setWorldObj(world);
            moved.xCoord = x;
            moved.yCoord = y;
            moved.zCoord = z;

            final Chunk chunk = world.getChunkFromBlockCoords(x, z);
            chunk.func_150812_a(x & 0xF, y, z & 0xF, moved);

            if (chunk.isChunkLoaded) {
                world.addTileEntity(moved);
                world.markBlockForUpdate(x, y, z);
            }
        } catch (final Throwable e) {
            AELog.debug(e);
            this.fallback.moveTile(tile, world, x, y, z);
        } finally {
            this.snapshots.remove(tile);
        }
    }

    NBTTagCompound createMovedTag(final TileEntity tile, final int x, final int y, final int z) {
        NBTTagCompound snapshot = this.snapshots.get(tile);
        if (snapshot == null) {
            snapshot = new NBTTagCompound();
            tile.writeToNBT(snapshot);
        }

        final NBTTagCompound tag = (NBTTagCompound) snapshot.copy();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        return tag;
    }
}
