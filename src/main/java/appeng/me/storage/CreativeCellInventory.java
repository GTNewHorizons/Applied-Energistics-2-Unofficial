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

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.glodblock.github.util.Util;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.items.contents.CellConfig;
import appeng.tile.inventory.IAEStackInventory;

public class CreativeCellInventory<StackType extends IAEStack<StackType>>
        implements IMEInventoryHandler<StackType>, ICellInventory<StackType> {

    private final IItemList<StackType> listCache;
    private final ItemStack cellItem;
    private final IStorageCell cellType;

    protected CreativeCellInventory(final ItemStack o) {
        this.cellItem = o;
        this.cellType = (IStorageCell) o.getItem();
        this.listCache = getChannel().createPrimitiveList();

        final IAEStackInventory cc = o.getItem() instanceof IStorageCell sc ? sc.getConfigAEInventory(o)
                : new IAEStackInventory(null, 0);
        for (int i = 0; i < cc.getSizeInventory(); i++) {
            IAEStack<?> aes = cc.getAEStackInSlot(i);
            if (aes != null) {
                if (getChannel() == StorageChannel.FLUIDS && aes instanceof IAEItemStack ais) {
                    aes = Util.getAEFluidFromItem(ais.getItemStack());
                }

                aes.setStackSize((long) (Math.pow(2, 52) - 1));
                this.listCache.add((StackType) aes);
            }
        }
    }

    public static IMEInventoryHandler getCell(final ItemStack o, StorageChannel sc) {
        if (sc == StorageChannel.ITEMS) return new ItemCellInventoryHandler(new CreativeCellInventory<>(o));
        if (sc == StorageChannel.FLUIDS) return new FluidCellInventoryHandler(new CreativeCellInventory<>(o));
        return null;
    }

    @Override
    public StackType injectItems(final StackType input, final Actionable mode, final BaseActionSource src) {
        final StackType local = this.listCache.findPrecise(input);
        if (local == null) {
            return input;
        }

        return null;
    }

    @Override
    public StackType extractItems(final StackType request, final Actionable mode, final BaseActionSource src) {
        final StackType local = this.listCache.findPrecise(request);
        if (local == null) {
            return null;
        }

        return request.copy();
    }

    @Override
    public IItemList<StackType> getAvailableItems(final IItemList out, int iteration) {
        for (final StackType ais : this.listCache) {
            out.add(ais);
        }
        return out;
    }

    @Override
    public StackType getAvailableItem(@Nonnull StackType request, int iteration) {
        final StackType local = this.listCache.findPrecise(request);
        if (local == null) {
            return null;
        }

        return local.copy();
    }

    @Override
    public StorageChannel getChannel() {
        return this.cellType.getStorageChannel();
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final StackType input) {
        return this.listCache.findPrecise(input) != null;
    }

    @Override
    public boolean canAccept(final StackType input) {
        return this.listCache.findPrecise(input) != null;
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
    public IAEStackInventory getConfigAEInventory() {
        return new CellConfig(this.cellItem);
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return 0;
    }

    @Override
    public boolean canHoldNewItem() {
        return false;
    }

    @Override
    public long getTotalBytes() {
        return 0;
    }

    @Override
    public long getFreeBytes() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalItemTypes() {
        return 0;
    }

    @Override
    public long getStoredItemCount() {
        return 0;
    }

    @Override
    public long getStoredItemTypes() {
        return 0;
    }

    @Override
    public long getRemainingItemTypes() {
        return 0;
    }

    @Override
    public long getRemainingItemCount() {
        return 0;
    }

    @Override
    public long getRemainingItemsCountDist(StackType l) {
        return 0;
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public int getStatusForCell() {
        return 0;
    }

    @Override
    public String getOreFilter() {
        return "";
    }
}
