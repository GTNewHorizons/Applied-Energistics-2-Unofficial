package appeng.api.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class NamedDimensionalCoord extends DimensionalCoord {

    private final String name;

    public NamedDimensionalCoord(DimensionalCoord s, String customName) {
        super(s);
        name = customName;
    }

    public NamedDimensionalCoord(TileEntity s, String customName) {
        super(s);
        name = customName;
    }

    public NamedDimensionalCoord(World _w, int _x, int _y, int _z, String customName) {
        super(_w, _x, _y, _z);
        name = customName;
    }

    public NamedDimensionalCoord(int _x, int _y, int _z, int _dim, String customName) {
        super(_x, _y, _z, _dim);
        name = customName;
    }

    public String getCustomName() {
        return name;
    }

    private static void writeToNBT(final NBTTagCompound data, int x, int y, int z, int dimId, String name) {
        data.setInteger("dim", dimId);
        data.setInteger("x", x);
        data.setInteger("y", y);
        data.setInteger("z", z);
        data.setString("customName", name);
    }

    public void writeToNBT(final NBTTagCompound data) {
        writeToNBT(data, this.x, this.y, this.z, this.dimId, name);
    }

    public static void writeListToNBTNamed(final NBTTagCompound tag, List<NamedDimensionalCoord> list) {
        int i = 0;
        for (NamedDimensionalCoord d : list) {
            NBTTagCompound data = new NBTTagCompound();
            writeToNBT(data, d.x, d.y, d.z, d.dimId, d.name);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static NamedDimensionalCoord readFromNBT(final NBTTagCompound data) {
        return new NamedDimensionalCoord(
                data.getInteger("x"),
                data.getInteger("y"),
                data.getInteger("z"),
                data.getInteger("dim"),
                data.getString("customName"));
    }

    public static List<NamedDimensionalCoord> readAsListFromNBTNamed(final NBTTagCompound tag) {
        List<NamedDimensionalCoord> list = new ArrayList<>();
        int i = 0;
        while (tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(readFromNBT(data));
            i++;
        }
        return list;
    }

    public void writeToPacket(final ByteBuf data) {
        data.writeInt(this.x);
        data.writeInt(this.y);
        data.writeInt(this.z);
        data.writeInt(this.dimId);
        ByteBufUtils.writeUTF8String(data, this.name == null ? "" : this.name);
    }

    public static void writeListToPacket(final ByteBuf data, final List<NamedDimensionalCoord> list) {
        data.writeInt(list == null ? 0 : list.size());
        if (list == null) {
            return;
        }

        for (final NamedDimensionalCoord coord : list) {
            coord.writeToPacket(data);
        }
    }

    public static NamedDimensionalCoord readFromPacket(final ByteBuf data) {
        return new NamedDimensionalCoord(
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                ByteBufUtils.readUTF8String(data));
    }

    public static List<NamedDimensionalCoord> readAsListFromPacket(final ByteBuf data) {
        final int count = data.readInt();
        final List<NamedDimensionalCoord> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(readFromPacket(data));
        }
        return list;
    }
}
