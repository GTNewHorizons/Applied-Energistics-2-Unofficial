/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageInterceptor;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.util.IterationCounter;
import appeng.util.item.LazyItemList;
import appeng.util.item.NetworkItemList;
import appeng.util.item.PrioritizedNetworkItemList;

public class NetworkMonitor<T extends IAEStack<T>> implements IMEMonitor<T> {

    @Nonnull
    private static final Deque<NetworkMonitor<?>> GLOBAL_DEPTH = Lists.newLinkedList();

    @Nonnull
    private final GridStorageCache myGridCache;

    private final IAEStackType<T> stackType;

    @Nonnull
    private final IItemList<T> cachedList;

    @Nonnull
    private final Map<IMEMonitorHandlerReceiver, Object> listeners;

    private boolean sendEvent = false;

    /** {@link #cachedList} no longer reflects the network and must be rebuilt before it is handed out. */
    private boolean stale = true;

    /**
     * Whether {@link #cachedList} can be kept up to date from the change stream in {@link #postChange} instead of
     * rescanning every storage handler. Decided per rebuild, see {@link #canTrackIncrementally()}.
     */
    private boolean incremental = false;

    @Nonnegative
    private int localDepthSemaphore = 0;

    private volatile boolean locked = false;

    private final Set<IStorageInterceptor> storageInterceptors = Collections.newSetFromMap(new WeakHashMap<>());

