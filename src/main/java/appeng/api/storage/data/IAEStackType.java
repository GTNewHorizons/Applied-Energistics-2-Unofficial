package appeng.api.storage.data;

import net.minecraft.nbt.NBTTagCompound;

public interface IAEStackType<T extends IAEStack> {

    String getId();

    T loadStackFromNBT(NBTTagCompound tag);

    IItemList<T> createList();

    default IItemList<T> createPrimitiveList() {
        return this.createList();
    }
}
