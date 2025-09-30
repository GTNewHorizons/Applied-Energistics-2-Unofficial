package appeng.api.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.storage.data.IAEStack;

public class ItemSearchDTO {

    public DimensionalCoord coord;
    public int cellSlot;
    public long itemCount;
    public ForgeDirection forward;
    public ForgeDirection up;
    public String blockName;
    public String itemName;

    public ItemSearchDTO(DimensionalCoord coord, IAEStack items, String blockName, int cellSlot, ForgeDirection forward,
            ForgeDirection up) {
        this.coord = coord;
        this.cellSlot = cellSlot;
        this.itemCount = items.getStackSize();
        this.itemName = items.getLocalizedName();
        if (this.itemName == null) this.itemName = " ";
        this.blockName = blockName;
        this.forward = forward;
        this.up = up;
    }

    public ItemSearchDTO(DimensionalCoord coord, IAEStack items, String blockName) {
        this(coord, items, blockName, -1, ForgeDirection.UNKNOWN, ForgeDirection.UNKNOWN);
    }

    public ItemSearchDTO(final NBTTagCompound data) {
        this.readFromNBT(data);
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setInteger("dim", coord.dimId);
        data.setInteger("x", coord.x);
        data.setInteger("y", coord.y);
        data.setInteger("z", coord.z);
        data.setInteger("cellSlot", cellSlot);
        data.setLong("itemCount", itemCount);
        data.setString("blockName", blockName);
        data.setString("itemName", itemName);
        data.setString("forward", this.forward.name());
        data.setString("up", this.up.name());
    }

    public static void writeListToNBT(final NBTTagCompound tag, List<ItemSearchDTO> list) {
        int i = 0;
        for (ItemSearchDTO d : list) {
            NBTTagCompound data = new NBTTagCompound();
            d.writeToNBT(data);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static List<ItemSearchDTO> readAsListFromNBT(final NBTTagCompound tag) {
        List<ItemSearchDTO> list = new ArrayList<>();
        int i = 0;
        while (tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(new ItemSearchDTO(data));
            i++;
        }
        return list;
    }

    private void readFromNBT(final NBTTagCompound data) {
        int dim = data.getInteger("dim");
        int x = data.getInteger("x");
        int y = data.getInteger("y");
        int z = data.getInteger("z");
        this.blockName = data.getString("blockName");
        this.itemName = data.getString("itemName");
        this.itemCount = data.getLong("itemCount");
        this.cellSlot = data.getInteger("cellSlot");
        this.coord = new DimensionalCoord(x, y, z, dim);
        this.forward = ForgeDirection.valueOf(data.getString("forward"));
        this.up = ForgeDirection.valueOf(data.getString("up"));
    }

}
