package appeng.util.inv;

import java.util.Iterator;

import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;

public class AdaptorNull extends InventoryAdaptor {

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        return null;
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return null;
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null;
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return toBeAdded;
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return toBeSimulated;
    }

    @Override
    public boolean containsItems() {
        return false;
    }

    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        return toBeAdded;
    }

    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        return toBeSimulated;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return null;
    }
}
