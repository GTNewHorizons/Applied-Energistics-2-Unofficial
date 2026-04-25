package appeng.util.inv;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.InsertionMode;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.storage.CellInventory;
import appeng.tile.storage.TileChest;
import appeng.util.item.AEItemStack;

public class AdaptorMEChest extends AdaptorIInventory {

    private final TileChest meChest;
    private final IInventory i;

    public AdaptorMEChest(WrapperMCISidedInventory i, TileChest meChest) {
        super(i);
        this.meChest = meChest;
        this.i = i;
    }

    @Override
    public ItemStack addItems(final ItemStack toBeAdded) {
        return this.addItems(toBeAdded, InsertionMode.DEFAULT);
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded, InsertionMode insertionMode) {
        if (meChest.getItemInventory() == null) {
            if (CellInventory.isCell(toBeAdded)) {
                return addCell(toBeAdded, true);
            }
            return toBeAdded;
        }
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IAEItemStack result = (IAEItemStack) meChest.getItemInventory()
                .injectItems(AEItemStack.create(toBeAdded), Actionable.MODULATE, meChest.getActionSource());
        return result == null ? null : result.getItemStack();
    }

    @Override
    public ItemStack simulateAdd(final ItemStack toBeSimulated) {
        return this.simulateAdd(toBeSimulated, InsertionMode.DEFAULT);
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated, InsertionMode insertionMode) {
        if (meChest.getItemInventory() == null) {
            if (CellInventory.isCell(toBeSimulated)) {
                return addCell(toBeSimulated, false);
            }
            return toBeSimulated;
        }
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IAEItemStack result = (IAEItemStack) meChest.getItemInventory()
                .injectItems(AEItemStack.create(toBeSimulated), Actionable.SIMULATE, meChest.getActionSource());
        return result == null ? null : result.getItemStack();
    }

    @Override
    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        return addStackToMonitor(toBeAdded, Actionable.MODULATE);
    }

    @Override
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        return addStackToMonitor(toBeSimulated, Actionable.SIMULATE);
    }

    private IAEStack<?> addStackToMonitor(IAEStack<?> aes, Actionable act) {
        final IMEMonitor monitor = meChest.getMEMonitor(aes.getStackType());
        if (monitor == null) return aes;
        return monitor.injectItems(aes, act, meChest.getActionSource());
    }

    @Override
    public boolean containsItems() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            final IMEMonitor<?> monitor = meChest.getMEMonitor(type);
            if (monitor != null && !monitor.getStorageList().isEmpty()) return true;
        }
        return false;
    }

    private ItemStack addCell(final ItemStack cell, final boolean modulate) {
        // Snippet of AdaptorIInventory to prevent invalid stacks from transferring
        final ItemStack leftoverItems = cell.copy();
        final ItemStack nextItem = leftoverItems.copy();
        nextItem.stackSize = 1; // Max stack size of cell should be 1
        leftoverItems.stackSize -= nextItem.stackSize;
        if (modulate) {
            this.i.setInventorySlotContents(0, nextItem);
            this.i.markDirty();
        }
        if (leftoverItems.stackSize <= 0) {
            return null;
        }
        return leftoverItems;
    }
}
