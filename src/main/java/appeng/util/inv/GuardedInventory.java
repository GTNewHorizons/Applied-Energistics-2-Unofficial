package appeng.util.inv;

import java.util.function.BooleanSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/** Keeps a chained inventory's slot layout stable while refusing access to a detached output. */
public final class GuardedInventory implements IInventory {

    private final IInventory inventory;
    private final BooleanSupplier available;
    private final int size;
    private int stackLimit;

    public GuardedInventory(IInventory inventory, BooleanSupplier available) {
        this.inventory = inventory;
        this.available = available;
        this.size = inventory.getSizeInventory();
        this.stackLimit = inventory.getInventoryStackLimit();
    }

    @Override
    public int getSizeInventory() {
        return this.size;
    }

    @Override
    public @Nullable ItemStack getStackInSlot(int slot) {
        return this.available.getAsBoolean() ? this.inventory.getStackInSlot(slot) : null;
    }

    @Override
    public @Nullable ItemStack decrStackSize(int slot, int amount) {
        return this.available.getAsBoolean() ? this.inventory.decrStackSize(slot, amount) : null;
    }

    @Override
    public @Nullable ItemStack getStackInSlotOnClosing(int slot) {
        return this.available.getAsBoolean() ? this.inventory.getStackInSlotOnClosing(slot) : null;
    }

    @Override
    public void setInventorySlotContents(int slot, @Nullable ItemStack stack) {
        if (this.available.getAsBoolean()) this.inventory.setInventorySlotContents(slot, stack);
    }

    @Override
    public String getInventoryName() {
        return "GuardedInventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    /**
     * A chained inventory reports the smallest limit of its parts, so a detached output must not report 0 and block the
     * others. Inserts into it are refused by {@link #isItemValidForSlot} instead.
     */
    @Override
    public int getInventoryStackLimit() {
        if (this.available.getAsBoolean()) this.stackLimit = this.inventory.getInventoryStackLimit();
        return this.stackLimit;
    }

    @Override
    public void markDirty() {
        if (this.available.getAsBoolean()) this.inventory.markDirty();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return this.available.getAsBoolean() && this.inventory.isItemValidForSlot(slot, stack);
    }
}
