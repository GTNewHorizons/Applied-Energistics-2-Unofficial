/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import static appeng.util.Platform.isServer;

import java.util.HashMap;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.prioitylist.PrecisePriorityList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerStorageBus extends ContainerUpgradeable implements IVirtualSlotHolder {

    private final IStorageBus storageBus;

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    private static final HashMap<EntityPlayer, IteratorState> PartitionIteratorMap = new HashMap<>();

    @GuiSync(8)
    public ActionItems partitionMode; // use for icon and tooltip

    private final IAEStack<?>[] configClientSlot = new IAEStack[63];

    public ContainerStorageBus(final InventoryPlayer ip, final IStorageBus te) {
        super(ip, te);
        this.storageBus = te;
        partitionMode = PartitionIteratorMap.containsKey(ip.player) ? ActionItems.NEXT_PARTITION : ActionItems.WRENCH;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        final IInventory upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        0,
                        187,
                        8,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        1,
                        187,
                        8 + 18,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        2,
                        187,
                        8 + 18 * 2,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        3,
                        187,
                        8 + 18 * 3,
                        this.getInventoryPlayer())).setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        upgrades,
                        4,
                        187,
                        8 + 18 * 4,
                        this.getInventoryPlayer())).setNotDraggable());
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setReadWriteMode(
                    (AccessRestriction) this.getUpgradeable().getConfigManager().getSetting(Settings.ACCESS));
            this.setStorageFilter(
                    (StorageFilter) this.getUpgradeable().getConfigManager().getSetting(Settings.STORAGE_FILTER));
            this.setStickyMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.STICKY_MODE));

            final IAEStackInventory config = this.storageBus.getAEInventoryByName(StorageName.NONE);
            this.updateVirtualSlots(StorageName.NONE, config, this.configClientSlot);
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if (this.getUpgradeable().getInstalledUpgrades(Upgrades.ORE_FILTER) > 0) return false;

        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > (idx - 2);
    }

    public void clear() {
        final IAEStackInventory inv = this.storageBus.getAEInventoryByName(StorageName.NONE);
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            inv.putAEStackInSlot(x, null);
        }
        this.detectAndSendChanges();
    }

    private void clearPartitionIterator(EntityPlayer player) {
        PartitionIteratorMap.remove(player);
        partitionMode = ActionItems.WRENCH;
    }

    public void partition(boolean clearIterator) {
        EntityPlayer player = this.getInventoryPlayer().player;
        if (clearIterator) {
            clearPartitionIterator(player);
            return;
        }
        final IAEStackInventory inv = this.storageBus.getAEInventoryByName(StorageName.NONE);

        final MEInventoryHandler cellInv = this.storageBus.getInternalHandler();

        if (cellInv == null) {
            clearPartitionIterator(player);
            return;
        }
        IteratorState it;
        if (!PartitionIteratorMap.containsKey(player)) {
            // clear filter for fetching items
            cellInv.setPartitionList(new PrecisePriorityList<>(this.storageBus.getItemList()));
            final IItemList list = cellInv
                    .getAvailableItems(this.storageBus.getItemList(), IterationCounter.fetchNewId());
            it = new IteratorState(list.iterator());
            PartitionIteratorMap.put(player, it);
            partitionMode = ActionItems.NEXT_PARTITION;
        } else {
            it = PartitionIteratorMap.get(player);
        }
        boolean skip = false;
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (skip) {
                inv.putAEStackInSlot(x, null);
                continue;
            }
            if (this.isSlotEnabled(x / 9)) {
                IAEStack<?> AEis = it.next();
                if (AEis != null) {
                    final IAEStack<?> tempAes = AEis.copy();
                    tempAes.setStackSize(1);
                    inv.putAEStackInSlot(x, tempAes);
                } else {
                    clearPartitionIterator(player);
                    skip = true;
                    inv.putAEStackInSlot(x, null);
                }
            } else {
                skip = true;
                inv.putAEStackInSlot(x, null);
            }

        }
        if (!it.hasNext) clearPartitionIterator(player);
        this.detectAndSendChanges();
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    public ActionItems getPartitionMode() {
        return this.partitionMode;
    }

    public void setPartitionMode(final ActionItems action) {
        partitionMode = action;
    }

    private void setStorageFilter(final StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }

    public YesNo getStickyMode() {
        return this.stickyMode;
    }

    private void setStickyMode(final YesNo stickyMode) {
        this.stickyMode = stickyMode;
    }

    private void setReadWriteMode(final AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

    private static class IteratorState {

        private final Iterator<IAEStack<?>> it;
        private boolean hasNext; // cache hasNext(), call next of internal iterator

        public IteratorState(Iterator<IAEStack<?>> it) {
            this.it = it;
            this.hasNext = it.hasNext();
        }

        public IAEStack<?> next() {
            if (this.hasNext) {
                IAEStack<?> is = it.next();
                hasNext = it.hasNext();
                return is;
            }
            return null;
        }
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.storageBus.getAEInventoryByName(StorageName.NONE);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) {
            this.updateVirtualSlots(StorageName.NONE, config, this.configClientSlot);
        }
    }

    public StorageChannel getStorageChannel() {
        return this.storageBus.getStorageChannel();
    }

    public IAEStackInventory getConfig() {
        return this.storageBus.getAEInventoryByName(StorageName.NONE);
    }
}
