package appeng.me.storage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.NetworkMonitor;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    private static final ThreadLocal<Map<Integer, Map<NetworkInventoryHandler<?>, IItemList<?>>>> networkItemsForIteration = new ThreadLocal<>();

    public StorageBusInventoryHandler(IMEInventory<T> i, StorageChannel channel) {
        super(i, channel);
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        if (!this.hasReadAccess && !isVisible()) {
            return out;
        }

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
        final IItemList<T> allAvailableItems = this.getAllAvailableItems(iteration);
        Iterator<T> it = allAvailableItems.iterator();
        while (it.hasNext()) {
            T items = it.next();
            if (filterCondition.test(items)) {
                out.add(items);
                // have to remove the item otherwise it could be counted double
                it.remove();
            }
        }
        return out;
    }

    private IItemList<T> getAllAvailableItems(int iteration) {
        NetworkInventoryHandler<T> networkInventoryHandler = getNetworkInventoryHandler();
        if (networkInventoryHandler == null) {
            return this.getInternal()
                    .getAvailableItems((IItemList<T>) this.getInternal().getChannel().createList(), iteration);
        }

        Map<Integer, Map<NetworkInventoryHandler<?>, IItemList<?>>> s = networkItemsForIteration.get();
        if (s != null && !s.containsKey(iteration)) {
            s = null;
        }
        if (s == null) {
            s = Collections.singletonMap(iteration, new IdentityHashMap<>());
            networkItemsForIteration.set(s);
        }
        Map<NetworkInventoryHandler<?>, IItemList<?>> networkInventoryItems = s.get(iteration);
        if (!networkInventoryItems.containsKey(networkInventoryHandler)) {
            IItemList<T> allAvailableItems = this.getInternal()
                    .getAvailableItems((IItemList<T>) this.getInternal().getChannel().createList(), iteration);
            networkInventoryItems.put(networkInventoryHandler, allAvailableItems);
        }

        return (IItemList<T>) networkInventoryItems.get(networkInventoryHandler);
    }

    /**
     * Find the NetworkInventoryHandler for this storage bus
     */
    private NetworkInventoryHandler<T> getNetworkInventoryHandler() {
        return (NetworkInventoryHandler<T>) findNetworkInventoryHandler(this.getInternal());
    }

    private NetworkInventoryHandler<?> findNetworkInventoryHandler(IMEInventory<?> inventory) {
        if (inventory instanceof MEPassThrough<?>passThrough) {
            return findNetworkInventoryHandler(passThrough.getInternal());
        } else if (inventory instanceof NetworkMonitor<?>networkMonitor) {
            return findNetworkInventoryHandler(networkMonitor.getHandler());
        } else if (inventory instanceof NetworkInventoryHandler<?>networkInventoryHandler) {
            return networkInventoryHandler;
        } else {
            return null;
        }
    }
}
