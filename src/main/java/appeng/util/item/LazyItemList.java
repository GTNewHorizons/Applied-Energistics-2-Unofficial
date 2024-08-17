package appeng.util.item;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * IItemList that only gets populated on first use. Thread-safe if the supplied list and initializer function are
 * thread-safe.
 */
public final class LazyItemList<StackType extends IAEStack<StackType>> implements IItemList<StackType> {

    private volatile IItemList<StackType> cache = null;
    private final Supplier<IItemList<StackType>> computeFn;

    public LazyItemList(Supplier<IItemList<StackType>> computeFn) {
        this.computeFn = computeFn;
    }

    private IItemList<StackType> getCachedOrCompute() {
        if (cache == null) {
            // Use double-checked locking to only initialize cache once even if multiple threads race
            synchronized (this) {
                if (cache == null) {
                    cache = Objects.requireNonNull(computeFn.get());
                }
            }
        }
        return cache;
    }

    @Override
    public void add(StackType option) {
        getCachedOrCompute().add(option);
    }

    @Override
    public StackType findPrecise(StackType i) {
        return getCachedOrCompute().findPrecise(i);
    }

    @Override
    public Collection<StackType> findFuzzy(StackType input, FuzzyMode fuzzy) {
        return getCachedOrCompute().findFuzzy(input, fuzzy);
    }

    @Override
    public boolean isEmpty() {
        return getCachedOrCompute().isEmpty();
    }

    @Override
    public void addStorage(StackType option) {
        getCachedOrCompute().addStorage(option);
    }

    @Override
    public void addCrafting(StackType option) {
        getCachedOrCompute().addCrafting(option);
    }

    @Override
    public void addRequestable(StackType option) {
        getCachedOrCompute().addRequestable(option);
    }

    @Override
    public StackType getFirstItem() {
        return getCachedOrCompute().getFirstItem();
    }

    @Override
    public int size() {
        return getCachedOrCompute().size();
    }

    @Override
    public Iterator<StackType> iterator() {
        return getCachedOrCompute().iterator();
    }

    @Override
    public void resetStatus() {
        getCachedOrCompute().resetStatus();
    }

    @Override
    public StackType[] toArray(StackType[] zeroSizedArray) {
        return getCachedOrCompute().toArray(zeroSizedArray);
    }

    @Override
    public void forEach(Consumer<? super StackType> action) {
        getCachedOrCompute().forEach(action);
    }

    @Override
    public Spliterator<StackType> spliterator() {
        return getCachedOrCompute().spliterator();
    }
}
