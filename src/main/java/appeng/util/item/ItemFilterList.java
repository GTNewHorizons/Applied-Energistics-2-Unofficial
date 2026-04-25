package appeng.util.item;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class ItemFilterList implements IItemList<IAEItemStack> {

    private final ObjectOpenHashSet<IAEItemStack> records = new ObjectOpenHashSet<>();

    @Override
    public void add(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.records.get(option);

        if (st == null) {
            putItemRecord(option.copy());
        }
    }

    @Override
    public IAEItemStack findPrecise(final IAEItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        return this.records.get(itemStack);
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(final IAEItemStack filter, final FuzzyMode fuzzy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return !this.iterator().hasNext();
    }

    @Override
    public void addStorage(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.records.get(option);

        if (st == null) {
            putItemRecord(option.copy());
        }
    }

    @Override
    public void addCrafting(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.records.get(option);

        if (st == null) {
            putItemRecord(option.copy());
        }
    }

    @Override
    public void addRequestable(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.records.get(option);

        if (st == null) {
            putItemRecord(option.copy());
        }
    }

    @Override
    public IAEItemStack getFirstItem() {
        for (final IAEItemStack stackType : this) {
            return stackType;
        }

        return null;
    }

    @Override
    public int size() {
        return this.records.size();
    }

    @Override
    @Nonnull
    public Iterator<IAEItemStack> iterator() {
        return this.records.iterator();
    }

    @Override
    public void resetStatus() {
        for (final IAEItemStack i : this) {
            i.reset();
        }
    }

    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return ITEM_STACK_TYPE;
    }

    public void clear() {
        this.records.clear();
    }

    private void putItemRecord(final IAEItemStack itemStack) {
        itemStack.setStackSize(1).setCraftable(false).setCountRequestable(0);
        this.records.add(itemStack);
    }
}
