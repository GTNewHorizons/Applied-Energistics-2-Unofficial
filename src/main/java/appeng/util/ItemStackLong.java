package appeng.util;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

public class ItemStackLong {

    public final Item itemStack;
    public long stackSize;
    private Item field_151002_e;
    public NBTTagCompound stackTagCompound;

    private cpw.mods.fml.common.registry.RegistryDelegate<Item> delegate;

    public ItemStackLong(Item item, long stackSize) {
        this.itemStack = item;
        this.stackSize = stackSize;
    }

    public long getStackSize() {
        return stackSize;
    }

    public long compareTo(tectech.util.ItemStackLong itemStackLong) {
        return (stackSize - itemStackLong.stackSize);
    }

    public ItemStackLong copy() {
        ItemStackLong itemstack = new ItemStackLong(this.field_151002_e, this.stackSize);

        if (this.stackTagCompound != null) {
            itemstack.stackTagCompound = (NBTTagCompound) this.stackTagCompound.copy();
        }

        return itemstack;
    }

    public Item getItem() {
        return this.delegate != null ? this.delegate.get() : null;
    }
}
