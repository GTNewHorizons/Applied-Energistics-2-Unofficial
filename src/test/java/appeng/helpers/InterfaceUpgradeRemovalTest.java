package appeng.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import appeng.tile.inventory.AppEngInternalInventory;

public class InterfaceUpgradeRemovalTest {

    @Test
    public void checksDisabledSlotsInsteadOfPatternCountWithoutMovingPatterns() {
        AppEngInternalInventory patterns = new AppEngInternalInventory(null, 36, 1);
        ItemStack pattern = new ItemStack(new Item());
        patterns.setInventorySlotContents(17, pattern);

        assertFalse(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, 0, 9));
        assertTrue(patterns.getStackInSlot(17) == pattern);
        assertTrue(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, 1, 9));

        patterns.setInventorySlotContents(17, null);
        patterns.setInventorySlotContents(8, pattern);
        assertTrue(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, 0, 9));
        assertFalse(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, -1, 9));
    }

    @Test
    public void checksTheEntireBackingInventoryIncludingHiddenRows() {
        AppEngInternalInventory patterns = new AppEngInternalInventory(null, 36, 1);
        patterns.setInventorySlotContents(35, new ItemStack(new Item()));
        assertFalse(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, 2, 9));
        patterns.setInventorySlotContents(35, null);
        assertTrue(InterfaceUpgradeRemoval.canRemoveCapacityCard(patterns, 2, 9));
    }
}
