package appeng.me.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

public class ItemCellInventory extends CellInventory<IAEItemStack> {

    public ItemCellInventory(ItemStack o, ISaveProvider container2) throws AppEngException {
        super(o, container2);

        this.setTypeWeight(8);
    }

    @Override
    protected IAEItemStack readStack(NBTTagCompound tag) {
        return AEItemStack.loadItemStackFromNBT(tag);
    }

    @Override
    protected String getStackTypeTag() {
        return "it";
    }

    @Override
    protected String getStackCountTag() {
        return "ic";
    }
}