    public NetworkMonitor(final GridStorageCache cache, final IAEStackType<T> type) {
        this.myGridCache = cache;
        this.stackType = type;
        this.cachedList = type.createList();
        this.listeners = new HashMap<>();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isLocked() {
        return this.locked;
    }

    private boolean isReshuffleSource(BaseActionSource src) {
        return src instanceof ReshuffleActionSource;
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public boolean canAccept(final T input) {
        return this.getHandler().canAccept(input);
    }

    @Override
    public T extractItems(final T request, final Actionable mode, final BaseActionSource src) {
        if (this.locked && !isReshuffleSource(src)) {
            return null;
        }

        if (mode == Actionable.SIMULATE) {
            return this.getHandler().extractItems(request, mode, src);
        }

        localDepthSemaphore++;
        final T leftover = this.getHandler().extractItems(request, mode, src);
        localDepthSemaphore--;

        if (localDepthSemaphore == 0) {
            this.monitorDifference(request.copy(), leftover, true, src);
        }

        return leftover;
    }

    @Override
    public AccessRestriction getAccess() {
        return this.getHandler().getAccess();
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList out, int iteration) {
        return this.getHandler().getAvailableItems(out, iteration);
    }

    @Override
    public PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(int iteration) {
        return this.getHandler().getAvailableItemsWithPriority(iteration);
    }

    @Override
    public StorageChannel getChannel() {
        return this.getHandler().getChannel();
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public @NotNull IAEStackType<T> getStackType() {
        return (IAEStackType<T>) this.getHandler().getStackType();
    }

    @Override
    public int getPriority() {
        return this.getHandler().getPriority();
    }

    @Override
    public int getSlot() {
        return this.getHandler().getSlot();
    }

    @Nonnull
    @Override
    public IItemList<T> getStorageList() {
        if (this.stale) {
            this.stale = false;
            this.cachedList.resetStatus();
            final IItemList<T> ret = this.getAvailableItems(this.cachedList, IterationCounter.fetchNewId());
            if (ret instanceof NetworkItemList) {
                for (T item : ret) {
                    this.cachedList.add(item);
                }
            }
            // A scan can post changes of its own (a storage bus doing its first onTick), which marked us stale
            // again. Rebuild next call rather than hand out a list that already drifted.
            this.incremental = !this.stale && this.canTrackIncrementally();
        }

        return this.cachedList;
    }

    /**
     * A subnet reachable through two storage buses is counted once by {@link NetworkItemList} during a scan, but its
     * change stream arrives once per bus, so applying those deltas would double count. Grids like that keep rebuilding
     * on change.
     */
    private boolean canTrackIncrementally() {
        final IMEInventoryHandler<T> handler = this.getHandler();
        return handler instanceof NetworkInventoryHandler
                && !((NetworkInventoryHandler<T>) handler).readsFromOtherNetwork();
    }

    @Override
    public T injectItems(T input, final Actionable mode, final BaseActionSource src) {
        if (this.locked && !isReshuffleSource(src)) {
            return input;
        }

        for (Iterator<IStorageInterceptor> iterator = storageInterceptors.iterator(); iterator.hasNext();) {
            final IStorageInterceptor isi = iterator.next();
            if (isi.canAccept(input)) {
                input = (T) isi.injectItems(input, mode, src);

                if (mode == Actionable.MODULATE && isi.shouldRemoveInterceptor(input)) iterator.remove();

                if (input == null) return null;
            }
        }

        if (mode == Actionable.SIMULATE) {
            return this.getHandler().injectItems(input, mode, src);
        }

        localDepthSemaphore++;
        final T leftover = this.getHandler().injectItems(input, mode, src);
        localDepthSemaphore--;

        if (localDepthSemaphore == 0) {
            this.monitorDifference(input.copy(), leftover, false, src);
        }

        return leftover;
    }

    @Override
    public boolean isPrioritized(final T input) {
        return this.getHandler().isPrioritized(input);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    @Override
    public boolean validForPass(final int i) {
        return this.getHandler().validForPass(i);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IMENetworkInventory<T> getExternalNetworkInventory() {
        IMEInventoryHandler<T> handler = this.getHandler();
        if (handler instanceof IMENetworkInventory<?>networkInventory) {
            return (IMENetworkInventory<T>) networkInventory;
        }
        return handler.getExternalNetworkInventory();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public IMEInventoryHandler<T> getHandler() {
        return (IMEInventoryHandler<T>) this.myGridCache.getInventoryHandler(this.stackType);
    }

    public IGrid getGrid() {
        return this.myGridCache.getGrid();
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    private T monitorDifference(final IAEStack original, final T leftOvers, final boolean extraction,
            final BaseActionSource src) {
        final T diff = (T) original.copy();

        if (extraction) {
            diff.setStackSize(leftOvers == null ? 0 : -leftOvers.getStackSize());
        } else if (leftOvers != null) {
            diff.decStackSize(leftOvers.getStackSize());
        }

        if (diff.getStackSize() != 0) {
            this.postChangesToListeners(ImmutableList.of(diff), src);
            final ItemFlowGridCache flowCache = this.myGridCache.getGrid().getCache(ItemFlowGridCache.class);
            flowCache.recordFlow(diff, src);
        }

        return leftOvers;
    }

    private void notifyListenersOfChange(final Iterable<IAEStack<?>> diff, final BaseActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
            final IMEMonitorHandlerReceiver receiver = o.getKey();
            if (receiver.isValid(o.getValue())) {
                receiver.postChange(this, diff, src);
            } else {
                i.remove();
            }
        }
    }

    private void postChangesToListeners(final Iterable<IAEStack<?>> changes, final BaseActionSource src) {
        this.postChange(true, changes, src);
    }

    protected void postChange(final boolean add, final Iterable<IAEStack<?>> changes, final BaseActionSource src) {
        // Maintain the cached list *above* the re-entrancy guard below: that guard only exists to stop recursive
        // listener dispatch, but the change itself is real either way, and a delta dropped here would drift the
        // cache permanently instead of merely skipping a notification.
        // Removals are never applied incrementally: IAEStack#add ORs isCraftable and can never clear it, so a
        // negated stack cannot undo a craftable entry. They mark the list stale instead. That only happens when a
        // cell provider is deactivated, which already coincides with MENetworkCellArrayUpdate.
        if (this.incremental && add) {
            // ponytail: a negative delta for an item absent from the list inserts a phantom negative entry rather
            // than being rejected, and it survives until the next rebuild. Not guarded because it can only happen
            // if a delta was already lost upstream; add a findPrecise check here if drift shows up in the wild.
            for (final IAEStack<?> changedItem : changes) {
                if (changedItem != null) {
                    this.cachedList.add((T) changedItem);
                }
            }
        } else {
            this.invalidate();
        }

        if (localDepthSemaphore > 0 || GLOBAL_DEPTH.contains(this)) {
            return;
        }

        GLOBAL_DEPTH.push(this);
        localDepthSemaphore++;

        this.sendEvent = true;

        this.notifyListenersOfChange(changes, src);

        for (final IAEStack<?> changedItem : changes) {
            if (changedItem == null) {
                continue;
            }

            T difference = (T) changedItem;

            if (!add) {
                difference = (T) changedItem.copy();
                difference.setStackSize(-changedItem.getStackSize());
            }

            if (this.myGridCache.getInterestManager().containsKey(changedItem)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(changedItem);

                if (!list.isEmpty()) {
                    IAEStack<T> fullStack = this.getHandler()
                            .getAvailableItem((T) changedItem, IterationCounter.fetchNewId());

                    if (fullStack == null) {
                        fullStack = changedItem.copy();
                        fullStack.setStackSize(0);
                    }

                    this.myGridCache.getInterestManager().enableTransactions();

                    IItemList<T> itemList = new LazyItemList<>(this::getStorageList);
                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(itemList, fullStack, difference, src, this.getChannel());
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        final NetworkMonitor<?> last = GLOBAL_DEPTH.pop();
        localDepthSemaphore--;

        if (last != this) {
            throw new IllegalStateException("Invalid Access to Networked Storage API detected.");
        }
    }

    /** Drops the cached list; the next {@link #getStorageList()} rebuilds it from the storage handlers. */
    void invalidate() {
        this.stale = true;
        this.incremental = false;
    }

    void forceUpdate() {
        this.invalidate();

        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
            final IMEMonitorHandlerReceiver receiver = o.getKey();

            if (receiver.isValid(o.getValue())) {
                receiver.onListUpdate();
            } else {
                i.remove();
            }
        }
    }

    void onTick() {
        if (this.sendEvent) {
            this.sendEvent = false;
            this.myGridCache.getGrid().postEvent(new MENetworkStorageEvent(this, this.stackType));
        }
    }

    public void addStorageInterceptor(IStorageInterceptor storageInterceptor) {
        this.storageInterceptors.add(storageInterceptor);
    }

    public void removeStorageInterceptor(IStorageInterceptor storageInterceptor) {
        this.storageInterceptors.remove(storageInterceptor);
    }
}
