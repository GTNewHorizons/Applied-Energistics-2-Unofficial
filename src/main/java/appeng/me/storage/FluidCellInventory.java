package appeng.me.storage;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.exceptions.AppEngException;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;

public class FluidCellInventory extends CellInventory<IAEFluidStack> {

    public FluidCellInventory(ItemStack o, ISaveProvider container2) throws AppEngException {
        super(o, container2);

        this.setTypeWeight(256 * 8);
    }

    @Override
    protected IAEFluidStack readStack(NBTTagCompound tag) {
        return AEFluidStack.loadFluidStackFromNBT(tag);
    }

    @Override
    protected String getStackTypeTag() {
        return "ft";
    }

    @Override
    protected String getStackCountTag() {
        return "fc";
    }
}
