package appeng.me.storage;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import appeng.api.config.AccessRestriction;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.ItemFilterList;
import appeng.util.item.NetworkItemList;
import appeng.util.item.PrioritizedNetworkItemList;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    private final Supplier<AccessRestriction> reshuffleAccess;

    public StorageBusInventoryHandler(IMEInventory<T> i, IAEStackType<T> type,
            Supplier<AccessRestriction> reshuffleAccess) {
        super(i, type);
        this.reshuffleAccess = reshuffleAccess;
    }

    @Override
    public AccessRestriction getReshuffleAccess() {
        return this.getAccess().restrictPermissions(this.reshuffleAccess.get())
                .restrictPermissions(super.getReshuffleAccess());
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        return this.getAvailableItems(out, iteration, Optional.empty());
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration, Optional<Predicate<T>> filter) {
        if (!this.hasReadAccess && !isVisible()) {
            return out;
        }

        if (out instanceof ItemFilterList) return this.getAvailableItemsFilter(out, iteration);

        Predicate<T> storageBusFilter = $ -> true;

        if (this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()) {
            storageBusFilter = this.getExtractFilterCondition();
        }

        final IItemList<T> availableItems = this.getInternal()
                .getAvailableItems((IItemList<T>) this.getStackType().createList(), iteration, filter);

        if (availableItems instanceof NetworkItemList networkItemList) {
            // when we cross between networks on a NetworkInventoryHandler dive, we need to break the "out" contract to
            // avoid modifying the passed in list which belongs to a different network (and would cause double-counting
            // for triangle-shaped networks)
            NetworkItemList<T> itemList = new NetworkItemList<>(networkItemList);
            itemList.addFilter(storageBusFilter);
            return itemList;
        } else {
            // for non-cross-network dives, we need to honor the "out" contract
            // and put the results in the passed in list
            for (T items : availableItems) {
                if (storageBusFilter == null || storageBusFilter.test(items)) {
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
