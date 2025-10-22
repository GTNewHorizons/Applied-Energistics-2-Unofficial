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

import java.util.HashMap;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.OptionalSlotFakeTypeOnly;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IStorageBus;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.prioitylist.PrecisePriorityList;

public class ContainerStorageBus extends ContainerUpgradeable {

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
        final int xo = 8;
        final int yo = 23 + 6;

        final IInventory config = this.getUpgradeable().getInventoryByName("config");
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(new OptionalSlotFakeTypeOnly(config, this, y * 9 + x, xo, yo, x, y, y));
            }
        }

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

    private int rowToUpdate = 0;

    /**
     * @param row      the specific row to send, -1 indicates sending all.
     * @param upgrades number of installed capacity cards
     */
    private void sendRow(int row, int upgrades) {
        IInventory inv = this.getUpgradeable().getInventoryByName("config");
        // start at first filter slot or at specific row
        int from = row <= -1 ? 18 : 9 + (9 * row);
        // end at last filter slot or at end of specific row
        int to = row <= -1 ? inv.getSizeInventory() : 18 + (9 * row);

        int offset = getToolboxSizeInventory();
        for (; from < to; from++) {
            if (upgrades <= (from / 9 - 2)) break;

            ItemStack stack = inv.getStackInSlot(from);
            if (stack == null) continue;

            for (ICrafting crafter : this.crafters) {
                if (crafter instanceof EntityPlayerMP playerMP) {
                    // necessary to ensure that the package is sent correctly
                    playerMP.isChangingQuantityOnly = false;
                }
                crafter.sendSlotContents(this, from + offset, stack);
            }
        }
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
        }
        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        if (upgrades > 0) { // sync filter slots
            boolean needSync = this.storageBus.needSyncGUI();
            int row;
            if (needSync) {
                row = -1; // send all rows
                rowToUpdate = upgrades; // force update of last row at next call
                this.storageBus.setNeedSyncGUI(false);
            } else row = rowToUpdate++;

            if (row >= upgrades) rowToUpdate = 0;
            sendRow(row, upgrades);
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
        final IInventory inv = this.getUpgradeable().getInventoryByName("config");
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            inv.setInventorySlotContents(x, null);
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
        final IInventory inv = this.getUpgradeable().getInventoryByName("config");

        final MEInventoryHandler<IAEItemStack> cellInv = (MEInventoryHandler<IAEItemStack>) this.storageBus
                .getInternalHandler();

        if (cellInv == null) {
            clearPartitionIterator(player);
            return;
        }
        IteratorState it;
        if (!PartitionIteratorMap.containsKey(player)) {
            // clear filter for fetching items
            cellInv.setPartitionList(new PrecisePriorityList<>(AEApi.instance().storage().createItemList()));
            final IItemList<IAEItemStack> list = cellInv.getAvailableItems(
                    AEApi.instance().storage().createItemFilterList(),
                    IterationCounter.fetchNewId());
            it = new IteratorState(list.iterator());
            PartitionIteratorMap.put(player, it);
            partitionMode = ActionItems.NEXT_PARTITION;
        } else {
            it = PartitionIteratorMap.get(player);
        }
        boolean skip = false;
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (skip) {
                inv.setInventorySlotContents(x, null);
                continue;
            }
            if (this.isSlotEnabled(x / 9)) {
                IAEItemStack AEis = it.next();
                if (AEis != null) {
                    final ItemStack is = AEis.getItemStack();
                    is.stackSize = 1;
                    inv.setInventorySlotContents(x, is);
                } else {
                    clearPartitionIterator(player);
                    skip = true;
                    inv.setInventorySlotContents(x, null);
                }
            } else {
                skip = true;
                inv.setInventorySlotContents(x, null);
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

        private final Iterator<IAEItemStack> it;
        private boolean hasNext; // cache hasNext(), call next of internal iterator

        public IteratorState(Iterator<IAEItemStack> it) {
            this.it = it;
            this.hasNext = it.hasNext();
        }

        public IAEItemStack next() {
            if (this.hasNext) {
                IAEItemStack is = it.next();
                hasNext = it.hasNext();
                return is;
            }
            return null;
        }
    }
}
