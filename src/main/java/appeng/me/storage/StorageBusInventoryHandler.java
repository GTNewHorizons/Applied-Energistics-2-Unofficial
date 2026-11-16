package appeng.me.storage;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.NetworkMonitor;
import appeng.util.item.ItemFilterList;
import appeng.util.item.NetworkItemList;
import appeng.util.item.PrioritizedNetworkItemList;
import appeng.util.target.TileTarget;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    private final Supplier<AccessRestriction> reshuffleAccess;
    private final TileTarget target;
    private final IMEInventory<T> inventory;
    private boolean retired;

    public StorageBusInventoryHandler(IMEInventory<T> inventory, IAEStackType<T> type,
            Supplier<AccessRestriction> reshuffleAccess, TileTarget target) {
        super(inventory, type);
        this.reshuffleAccess = reshuffleAccess;
        this.target = target;
        this.inventory = inventory;
    }

    public boolean isValid() {
        return !this.retired && this.target.isValid();
    }

    /** Called when the owner replaces this handler. A retired handler stays invalid even if its target is valid. */
    public void invalidate() {
        this.retired = true;
    }

    public TileTarget getTarget() {
        return this.target;
    }

    public IMEInventory<T> getInventory() {
        return this.inventory;
    }

    /**
     * Returns the contents that the network was told about, to post their removal. Unlike
     * {@link #getAvailableItems(IItemList, int)}, this prefers the published view even while the handler is valid.
     */
    public IItemList<T> getItemsForRemoval(IItemList<T> out, int iteration) {
        final IItemList<T> published = this.getPublishedItems(out, iteration, Optional.empty());
        if (published != null) return published;
        return this.isValid() ? this.getAvailableItemsFromInventory(out, iteration, Optional.empty()) : out;
    }

    /**
     * Reads the contents published to the network without touching the target's world, which may be unloaded.
     *
     * @return null if the inventory has no published view
     */
    private @Nullable IItemList<T> getPublishedItems(IItemList<T> out, int iteration,
            Optional<Predicate<T>> requestFilter) {
        // A pass-through forwards the changes of its internal inventory, so that inventory is the published view.
        final IMEInventory<?> view = this.inventory instanceof MEMonitorPassThrough<?>passThrough
                ? passThrough.getInternal()
                : this.inventory;
        if (view instanceof NetworkMonitor<?>) {
            // The subnet may outlive the interface tile. If setInternal has already changed the view, the listener
            // has forwarded its delta.
            return this.getAvailableItemsFromInventory(out, iteration, requestFilter);
        }
        if (view instanceof IMEMonitor<?>monitor) {
            // The owner listens to this monitor, so its storage list is what has been forwarded to the network.
            // Monitors such as MEMonitorHandler rebuild the list from the detached tile's in-memory state, which does
            // not access the world.
            if (!this.hasReadAccess && !this.isVisible()) return out;
            final Predicate<T> filter = this.getStorageBusFilter();
            final Predicate<T> combined = requestFilter.map(filter::and).orElse(filter);
            for (T item : (IItemList<T>) monitor.getStorageList()) {
                if (combined.test(item)) out.add(item);
            }
            return out;
        }
        // Reading a plain inventory may access the world around a detached tile.
        return null;
    }

    private Predicate<T> getStorageBusFilter() {
        return this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()
                ? this.getExtractFilterCondition()
                : item -> true;
    }

    @Override
    public boolean canAccept(T input) {
        return this.isValid() && super.canAccept(input);
    }

    @Override
    public T extractItems(T request, Actionable type, BaseActionSource src) {
        return this.isValid() ? super.extractItems(request, type, src) : null;
    }

    @Override
    public T getAvailableItem(@Nonnull T request, int iteration) {
        return this.isValid() ? super.getAvailableItem(request, iteration) : null;
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
        if (this.isValid()) return this.getAvailableItemsFromInventory(out, iteration, filter);
        // Keep reporting what was published, so the grid can post its removal when the storage bus leaves the grid.
        final IItemList<T> published = this.getPublishedItems(out, iteration, filter);
        return published == null ? out : published;
    }

    private IItemList<T> getAvailableItemsFromInventory(final IItemList<T> out, int iteration,
            Optional<Predicate<T>> filter) {
        if (!this.hasReadAccess && !isVisible()) return out;

        if (out instanceof ItemFilterList) return this.getAvailableItemsFilter(out, iteration);

        final Predicate<T> storageBusFilter = this.getStorageBusFilter();

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
        if (!this.isValid()) return new PrioritizedNetworkItemList<>(this.getExternalNetworkInventory());
        return this.getAvailableItemsWithPriority(iteration, this.getStorageBusFilter());
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
