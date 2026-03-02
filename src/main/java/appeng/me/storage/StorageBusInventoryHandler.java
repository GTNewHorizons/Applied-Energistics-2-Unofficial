package appeng.me.storage;

import java.util.Optional;
import java.util.function.Predicate;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.ItemFilterList;
import appeng.util.item.NetworkItemList;
import appeng.util.item.PrioritizedNetworkItemList;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    public StorageBusInventoryHandler(IMEInventory<T> i, IAEStackType<T> type) {
        super(i, type);
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        return this.getAvailableItems(out, iteration, Optional.empty());
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration, Optional<Predicate<T>> preFilter) {
        if (!this.hasReadAccess && !isVisible()) {
            return out;
        }

        if (out instanceof ItemFilterList) return this.getAvailableItemsFilter(out, iteration, preFilter);

        Predicate<T> filterCondition = preFilter.orElse(null);

        if (this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()) {
            Predicate<T> extractFilter = this.getExtractFilterCondition();
            filterCondition = filterCondition == null ? extractFilter : extractFilter.and(filterCondition);
        }

        return this.getAvailableItemsInternal(out, iteration, filterCondition == null ? e -> true : filterCondition);
    }

    @Override
    protected IItemList<T> filterAvailableItems(IItemList<T> out, int iteration) {
        Predicate<T> filterCondition = this.getExtractFilterCondition();
        return getAvailableItemsInternal(out, iteration, filterCondition);
    }

    @SuppressWarnings("unchecked")
    private IItemList<T> getAvailableItemsInternal(IItemList<T> out, int iteration, Predicate<T> filterCondition) {
        final IItemList<T> availableItems = this.getInternal().getAvailableItems(
                (IItemList<T>) this.getStackType().createList(),
                iteration,
                Optional.of(filterCondition));
        if (availableItems instanceof NetworkItemList) {
            NetworkItemList<T> networkItemList = new NetworkItemList<>((NetworkItemList<T>) availableItems);
            networkItemList.addFilter(filterCondition);
            return networkItemList;
        } else {
            for (T items : availableItems) {
                if (filterCondition.test(items)) {
                    out.add(items);
                }
            }
            return out;
        }
    }

    @Override
    public PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(int iteration) {
        final Predicate<T> predicate = this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()
                ? this.getExtractFilterCondition()
                : e -> true;
        return this.getAvailableItemsWithPriority(iteration, predicate);
    }

    private PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(int iteration, Predicate<T> filterCondition) {
        final IMENetworkInventory<T> externalNetworkInventory = this.getExternalNetworkInventory();
        final PrioritizedNetworkItemList<T> available = externalNetworkInventory
                .getAvailableItemsWithPriority(iteration);

        final PrioritizedNetworkItemList<T> copy = new PrioritizedNetworkItemList<>(available);
        copy.addFilter(filterCondition);
        return copy;
    }
}
