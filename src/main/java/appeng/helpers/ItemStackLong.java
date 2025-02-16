package appeng.helpers;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

// Copied from GT5U
public class ItemStackLong {

    public long stackSize;
    public ItemStack itemStack;

    private ItemStackLong() {}

    /// Creates new ItemStackLong from ItemStack.
    /// It copies the stackSize from the passed ItemStack.
    public ItemStackLong(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.stackSize = itemStack.stackSize;
    }

    /// Creates new ItemStackLong from ItemStack and stack size.
    public ItemStackLong(ItemStack itemStack, long stackSize) {
        this.itemStack = itemStack;
        this.stackSize = stackSize;
    }

    public long getStackSize() {
        return stackSize;
    }

    public long compareTo(tectech.util.ItemStackLong itemStackLong) {
        return (stackSize - itemStackLong.stackSize);
    }

    /**
     * Returns a new stack with the same properties.
     */
    public ItemStackLong copy() {
        return new ItemStackLong(this.itemStack.copy(), this.stackSize);
    }

    public static ItemStackLong loadItemStackFromNBT(NBTTagCompound tagCompound) {
        ItemStackLong itemstack = new ItemStackLong();
        itemstack.readFromNBT(tagCompound);
        return itemstack.getItem() != null ? itemstack : null;
    }

    private Item getItem() {
        return this.itemStack.getItem();
    }

    /**
     * Read the stack fields from a NBT object.
     */
    public void readFromNBT(NBTTagCompound tagCompound) {
        this.itemStack.readFromNBT(tagCompound);
        this.stackSize = tagCompound.getLong("CountLong");
    }

    /**
     * Write the stack fields to a NBT object. Return the new NBT object.
     */
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        this.itemStack.writeToNBT(tagCompound);
        tagCompound.setLong("CountLong", this.stackSize);
        return tagCompound;
    }
}
