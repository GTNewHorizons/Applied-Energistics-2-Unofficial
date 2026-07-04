package appeng.api.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

public class FlowSearchDTO {

    public DimensionalCoord coord;
    public long netTotal;

    public FlowSearchDTO(DimensionalCoord coord, long netTotal) {
        this.coord = coord;
        this.netTotal = netTotal;
    }

    public FlowSearchDTO(final NBTTagCompound data) {
        this.readFromNBT(data);
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setInteger("dim", coord.dimId);
        data.setInteger("x", coord.x);
        data.setInteger("y", coord.y);
        data.setInteger("z", coord.z);
        data.setLong("netTotal", netTotal);
    }

    public static void writeListToNBT(final NBTTagCompound tag, List<FlowSearchDTO> list) {
        int i = 0;
        for (FlowSearchDTO d : list) {
            NBTTagCompound data = new NBTTagCompound();
            d.writeToNBT(data);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static List<FlowSearchDTO> readAsListFromNBT(final NBTTagCompound tag) {
        List<FlowSearchDTO> list = new ArrayList<>();
        int i = 0;
        while (tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(new FlowSearchDTO(data));
            i++;
        }
        return list;
    }

    private void readFromNBT(final NBTTagCompound data) {
        int dim = data.getInteger("dim");
        int x = data.getInteger("x");
        int y = data.getInteger("y");
        int z = data.getInteger("z");
        this.coord = new DimensionalCoord(x, y, z, dim);
        this.netTotal = data.getLong("netTotal");
    }
}
