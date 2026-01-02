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

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;
import appeng.util.Platform;

public abstract class CellInventory<StackType extends IAEStack<StackType>> implements ICellInventory<StackType> {

    private static final String STACK_SLOT = "#";
    private static final String STACK_SLOT_COUNT = "@";
    private final String[] slots;
    private final String[] slotCount;
    private final NBTTagCompound tagCompound;
    private final ISaveProvider container;
    private int maxTypes = 63;
    private short storedTypes = 0;
    private long storedCount = 0;
    private IItemList<StackType> cellStacks;
    private final ItemStack cellItem;
    private final IStorageCell cellType;
    private boolean cardVoidOverflow = false;
    private boolean cardDistribution = false;
    private byte restrictionTypes = 0;
    private long restrictionLong = 0;
    private int typeWeight = 8;
    private final int distTypesCount;

    protected CellInventory(final ItemStack o, final ISaveProvider container) throws AppEngException {
        slots = new String[this.maxTypes];
        slotCount = new String[this.maxTypes];

        for (int x = 0; x < this.maxTypes; x++) {
            slots[x] = STACK_SLOT + x;
            slotCount[x] = STACK_SLOT_COUNT + x;
        }

        if (o == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        this.cellItem = o;

        if (this.cellItem.getItem() instanceof IStorageCell type) {
            this.cellType = type;
            this.maxTypes = this.cellType.getTotalTypes(this.cellItem);
        } else this.cellType = null;

        if (this.cellType == null) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        if (!this.cellType.isStorageCell(this.cellItem)) {
            throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
        }

        if (this.maxTypes > 63) {
            this.maxTypes = 63;
        }
        if (this.maxTypes < 1) {
            this.maxTypes = 1;
        }

        final IInventory upgrades = this.getUpgradesInventory();
        for (int x = 0; x < upgrades.getSizeInventory(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            if (is != null && is.getItem() instanceof IUpgradeModule) {
                final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                if (u != null) {
                    switch (u) {
                        case VOID_OVERFLOW -> cardVoidOverflow = true;
                        case DISTRIBUTION -> cardDistribution = true;
                        default -> {}
                    }
                }
            }
        }

        this.container = container;
        this.tagCompound = Platform.openNbtData(o);
        this.storedTypes = this.tagCompound.getShort(getStackTypeTag());
        this.storedCount = this.tagCompound.getLong(getStackCountTag());
        this.restrictionTypes = this.tagCompound.getByte("cellRestrictionTypes");
        this.restrictionLong = this.tagCompound.getLong("cellRestrictionAmount");
        this.cellStacks = null;

        if (this.restrictionTypes > 0) this.distTypesCount = this.restrictionTypes;
        else {
            int types = 0;
            final IAEStackInventory config = this.getConfigAEInventory();
            for (int i = 0; i < config.getSizeInventory(); i++) {
                if (config.getAEStackInSlot(i) != null) types++;
            }

            this.distTypesCount = types == 0 ? this.maxTypes : Math.min(types, this.maxTypes);
        }
    }

    private static boolean isStorageCell(final IAEStack<?> itemStack) {
        if (!(itemStack instanceof IAEItemStack ais)) {
            return false;
        }

        try {
            final Item type = ais.getItem();

            if (type instanceof IStorageCell sc) {
                return !sc.storableInStorageCell();
            }
        } catch (final Throwable err) {
            return true;
        }

        return false;
    }

    public static boolean isCell(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        final Item type = itemStack.getItem();

        if (type instanceof IStorageCell cell) {
            return cell.isStorageCell(itemStack);
        }

        return false;
    }

    public static IMEInventoryHandler<?> getCell(final ItemStack o, final ISaveProvider container2, StorageChannel sc) {
        try {
            if (sc == StorageChannel.ITEMS) return new ItemCellInventoryHandler(new ItemCellInventory(o, container2));
            if (sc == StorageChannel.FLUIDS)
                return new FluidCellInventoryHandler(new FluidCellInventory(o, container2));
        } catch (final AppEngException ignored) {}
        return null;
    }

    private boolean isEmpty(final IMEInventory<?> meInventory) {
        return ((IMEInventory<StackType>) meInventory)
                .getAvailableItems(this.getChannel().createPrimitiveList(), IterationCounter.fetchNewId()).isEmpty();
    }

    @Override
    public StackType injectItems(final StackType input, final Actionable mode, final BaseActionSource src) {
        if (input == null) {
            return null;
        }

        if (input.getStackSize() == 0) {
            return null;
        }

        if (this.cellType.isBlackListed(input)) {
            return input;
        }

        if (CellInventory.isStorageCell(input)) {
            final ItemStack cellStack = ((IAEItemStack) input).getItemStack();
            final CellInventoryHandler<?> cellInventory = (CellInventoryHandler<?>) getCell(
                    cellStack,
                    null,
                    ((IStorageCell) cellStack.getItem()).getStorageChannel());

            // same as isEmpty but less effort
            if (cellInventory != null && cellInventory.getUsedBytes() > 0) {
                return input;
            }
        }

        if (mode == Actionable.MODULATE && input.isCraftable()) {
            input.setCraftable(false);
        }

        final StackType l = this.getCellStacks().findPrecise(input);

        if (l != null) {
            long remainingItemSlots;
            if (cardDistribution) {
                remainingItemSlots = this.getRemainingItemsCountDist(l);
            } else {
                remainingItemSlots = this.getRemainingItemCount();
            }

            if (remainingItemSlots <= 0) {
                if (cardVoidOverflow) {
                    return null;
                }
                return input;
            }

            if (input.getStackSize() > remainingItemSlots) {
                final StackType r = input.copy();
                r.setStackSize(r.getStackSize() - remainingItemSlots);

                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + remainingItemSlots);
                    this.updateItemCount(remainingItemSlots);
                    this.saveChanges();
                }

                return r;
            } else {
                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() + input.getStackSize());
                    this.updateItemCount(input.getStackSize());
                    this.saveChanges();
                }

                return null;
            }
        }

        if (this.canHoldNewItem()) // room for new type, and for at least one item!
        {
            long remainingItemCount;
            if (cardDistribution) {
                remainingItemCount = this.getRemainingItemsCountDist(null);
            } else {
                if (restrictionLong > 0) {
                    remainingItemCount = this.getRemainingItemCount();
                } else {
                    remainingItemCount = this.getRemainingItemCount() - this.getBytesPerType() * (long) this.typeWeight;
                }
            }

            if (remainingItemCount > 0) {
                if (input.getStackSize() > remainingItemCount) {
                    final StackType toReturn = input.copy();
                    toReturn.decStackSize(remainingItemCount);

                    if (mode == Actionable.MODULATE) {
                        final StackType toWrite = input.copy();
                        toWrite.setStackSize(remainingItemCount);

                        this.cellStacks.add(toWrite);
                        this.updateItemCount(toWrite.getStackSize());
                        this.saveChanges();
                    }
                    return toReturn;
                }

                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(input.getStackSize());
                    this.cellStacks.add(input);
                    this.saveChanges();
                }

                return null;
            }
        }

        return input;
    }

    @Override
    public StackType extractItems(final StackType request, final Actionable mode, final BaseActionSource src) {
        if (request == null) {
            return null;
        }

        final long size = request.getStackSize();

        StackType results = null;

        final StackType l = this.getCellStacks().findPrecise(request);

        if (l != null) {
            results = l.copy();

            if (l.getStackSize() <= size) {
                results.setStackSize(l.getStackSize());

                if (mode == Actionable.MODULATE) {
                    this.updateItemCount(-l.getStackSize());
                    l.setStackSize(0);
                    this.saveChanges();
                }
            } else {
                results.setStackSize(size);

                if (mode == Actionable.MODULATE) {
                    l.setStackSize(l.getStackSize() - size);
                    this.updateItemCount(-size);
                    this.saveChanges();
                }
            }
        }

        return results;
    }

    private IItemList<StackType> getCellStacks() {
        if (this.cellStacks == null) {
            this.loadCellItems();
        }

        return this.cellStacks;
    }

    private void updateItemCount(final long delta) {
        this.storedCount += delta;
        this.tagCompound.setLong(getStackCountTag(), this.storedCount);
    }

    private void saveChanges() {
        // cellItems.clean();
        long itemCount = 0;

        // add new pretty stuff...
        int x = 0;

        for (final StackType v : this.cellStacks) {
            itemCount += v.getStackSize();

            final NBTBase c = this.tagCompound.getTag(slots[x]);

            if (c instanceof NBTTagCompound nbt) {
                v.writeToNBT(nbt);
            } else {
                final NBTTagCompound g = new NBTTagCompound();
                v.writeToNBT(g);
                this.tagCompound.setTag(slots[x], g);
            }

            /*
             * NBTBase tagSlotCount = tagCompound.getTag( itemSlotCount[x] ); if ( tagSlotCount instanceof NBTTagInt )
             * ((NBTTagInt) tagSlotCount).data = (int) v.getStackSize(); else
             */
            this.tagCompound.setLong(slotCount[x], v.getStackSize());

            x++;
        }

        // NBTBase tagType = tagCompound.getTag( ITEM_TYPE_TAG );
        // NBTBase tagCount = tagCompound.getTag( ITEM_COUNT_TAG );
        final short oldStoredItems = this.storedTypes;

        /*
         * if ( tagType instanceof NBTTagShort ) ((NBTTagShort) tagType).data = storedItems = (short) cellItems.size();
         * else
         */
        this.storedTypes = (short) this.cellStacks.size();

        if (this.cellStacks.isEmpty()) {
            this.tagCompound.removeTag(getStackTypeTag());
        } else {
            this.tagCompound.setShort(getStackTypeTag(), this.storedTypes);
        }

        /*
         * if ( tagCount instanceof NBTTagInt ) ((NBTTagInt) tagCount).data = storedItemCount = itemCount; else
         */
        this.storedCount = itemCount;

        if (itemCount == 0) {
            this.tagCompound.removeTag(getStackCountTag());
        } else {
            this.tagCompound.setLong(getStackCountTag(), itemCount);
        }

        // clean any old crusty stuff...
        for (; x < oldStoredItems && x < this.maxTypes; x++) {
            this.tagCompound.removeTag(slots[x]);
            this.tagCompound.removeTag(slotCount[x]);
        }

        if (this.container != null) {
            this.container.saveChanges(this);
        }
    }

    private void loadCellItems() {
        if (this.cellStacks == null) {
            this.cellStacks = this.getChannel().createPrimitiveList();
        }

        this.cellStacks.resetStatus(); // clears totals and stuff.

        final int types = (int) this.getStoredItemTypes();

        for (int x = 0; x < types; x++) {
            final StackType ias = readStack(this.tagCompound.getCompoundTag(slots[x]));
            if (ias != null) {
                ias.setStackSize(this.tagCompound.getLong(slotCount[x]));
                if (ias.getStackSize() > 0) {
                    this.cellStacks.add(ias);
                } else {
                    // Dirty Compact for EC2
                    ias.setStackSize(this.tagCompound.getCompoundTag(slots[x]).getLong("Cnt"));
                    if (ias.getStackSize() > 0) {
                        this.cellStacks.add(ias);
                    }
                }
            }
        }

        if (this.cellStacks.size() != types) {
            // fix broken singularity cells
            this.saveChanges();
        }
    }

    protected abstract StackType readStack(NBTTagCompound tag);

    @Override
    public IItemList<StackType> getAvailableItems(final IItemList<StackType> out, int iteration) {
        for (final StackType i : this.getCellStacks()) {
            out.add(i);
        }

        return out;
    }

    @Override
    public StackType getAvailableItem(@Nonnull StackType request, int iteration) {
        long count = 0;
        for (final StackType is : this.getCellStacks()) {
            if (is != null && is.getStackSize() > 0 && is.isSameType(request)) {
                count += is.getStackSize();
                if (count < 0) {
                    // overflow
                    count = Long.MAX_VALUE;
                    break;
                }
            }
        }
        return count == 0 ? null : request.copy().setStackSize(count);
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain(this.cellItem);
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.cellItem);
    }

    @Override
    public String getOreFilter() {
        return this.cellType.getOreFilter(this.cellItem);
    }

    @Override
    @Deprecated
    public IInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.cellItem);
    }

    @Override
    public IAEStackInventory getConfigAEInventory() {
        return this.cellType.getConfigAEInventory(this.cellItem);
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.cellItem);
    }

    @Override
    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();

        return (bytesFree > this.getBytesPerType()
                || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0))
                && (restrictionLong <= 0 || restrictionLong > getStoredItemCount())
                && this.getRemainingItemTypes() > 0;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getBytesLong(this.cellItem);
    }

    @Override
    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    @Override
    public long getUsedBytes() {
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / this.typeWeight;

        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    @Override
    public long getTotalItemTypes() {
        if (restrictionTypes > 0) return restrictionTypes;
        return this.maxTypes;
    }

    public long getMaxTypes() {
        return this.maxTypes;
    }

    @Override
    public long getStoredItemCount() {
        return this.storedCount;
    }

    @Override
    public long getStoredItemTypes() {
        return this.storedTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();

        return Math.min(basedOnStorage, baseOnTotal);
    }

    @Override
    public long getRemainingItemsCountDist(StackType l) {
        long remaining;

        if (l != null) {
            if (restrictionLong > 0) {
                remaining = Math
                        .min((restrictionLong / this.distTypesCount) - l.getStackSize(), getRemainingItemCount());
            } else {
                remaining = (((getTotalBytes() / this.distTypesCount) - getBytesPerType()) * this.typeWeight)
                        - l.getStackSize();
            }
        } else {
            if (restrictionLong > 0) {
                remaining = Math.min(
                        restrictionLong / this.distTypesCount,
                        ((this.getTotalBytes() / this.distTypesCount) - this.getBytesPerType())
                                * (long) this.typeWeight);
            } else {
                remaining = ((this.getTotalBytes() / this.distTypesCount) - this.getBytesPerType())
                        * (long) this.typeWeight;
            }
        }
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public long getRemainingItemCount() {
        if (restrictionLong > 0) {
            return Math.min(
                    restrictionLong - this.getStoredItemCount(),
                    this.getFreeBytes() * this.typeWeight + this.getUnusedItemCount());
        }
        final long remaining = this.getFreeBytes() * this.typeWeight + this.getUnusedItemCount();

        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedItemCount() {
        final long div = this.getStoredItemCount() % this.typeWeight;

        if (div == 0) {
            return 0;
        }

        return (int) (this.typeWeight - div);
    }

    @Override
    public int getStatusForCell() {
        if (this.getStoredItemCount() == 0) {
            return 1;
        }
        if (this.canHoldNewItem()) {
            return 2;
        }
        if (this.getRemainingItemCount() > 0) {
            return 3;
        }
        return 4;
    }

    public List<Object> getRestriction() {
        return Arrays.asList(restrictionLong, restrictionTypes);
    }

    protected void setTypeWeight(int typeWeight) {
        this.typeWeight = typeWeight;
    }

    @Override
    public StorageChannel getChannel() {
        return this.cellType.getStorageChannel();
    }

    protected abstract String getStackTypeTag();

    protected abstract String getStackCountTag();
}
