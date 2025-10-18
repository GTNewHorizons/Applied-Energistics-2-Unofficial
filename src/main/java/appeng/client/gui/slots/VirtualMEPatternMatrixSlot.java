package appeng.client.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;

public class VirtualMEPatternMatrixSlot extends VirtualMESlot {

    private IInventory inventory;

    private ItemStack cachedStack;
    private IAEItemStack cachedAEStack;

    public VirtualMEPatternMatrixSlot(int x, int y, IInventory inventory, int slotIndex) {
        super(x, y, slotIndex);
        this.inventory = inventory;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        ItemStack stack = this.inventory.getStackInSlot(this.slotIndex);
        if (stack == null) {
            this.cachedAEStack = null;
            return null;
        } else if (!ItemStack.areItemStacksEqual(stack, cachedStack)) {
            this.cachedAEStack = AEItemStack.create(stack);
            return this.cachedAEStack;
        }
        return this.cachedAEStack;
    }
}
