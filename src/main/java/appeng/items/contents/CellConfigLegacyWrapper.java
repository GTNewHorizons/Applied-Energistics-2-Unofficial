package appeng.items.contents;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

public class CellConfigLegacyWrapper extends IAEStackInventory {

    private final IInventory inventory;

    public CellConfigLegacyWrapper(IInventory inventory) {
        super(null, 63);
        this.inventory = inventory;
    }

    @Override
    public boolean isEmpty() {
        int size = this.inventory.getSizeInventory();
        for (int i = 0; i < size; i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (stack != null) return false;
        }
        return true;
    }

    public IAEStack<?> getAEStackInSlot(final int n) {
        ItemStack stack = this.inventory.getStackInSlot(n);
        return stack != null ? AEItemStack.create(stack) : null;
    }

    public void putAEStackInSlot(final int n, IAEStack<?> aes) {
        if (aes instanceof IAEItemStack ais) {
            this.inventory.setInventorySlotContents(n, ais.getItemStack());
        } else {
            this.inventory.setInventorySlotContents(n, null);
        }
        markDirty();
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {

    }

    public void readFromNBT(final NBTTagCompound data, final String name) {

    }

    public int getSizeInventory() {
        return this.inventory.getSizeInventory();
    }

    public void markDirty() {
        this.inventory.markDirty();
    }

    public StorageName getStorageName() {
        return StorageName.NONE;
    }
}
