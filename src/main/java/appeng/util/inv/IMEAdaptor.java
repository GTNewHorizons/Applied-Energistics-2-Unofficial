/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.inv;

import java.util.Iterator;

import net.minecraft.item.ItemStack;

import com.google.common.collect.ImmutableList;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.InventoryAdaptor;
import appeng.util.IterationCounter;
import appeng.util.item.AEItemStack;

public class IMEAdaptor extends InventoryAdaptor {

    private final IMEInventory<IAEItemStack> target;
    private final BaseActionSource src;
    private int maxSlots = 0;

    public IMEAdaptor(final IMEInventory<IAEItemStack> input, final BaseActionSource src) {
        this.target = input;
        this.src = src;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return new IMEAdaptorIterator(this, this.getList());
    }

    private IItemList<IAEItemStack> getList() {
        return this.target
                .getAvailableItems(AEApi.instance().storage().createItemList(), IterationCounter.fetchNewId());
    }

    @Override
    public IItemList getAvailableItems(IItemList out, int iteration) {
        return this.target.getAvailableItems(out, iteration);
    }

    @Override
    public ItemStack removeItems(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.MODULATE);
    }

    private ItemStack doRemoveItems(final int amount, final ItemStack filter, final IInventoryDestination destination,
            final Actionable type) {
        IAEItemStack req = null;

        if (filter == null) {
            final IItemList<IAEItemStack> list = this.getList();
            if (!list.isEmpty()) {
                req = list.getFirstItem();
            }
        } else {
            req = AEItemStack.create(filter);
        }

        IAEItemStack out = null;

        if (req != null) {
            req.setStackSize(amount);
            out = this.target.extractItems(req, type, this.src);
        }

        if (out != null) {
            return out.getItemStack();
        }

        return null;
    }

    @Override
    public ItemStack simulateRemove(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.SIMULATE);
    }

    @Override
    public ItemStack removeSimilarItems(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode,
            final IInventoryDestination destination) {
        if (filter == null) {
            return this.doRemoveItems(amount, null, destination, Actionable.MODULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.MODULATE, fuzzyMode);
    }

    private ItemStack doRemoveItemsFuzzy(final int amount, final ItemStack filter,
            final IInventoryDestination destination, final Actionable type, final FuzzyMode fuzzyMode) {
        final IAEItemStack reqFilter = AEItemStack.create(filter);
        if (reqFilter == null) {
            return null;
        }

        IAEItemStack out = null;

        for (final IAEItemStack req : ImmutableList.copyOf(this.getList().findFuzzy(reqFilter, fuzzyMode))) {
            if (req != null) {
                req.setStackSize(amount);
                out = this.target.extractItems(req, type, this.src);
                if (out != null) {
                    return out.getItemStack();
                }
            }
        }

        return null;
    }

    @Override
    public ItemStack simulateSimilarRemove(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode,
            final IInventoryDestination destination) {
        if (filter == null) {
            return this.doRemoveItems(amount, null, destination, Actionable.SIMULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.SIMULATE, fuzzyMode);
    }

    @Override
    public ItemStack addItems(final ItemStack toBeAdded) {
        final IAEItemStack in = AEItemStack.create(toBeAdded);
        if (in != null) {
            final IAEItemStack out = this.target.injectItems(in, Actionable.MODULATE, this.src);
            if (out != null) {
                return out.getItemStack();
            }
        }
        return null;
    }

    @Override
    public ItemStack simulateAdd(final ItemStack toBeSimulated) {
        final IAEItemStack in = AEItemStack.create(toBeSimulated);
        if (in != null) {
            final IAEItemStack out = this.target.injectItems(in, Actionable.SIMULATE, this.src);
            if (out != null) {
                return out.getItemStack();
            }
        }
        return null;
    }

    @Override
    public boolean containsItems() {
        return !this.getList().isEmpty();
    }

    int getMaxSlots() {
        return this.maxSlots;
    }

    void setMaxSlots(final int maxSlots) {
        this.maxSlots = maxSlots;
    }
}
