package appeng.core.sync.packets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;

public class PacketPickBlockTest {

    @Test
    public void craftableEntryWithNoStockDoesNotPreventCraftingFallback() {
        assertTrue(PacketPickBlock.isMissingFromStorage(null));
        assertTrue(PacketPickBlock.isMissingFromStorage(stored(0)));
        assertFalse(PacketPickBlock.isMissingFromStorage(stored(1)));
        assertFalse(PacketPickBlock.isMissingFromStorage(stored(64)));
    }

    @Test
    public void equippedTerminalUsesEncodedBaublesSlotAndExactStackIdentity() {
        ItemStack terminal = new ItemStack((Item) null, 1, 0);
        ItemStack otherTerminal = new ItemStack((Item) null, 1, 0);
        IInventory baubles = (IInventory) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { IInventory.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSizeInventory":
                            return 4;
                        case "getStackInSlot":
                            return (int) args[0] == 2 ? terminal : otherTerminal;
                        default:
                            throw new AssertionError(method.getName());
                    }
                });
        assertEquals(
                Platform.baublesSlotsOffset + 2,
                PacketPickBlock.findSlot(baubles, terminal, Platform.baublesSlotsOffset));
        assertEquals(
                -1,
                PacketPickBlock.findSlot(baubles, new ItemStack((Item) null, 1, 0), Platform.baublesSlotsOffset));
        assertEquals(-1, PacketPickBlock.findSlot(null, terminal, Platform.baublesSlotsOffset));
    }

    private IAEItemStack stored(long amount) {
        return (IAEItemStack) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { IAEItemStack.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getStackSize")) return amount;
                    throw new AssertionError(method.getName());
                });
    }
}
