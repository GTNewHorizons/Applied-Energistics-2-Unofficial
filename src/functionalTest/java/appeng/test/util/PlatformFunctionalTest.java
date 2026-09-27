package appeng.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import appeng.util.item.NetworkItemList;

public class PlatformFunctionalTest {

    @Test
    void postListChangesSupportsReadOnlyNetworkItemLists() {
        final IItemList<IAEItemStack> networkItems = new ItemList();
        networkItems.add(AEItemStack.create(new ItemStack(Items.stick, 2)));

        final NetworkItemList<IAEItemStack> before = new NetworkItemList<>(null, ItemList::new);
        before.addNetworkItems(null, networkItems);

        final IItemList<IAEItemStack> after = new ItemList();
        after.add(AEItemStack.create(new ItemStack(Items.stick, 5)));

        final List<IAEItemStack> changes = new ArrayList<>();
        Platform.postListChanges(before, after, new IMEMonitorHandlerReceiver<IAEItemStack>() {

            @Override
            public boolean isValid(Object verificationToken) {
                return true;
            }

            @Override
            public void postChange(IBaseMonitor<IAEItemStack> monitor, Iterable<IAEItemStack> change,
                    BaseActionSource actionSource) {
                change.forEach(changes::add);
            }

            @Override
            public void onListUpdate() {}
        }, null);

        assertFalse(before.hasWriteAccess());
        assertEquals(2, before.getFirstItem().getStackSize());
        assertEquals(1, changes.size());
        assertEquals(3, changes.get(0).getStackSize());
    }
}
