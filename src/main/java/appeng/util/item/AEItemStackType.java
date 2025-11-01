package appeng.util.item;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

public class AEItemStackType implements IAEStackType<IAEItemStack> {

    public static final AEItemStackType ITEM_STACK_TYPE = new AEItemStackType();
    public static final String ITEM_STACK_ID = "item";

    @Override
    public String getId() {
        return ITEM_STACK_ID;
    }

    @Override
    public IAEItemStack loadStackFromNBT(NBTTagCompound tag) {
        return AEItemStack.loadItemStackFromNBT(tag);
    }

    @Override
    public IItemList<IAEItemStack> createList() {
        return AEApi.instance().storage().createItemList();
    }

    @Override
    public IItemList<IAEItemStack> createPrimitiveList() {
        return AEApi.instance().storage().createPrimitiveItemList();
    }
}
