package appeng.util.inv;

import java.util.Iterator;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.capability.item.IItemIO;
import com.gtnewhorizon.gtnhlib.capability.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.capability.item.InventorySourceIterator;

import appeng.api.config.FuzzyMode;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

public class AdapterItemIO extends InventoryAdaptor {

    private final IItemIO itemIO;

    public AdapterItemIO(IItemIO itemIO) {
        this.itemIO = itemIO;
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        InventorySourceIterator iter = itemIO.iterator();

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack immutableStack = iter.next();

            if (immutableStack == null) continue;

            ItemStack stack = immutableStack.toStack();

            if (filter != null && !Platform.isSameItemPrecise(stack, filter)) continue;
            if (destination != null && !destination.canInsert(stack)) continue;

            if (out == null) {
                out = immutableStack.toStack(0);
            }

            ItemStack extracted = iter.extract(amount);

            if (extracted != null) {
                out.stackSize += extracted.stackSize;
                amount -= extracted.stackSize;
            }
        }

        return out;
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        InventorySourceIterator iter = itemIO.iterator();

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack immutableStack = iter.next();

            if (immutableStack == null) continue;

            ItemStack stack = immutableStack.toStack();

            if (filter != null && !Platform.isSameItemPrecise(stack, filter)) continue;
            if (destination != null && !destination.canInsert(stack)) continue;

            if (out == null) {
                out = immutableStack.toStack(0);
            }

            int simulatedTransfer = Math.min(amount, immutableStack.getStackSize());

            out.stackSize += simulatedTransfer;
            amount -= simulatedTransfer;
        }

        return out;
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack fuzzyFilter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        InventorySourceIterator iter = itemIO.iterator();

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack immutableStack = iter.next();

            if (immutableStack == null) continue;

            ItemStack stack = immutableStack.toStack();

            if (out != null) {
                if (!Platform.isSameItemPrecise(out, stack)) continue;
            } else {
                if (fuzzyFilter != null && !Platform.isSameItemFuzzy(stack, fuzzyFilter, fuzzyMode)) continue;
            }

            if (destination != null && !destination.canInsert(stack)) continue;

            if (out == null) {
                out = immutableStack.toStack(0);
            }

            ItemStack extracted = iter.extract(amount);

            if (extracted != null) {
                out.stackSize += extracted.stackSize;
                amount -= extracted.stackSize;
            }
        }

        return out;
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack fuzzyFilter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        InventorySourceIterator iter = itemIO.iterator();

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack immutableStack = iter.next();

            if (immutableStack == null) continue;

            ItemStack stack = immutableStack.toStack();

            if (out != null) {
                if (!Platform.isSameItemPrecise(out, stack)) continue;
            } else {
                if (fuzzyFilter != null && !Platform.isSameItemFuzzy(stack, fuzzyFilter, fuzzyMode)) continue;
            }

            if (destination != null && !destination.canInsert(stack)) continue;

            if (out == null) {
                out = immutableStack.toStack(0);
            }

            int simulatedTransfer = Math.min(amount, immutableStack.getStackSize());

            out.stackSize += simulatedTransfer;
            amount -= simulatedTransfer;
        }

        return out;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return itemIO.store(toBeAdded);
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return null;
    }

    @Override
    public boolean containsItems() {
        return itemIO.iterator().hasNext();
    }

    @Override
    public @NotNull Iterator<ItemSlot> iterator() {
        InventorySourceIterator iter = itemIO.iterator();

        return new Iterator<>() {

            private final ItemSlot slot = new ItemSlot();
            private int i = 0;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public ItemSlot next() {
                ImmutableItemStack stack = iter.next();

                slot.setItemStack(stack == null ? null : stack.toStack());
                slot.setAEItemStack(null);
                slot.setExtractable(true);
                slot.setSlot(i++);

                return slot;
            }
        };
    }
}
