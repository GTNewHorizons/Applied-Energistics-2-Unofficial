package appeng.test.parts.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import com.github.bsideup.jabel.Desugar;

import appeng.parts.p2p.PartP2PItems;
import appeng.util.inv.WrapperChainedInventory;

public class PartP2PItemsFunctionalTest {

    private static final Item TEST_ITEM = new Item();

    @Test
    public void circularInventoryOperationsTerminateSafely() {
        final PartP2PItems first = tunnel();
        final PartP2PItems second = tunnel();
        setDestination(first, new InventoryView(second));
        setDestination(second, new InventoryView(first));
        final ItemStack stack = new ItemStack(TEST_ITEM);

        assertEquals(0, first.getSizeInventory());
        assertEquals(0, first.getAccessibleSlotsFromSide(0).length);
        assertNull(first.getStackInSlot(0));
        assertNull(first.decrStackSize(0, 1));
        first.setInventorySlotContents(0, stack);
        assertEquals(0, first.getInventoryStackLimit());
        assertFalse(first.isItemValidForSlot(0, stack));
        assertFalse(first.canInsertItem(0, stack, 0));
    }

    @Test
    public void comparatorIgnoresCircularRangeAndReadsConcreteInventories() {
        final PartP2PItems first = tunnel();
        final PartP2PItems second = tunnel();
        final InventoryBasic firstChest = chest(new ItemStack(TEST_ITEM, 64));
        final InventoryBasic secondChest = chest(new ItemStack(TEST_ITEM, 64));

        setDestination(first, new WrapperChainedInventory(firstChest, new InventoryView(second, 2, 64)));
        setDestination(second, new WrapperChainedInventory(secondChest, new InventoryView(first, 2, 64)));

        // Cable-bus inventory layers cache their slot count and expose a conventional stack limit.
        final IInventory firstCableBusLayer = new InventoryView(first, 3, 64);
        assertEquals(10, Container.calcRedstoneFromInventory(firstCableBusLayer));
        assertEquals(64, firstChest.getStackInSlot(0).stackSize);
        assertEquals(64, secondChest.getStackInSlot(0).stackSize);
    }

    @Test
    public void nonCircularP2PChainStillDelegates() {
        final PartP2PItems first = tunnel();
        final PartP2PItems second = tunnel();
        final ItemStack stack = new ItemStack(TEST_ITEM, 7);
        final InventoryBasic chest = chest(stack);
        setDestination(first, second);
        setDestination(second, chest);

        assertEquals(1, first.getSizeInventory());
        assertSame(stack, first.getStackInSlot(0));
        assertEquals(64, first.getInventoryStackLimit());
        assertEquals(1, first.getAccessibleSlotsFromSide(0).length);
    }

    private static PartP2PItems tunnel() {
        return new PartP2PItems(new ItemStack(TEST_ITEM));
    }

    private static InventoryBasic chest(final ItemStack stack) {
        final InventoryBasic inventory = new InventoryBasic("test", false, 1);
        inventory.setInventorySlotContents(0, stack);
        return inventory;
    }

    private static void setDestination(final PartP2PItems tunnel, final IInventory destination) {
        try {
            final Field cachedInventory = PartP2PItems.class.getDeclaredField("cachedInv");
            cachedInventory.setAccessible(true);
            cachedInventory.set(tunnel, destination);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("Unable to install the test P2P destination", e);
        }
    }

    /**
     * Models the cached inventory layer between two face-to-face cable-bus hosts. A negative fixed size delegates size
     * queries too, which is useful for exercising every guarded inventory operation directly.
     */
    @Desugar
    private record InventoryView(IInventory target, int fixedSize, int stackLimit) implements IInventory {

        private InventoryView(final IInventory target) {
            this(target, -1, -1);
        }

        @Override
        public int getSizeInventory() {
            return this.fixedSize >= 0 ? this.fixedSize : this.target.getSizeInventory();
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return this.target.getStackInSlot(slot);
        }

        @Override
        public ItemStack decrStackSize(final int slot, final int amount) {
            return this.target.decrStackSize(slot, amount);
        }

        @Override
        public ItemStack getStackInSlotOnClosing(final int slot) {
            return this.target.getStackInSlotOnClosing(slot);
        }

        @Override
        public void setInventorySlotContents(final int slot, final ItemStack stack) {
            this.target.setInventorySlotContents(slot, stack);
        }

        @Override
        public String getInventoryName() {
            return "P2P inventory view";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return this.stackLimit >= 0 ? this.stackLimit : this.target.getInventoryStackLimit();
        }

        @Override
        public void markDirty() {
            this.target.markDirty();
        }

        @Override
        public boolean isUseableByPlayer(final EntityPlayer player) {
            return false;
        }

        @Override
        public void openInventory() {
            this.target.openInventory();
        }

        @Override
        public void closeInventory() {
            this.target.closeInventory();
        }

        @Override
        public boolean isItemValidForSlot(final int slot, final ItemStack stack) {
            return this.target.isItemValidForSlot(slot, stack);
        }
    }
}
