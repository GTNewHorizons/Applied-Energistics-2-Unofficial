package appeng.util.item;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nonnull;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

public final class IAEStackList implements IItemList<IAEStack<?>> {

    private final IItemList<IAEItemStack> itemList = AEApi.instance().storage().createItemList();
    private final IItemList<IAEFluidStack> fluidList = AEApi.instance().storage().createFluidList();

    @Override
    public void add(final IAEStack<?> option) {
        if (option != null) {
            if (option.isItem()) {
                itemList.add((IAEItemStack) option);
            } else {
                fluidList.add((IAEFluidStack) option);
            }
        }
    }

    @Override
    public IAEStack<?> findPrecise(final IAEStack<?> stack) {
        if (stack != null) {
            if (stack.isItem()) {
                return itemList.findPrecise((IAEItemStack) stack);
            } else {
                return fluidList.findPrecise((IAEFluidStack) stack);
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<IAEStack<?>> findFuzzy(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        if (filter != null) {
            if (filter.isItem()) {
                return (Collection) itemList.findFuzzy((IAEItemStack) filter, fuzzy);
            } else {
                return (Collection) fluidList.findFuzzy((IAEFluidStack) filter, fuzzy);
            }
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    public void addStorage(final IAEStack<?> option) {
        if (option != null) {
            if (option.isItem()) {
                itemList.addStorage((IAEItemStack) option);
            } else {
                fluidList.addStorage((IAEFluidStack) option);
            }
        }
    }

    @Override
    public void addCrafting(final IAEStack<?> option) {
        if (option != null) {
            if (option.isItem()) {
                itemList.addCrafting((IAEItemStack) option);
            } else {
                fluidList.addCrafting((IAEFluidStack) option);
            }
        }
    }

    @Override
    public void addRequestable(final IAEStack<?> option) {
        if (option != null) {
            if (option.isItem()) {
                itemList.addRequestable((IAEItemStack) option);
            } else {
                fluidList.addRequestable((IAEFluidStack) option);
            }
        }
    }

    @Override
    public IAEStack<?> getFirstItem() {
        for (final IAEStack<?> stackType : this) {
            return stackType;
        }
        return null;
    }

    @Override
    public int size() {
        return itemList.size() + fluidList.size();
    }

    @Override
    @Nonnull
    public Iterator<IAEStack<?>> iterator() {
        return new MeaningfulAEStackIterator<>(new Iterator<>() {

            private final Iterator<IAEItemStack> itemIterator = itemList.iterator();
            private final Iterator<IAEFluidStack> fluidIterator = fluidList.iterator();
            private Iterator<?> currentIterator;

            @Override
            public boolean hasNext() {
                if (itemIterator.hasNext()) {
                    currentIterator = itemIterator;
                    return true;
                }
                if (fluidIterator.hasNext()) {
                    currentIterator = fluidIterator;
                    return true;
                }
                return false;
            }

            @Override
            public IAEStack<?> next() {
                return (IAEStack<?>) currentIterator.next();
            }

            @Override
            public void remove() {
                currentIterator.remove();
            }
        });
    }

    @Override
    public void resetStatus() {
        for (final IAEStack<?> i : this) {
            i.reset();
        }
    }

    @Override
    public byte getStackType() {
        return LIST_MIXED;
    }
}
