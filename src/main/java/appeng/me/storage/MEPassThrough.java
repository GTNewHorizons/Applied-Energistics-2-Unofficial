/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.NetworkMonitor;

public class MEPassThrough<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final IAEStackType<T> wrappedStackType;
    private IMEInventory<T> internal;

    public MEPassThrough(final IMEInventory<T> i, final IAEStackType<T> type) {
        this.wrappedStackType = type;
        this.setInternal(i);
    }

    @Override
    public IMEInventory<T> getInternal() {
        return this.internal;
    }

    /**
     * Get Connected grid if applicable
     * 
     * @return Connected grid
     */
    public IGrid getGrid() {
        if (this.internal instanceof NetworkMonitor networkMonitor) {
            return networkMonitor.getGrid();
        }
        return null;
    }

    public void setInternal(final IMEInventory<T> i) {
        this.internal = i;
    }

    @Override
    public T injectItems(final T input, final Actionable type, final BaseActionSource src) {
        return this.internal.injectItems(input, type, src);
    }

    @Override
    public T extractItems(final T request, final Actionable type, final BaseActionSource src) {
        return this.internal.extractItems(request, type, src);
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        return this.internal.getAvailableItems(out, iteration);
    }

    @Override
    public T getAvailableItem(@Nonnull T request, int iteration) {
        return this.internal.getAvailableItem(request, iteration);
    }

    @Override
    public StorageChannel getChannel() {
        return this.internal.getChannel();
    }

    @Override
    public @NotNull IAEStackType<?> getStackType() {
        return this.internal.getStackType();
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final T input) {
        return false;
    }

    @Override
    public boolean canAccept(final T input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IMENetworkInventory<T> getExternalNetworkInventory() {
        if (internal instanceof IMENetworkInventory<?>networkInventory) {
            return (IMENetworkInventory<T>) networkInventory;
        }
        if (internal instanceof IMEInventoryHandler<?>inventoryHandler) {
            return (IMENetworkInventory<T>) inventoryHandler.getExternalNetworkInventory();
        }
        return null;
    }

    IAEStackType<T> getWrappedChannel() {
        return this.wrappedStackType;
    }
}
