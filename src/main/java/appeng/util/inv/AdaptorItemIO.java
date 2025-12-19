package appeng.util.inv;

import java.util.Iterator;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Iterators;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.item.FastImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InsertionItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;

import appeng.api.config.FuzzyMode;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

public class AdaptorItemIO extends InventoryAdaptor {

    private final ItemIO itemIO;

    public AdaptorItemIO(ItemIO itemIO) {
        this.itemIO = itemIO;
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        InventoryIterator iter = itemIO.sourceIterator();

        if (iter == null) return null;

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack immutableStack = iter.next();

            if (immutableStack == null) continue;

            if (filter != null && !immutableStack.matches(filter)) continue;

            ItemStack stack = immutableStack.toStack();

            if (destination != null && !destination.canInsert(stack)) continue;

            if (out == null) {
                out = immutableStack.toStack(0);
            }

            ItemStack extracted = iter.extract(amount, false);

            if (extracted != null) {
                out.stackSize += extracted.stackSize;
                amount -= extracted.stackSize;
            }
        }

        return out;
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        InventoryIterator iter = itemIO.sourceIterator();

        if (iter == null) return null;

        ItemStack out = null;

        while (iter.hasNext() && amount > 0) {
            ImmutableItemStack stack = iter.next();

            if (stack == null) continue;

            if (filter != null && !stack.matches(filter)) continue;
            if (destination != null && !destination.canInsert(stack.toStackFast())) continue;

            if (out == null) {
                out = stack.toStack(0);
            }

            int simulatedTransfer = Math.min(amount, stack.getStackSize());

            out.stackSize += simulatedTransfer;
            amount -= simulatedTransfer;
        }

        return out;
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack fuzzyFilter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        InventoryIterator iter = itemIO.sourceIterator();

        if (iter == null) return null;

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

            ItemStack extracted = iter.extract(amount, false);

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
        InventoryIterator iter = itemIO.sourceIterator();

        if (iter == null) return null;

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
        int rejected = itemIO.store(new FastImmutableItemStack(toBeAdded));

        return rejected == 0 ? null : ItemUtil.copyAmount(rejected, toBeAdded);
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        if (toBeSimulated == null || toBeSimulated.stackSize == 0) return null;

        InventoryIterator iter = itemIO.simulatedSinkIterator();
        if (iter == null) return toBeSimulated;

        InsertionItemStack insertion = new InsertionItemStack(new FastImmutableItemStack(toBeSimulated));

        while (iter.hasNext()) {
            ImmutableItemStack ignored = iter.next();

            insertion.set(iter.insert(insertion, false));

            if (insertion.isEmpty()) return null;
        }

        final ItemStack left = toBeSimulated.copy();
        left.stackSize = insertion.getStackSize();
        return left;
    }

    @Override
    public boolean containsItems() {
        if (itemIO.getStoredItemsInSink(null).orElse(0) > 0) return true;

        InventoryIterator iter = itemIO.sinkIterator();

        if (iter != null) {
            while (iter.hasNext()) {
                if (iter.next() != null) return true;
            }
        }

        iter = itemIO.sourceIterator();

        if (iter != null) {
            while (iter.hasNext()) {
                if (iter.next() != null) return true;
            }
        }

        return false;
    }

    @Override
    public @NotNull Iterator<ItemSlot> iterator() {
        InventoryIterator iter = itemIO.sourceIterator();

        if (iter == null) return Iterators.emptyIterator();

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
                slot.setExtractable(true);
                slot.setSlot(i++);

                return slot;
            }
        };
    }
}
