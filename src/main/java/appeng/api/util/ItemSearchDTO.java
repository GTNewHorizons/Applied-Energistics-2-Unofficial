package appeng.api.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

public class ItemSearchDTO {

    public DimensionalCoord coord;
    public int cellSlot;
    public long itemCount;
    public ForgeDirection forward;
    public ForgeDirection up;
    public String blockName;
    public String itemName;
    public boolean blockNameNeedsTranslation;
    private ItemStack itemStack;

    public ItemSearchDTO(DimensionalCoord coord, IAEStack items, String blockName, boolean blockNameNeedsTranslation,
            int cellSlot, ForgeDirection forward, ForgeDirection up) {
        this.coord = coord;
        this.cellSlot = cellSlot;
        this.itemCount = items.getStackSize();
        if (items instanceof final IAEItemStack itemStack) {
            this.itemStack = itemStack.getItemStack();
        }
        this.itemName = items.getLocalizedName();
        if (this.itemName == null) this.itemName = " ";
        this.blockName = blockName;
        this.blockNameNeedsTranslation = blockNameNeedsTranslation;
        this.forward = forward;
        this.up = up;
    }

    public ItemSearchDTO(DimensionalCoord coord, IAEStack items, String blockName, boolean blockNameNeedsTranslation) {
        this(coord, items, blockName, blockNameNeedsTranslation, -1, ForgeDirection.UNKNOWN, ForgeDirection.UNKNOWN);
    }

    public ItemSearchDTO(DimensionalCoord coord, IAEStack items, String blockName) {
        this(coord, items, blockName, false);
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
        data.setBoolean("blockNameNeedsTranslation", blockNameNeedsTranslation);
        data.setString("forward", this.forward.name());
        data.setString("up", this.up.name());
        if (this.itemStack != null) {
            final NBTTagCompound itemStackData = new NBTTagCompound();
            this.itemStack.writeToNBT(itemStackData);
            data.setTag("itemStack", itemStackData);
        }
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
        this.blockNameNeedsTranslation = data.getBoolean("blockNameNeedsTranslation");
        this.itemCount = data.getLong("itemCount");
        this.cellSlot = data.getInteger("cellSlot");
        this.coord = new DimensionalCoord(x, y, z, dim);
        this.forward = ForgeDirection.valueOf(data.getString("forward"));
        this.up = ForgeDirection.valueOf(data.getString("up"));
        if (data.hasKey("itemStack")) {
            this.itemStack = ItemStack.loadItemStackFromNBT(data.getCompoundTag("itemStack"));
        }
    }

    public String getDisplayItemName() {
        if (this.itemStack != null) {
            return this.itemStack.getDisplayName();
        }
        return this.itemName;
    }

    public String getDisplayBlockName() {
        if (!this.blockNameNeedsTranslation) {
            return this.blockName;
        }
        return translateBlockName(this.blockName);
    }

    private static String translateBlockName(final String unlocalizedName) {
        if (unlocalizedName == null || unlocalizedName.isEmpty()) {
            return " ";
        }

        final String nameKey = unlocalizedName.endsWith(".name") ? unlocalizedName : unlocalizedName + ".name";
        if (StatCollector.canTranslate(nameKey)) {
            return StatCollector.translateToLocal(nameKey);
        }
        if (StatCollector.canTranslate(unlocalizedName)) {
            return StatCollector.translateToLocal(unlocalizedName);
        }

        return unlocalizedName;
    }

}
