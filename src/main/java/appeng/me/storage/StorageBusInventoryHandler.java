package appeng.me.storage;

import java.util.function.Predicate;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.ItemFilterList;
import appeng.util.item.NetworkItemList;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    public StorageBusInventoryHandler(IMEInventory<T> i, StorageChannel channel) {
        super(i, channel);
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        if (!this.hasReadAccess && !isVisible()) {
            return out;
        }

        if (out instanceof ItemFilterList) return this.getAvailableItemsFilter(out, iteration);

        if (this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()) {
            return this.filterAvailableItems(out, iteration);
        } else {
            return this.getAvailableItems(out, iteration, e -> true);
        }
    }

    @Override
    protected IItemList<T> filterAvailableItems(IItemList<T> out, int iteration) {
        Predicate<T> filterCondition = this.getExtractFilterCondition();
        return getAvailableItems(out, iteration, filterCondition);
    }

    @SuppressWarnings("unchecked")
    private IItemList<T> getAvailableItems(IItemList<T> out, int iteration, Predicate<T> filterCondition) {
        final IItemList<T> availableItems = this.getInternal()
                .getAvailableItems((IItemList<T>) getChannel().createList(), iteration);
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
}
