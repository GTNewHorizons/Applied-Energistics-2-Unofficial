package appeng.test.me.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import appeng.api.config.StorageFilter;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.storage.MEMonitorIInventory;
import appeng.util.inv.AdaptorList;

public class MEMonitorIInventoryFunctionalTest {

    @Test
    public void boundsRetainedTypesWithoutReadingTheCache() {
        final int slots = 54;
        final Item item = new Item().setHasSubtypes(true);
        final List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            contents.add(null);
        }
        final MEMonitorIInventory monitor = new MEMonitorIInventory(new AdaptorList(contents));
        monitor.setMode(StorageFilter.NONE);

        for (int poll = 0; poll < 512; poll++) {
            for (int slot = 0; slot < slots; slot++) {
                contents.set(slot, new ItemStack(item, 1, poll * slots + slot));
            }
            monitor.onTick();
            // size() does not iterate or clean the list; iteration would hide the regression.
            assertTrue(monitor.getStorageList().size() <= 2 * slots);
        }

        long total = 0;
        for (final IAEItemStack stack : monitor.getStorageList()) {
            total += stack.getStackSize();
        }
        assertEquals(slots, total);

        contents.clear();
        monitor.onTick();
        assertTrue(monitor.getStorageList().isEmpty());
    }
}
