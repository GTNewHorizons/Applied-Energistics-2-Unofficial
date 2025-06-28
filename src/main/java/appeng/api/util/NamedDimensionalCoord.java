package appeng.api.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

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
}
