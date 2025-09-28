package appeng.me.storage;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.ItemFilterList;

import java.util.Iterator;
import java.util.function.Predicate;

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
        getAvailableItems(out, iteration, filterCondition);
        return out;
    }

    private IItemList<T> getAvailableItems(IItemList<T> out, int iteration, Predicate<T> filterCondition) {
        final IItemList<T> unreadAvailableItems = this.getUnreadAvailableItems(iteration);
        Iterator<T> it = unreadAvailableItems.iterator();
        while (it.hasNext()) {
            T items = it.next();
            if (filterCondition.test(items)) {
                out.add(items);
                // remove the item since it was read
                it.remove();
            }
        }
        return out;
    }

    private IItemList<T> getUnreadAvailableItems(int iteration) {
        return getNetworkInventory().getUnreadAvailableItems(iteration);
    }
}
