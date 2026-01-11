package appeng.util.item;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

public final class IAEStackList implements IItemList<IAEStack<?>> {

    @SuppressWarnings({ "rawtypes" })
    private final Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();

    public IAEStackList() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.lists.put(type, type.createList());
        }
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public void add(final IAEStack<?> option) {
        if (option != null) {
            this.lists.get(option.getStackType()).add(option);
        }
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public IAEStack<?> findPrecise(final IAEStack<?> stack) {
        if (stack != null) {
            return this.lists.get(stack.getStackType()).findPrecise(stack);
        }
        return null;
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public Collection<IAEStack<?>> findFuzzy(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        if (filter != null) {
            return this.lists.get(filter.getStackType()).findFuzzy(filter, fuzzy);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public void addStorage(final IAEStack<?> option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addStorage(option);
        }
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public void addCrafting(final IAEStack<?> option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addCrafting(option);
        }
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public void addRequestable(final IAEStack<?> option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addRequestable(option);
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
        int size = 0;
        for (IItemList<?> list : this.lists.values()) {
            size += list.size();
        }
        return size;
    }

    @Override
    @Nonnull
    @SuppressWarnings({ "rawtypes" })
    public Iterator<IAEStack<?>> iterator() {
        return new MeaningfulAEStackIterator<>(new Iterator<>() {

            private final Iterator<IItemList> listIterator = lists.values().iterator();
            private Iterator<?> currentIterator;

            @Override
            public boolean hasNext() {
                if (currentIterator == null || !currentIterator.hasNext()) {
                    while (listIterator.hasNext()) {
                        currentIterator = listIterator.next().iterator();
                        if (currentIterator.hasNext()) return true;
                    }
                    return false;
                }
                return true;
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
    public @Nullable IAEStackType<IAEStack<?>> getStackType() {
        return null;
    }
}
