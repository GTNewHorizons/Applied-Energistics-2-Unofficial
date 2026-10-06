package appeng.util.inv;

import java.util.Collections;
import java.util.Iterator;

import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.parts.p2p.PartP2PItems;

public class AdaptorP2PItem extends AdaptorIInventory {

    public AdaptorP2PItem(PartP2PItems p2p) {
        super(p2p, p2p.getInventoryStackLimit());
    }

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
    public Iterator<ItemSlot> iterator() {
        return Collections.emptyIterator(); // nothing listed through the tunnel
    }
}
