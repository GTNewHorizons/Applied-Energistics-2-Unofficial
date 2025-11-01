package appeng.util.item;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

public class AEFluidStackType implements IAEStackType<IAEFluidStack> {

    public static final AEFluidStackType FLUID_STACK_TYPE = new AEFluidStackType();
    public static final String FLUID_STACK_ID = "fluid";

    @Override
    public String getId() {
        return FLUID_STACK_ID;
    }

    @Override
    public IAEFluidStack loadStackFromNBT(NBTTagCompound tag) {
        return AEFluidStack.loadFluidStackFromNBT(tag);
    }

    @Override
    public IItemList<IAEFluidStack> createList() {
        return AEApi.instance().storage().createFluidList();
    }
}
