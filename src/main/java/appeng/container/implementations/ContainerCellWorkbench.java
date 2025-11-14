/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import static appeng.util.Platform.isServer;

import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.CopyMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.implementations.tiles.ICellWorkbench;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.ICellRestriction;
import appeng.me.storage.CellInventory;
import appeng.tile.inventory.AppEngNullInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerCellWorkbench extends ContainerUpgradeable implements IVirtualSlotHolder {

    private final ICellWorkbench workBench;
    private final AppEngNullInventory nullInventory = new AppEngNullInventory();

    @GuiSync(2)
    public CopyMode copyMode = CopyMode.CLEAR_ON_REMOVE;

    private ItemStack prevStack = null;
    private int lastUpgrades = 0;

    private final IAEStack<?>[] configClientSlot = new IAEStack[63];

    public ContainerCellWorkbench(final InventoryPlayer ip, final ICellWorkbench te) {
        super(ip, te);
        this.workBench = te;
    }

    public void setFuzzy(final FuzzyMode valueOf) {
        final ICellWorkbenchItem cwi = this.workBench.getCell();
        if (cwi != null) {
            cwi.setFuzzyMode(this.workBench.getInventoryByName("cell").getStackInSlot(0), valueOf);
        }
    }

    public void nextWorkBenchCopyMode() {
        this.workBench.getConfigManager()
                .putSetting(Settings.COPY_MODE, Platform.nextEnum(this.getWorkBenchCopyMode()));
    }

    private CopyMode getWorkBenchCopyMode() {
        return (CopyMode) this.workBench.getConfigManager().getSetting(Settings.COPY_MODE);
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        final IInventory cell = this.getUpgradeable().getInventoryByName("cell");
        this.addSlotToContainer(
                new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.WORKBENCH_CELL,
                        cell,
                        0,
                        152,
                        8,
                        this.getInventoryPlayer()));

        final IInventory upgradeInventory = new Upgrades();

        for (int zz = 0; zz < 3; zz++) {
            for (int z = 0; z < 8; z++) {
                final int iSLot = zz * 8 + z;
                this.addSlotToContainer(
                        new OptionalSlotRestrictedInput(
                                SlotRestrictedInput.PlacableItemType.UPGRADES,
                                upgradeInventory,
                                this,
                                iSLot,
                                187 + zz * 18,
                                8 + 18 * z,
                                iSLot,
                                this.getInventoryPlayer()));
            }
        }
    }

    @Override
    public int availableUpgrades() {
        final ItemStack is = this.workBench.getInventoryByName("cell").getStackInSlot(0);
        if (this.prevStack != is) {
            this.prevStack = is;
            this.lastUpgrades = this.getCellUpgradeInventory().getSizeInventory();
        }
        return this.lastUpgrades;
    }

    @Override
    public void detectAndSendChanges() {
        final ItemStack is = this.workBench.getInventoryByName("cell").getStackInSlot(0);
        if (Platform.isServer()) {
            if (!this.workBench.isSame(this.workBench)) {
                this.setValidContainer(false);
            }

            for (final Object crafter : this.crafters) {
                final ICrafting icrafting = (ICrafting) crafter;

                if (this.prevStack != is) {
                    // if the bars changed an item was probably made, so just send shit!
                    for (final Object s : this.inventorySlots) {
                        if (s instanceof OptionalSlotRestrictedInput sri) {
                            icrafting.sendSlotContents(this, sri.slotNumber, sri.getStack());
                        }
                    }
                    ((EntityPlayerMP) icrafting).isChangingQuantityOnly = false;
                }
            }

            this.setCopyMode(this.getWorkBenchCopyMode());
            this.setFuzzyMode(this.getWorkBenchFuzzyMode());

            this.updateVirtualSlots(
                    StorageName.NONE,
                    this.workBench.getAEInventoryByName(StorageName.NONE),
                    this.configClientSlot);
        }

        this.prevStack = is;
        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        return idx < this.availableUpgrades();
    }

    public IInventory getCellUpgradeInventory() {
        final IInventory upgradeInventory = this.workBench.getCellUpgradeInventory();

        return upgradeInventory == null ? this.nullInventory : upgradeInventory;
    }

    public boolean haveCell() {
        return this.workBench.getCell() != null;
    }

    public boolean haveCellRestrictAble() {
        return haveCell() && this.workBench.getCell() instanceof ICellRestriction;
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("copyMode")) {
            this.workBench.getConfigManager().putSetting(Settings.COPY_MODE, this.getCopyMode());
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public void clear() {
        final IAEStackInventory inv = this.workBench.getAEInventoryByName(StorageName.NONE);
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            inv.putAEStackInSlot(x, null);
        }
        this.detectAndSendChanges();
    }

    private FuzzyMode getWorkBenchFuzzyMode() {
        final ICellWorkbenchItem cwi = this.workBench.getCell();
        if (cwi != null) {
            return cwi.getFuzzyMode(this.workBench.getInventoryByName("cell").getStackInSlot(0));
        }
        return FuzzyMode.IGNORE_ALL;
    }

    public void partition() {
        final IAEStackInventory inv = this.workBench.getAEInventoryByName(StorageName.NONE);
        final ItemStack is = this.getUpgradeable().getInventoryByName("cell").getStackInSlot(0);

        if (!(is != null && is.getItem() instanceof ICellWorkbenchItem wi)) return;

        final IMEInventory<?> cellInv = AEApi.instance().registries().cell()
                .getCellInventory(is, null, wi.getStorageChannel());

        if (!(cellInv instanceof CellInventory ci)) return;

        final IItemList<?> list = ci
                .getAvailableItems(ci.getChannel().createPrimitiveList(), IterationCounter.fetchNewId());
        Iterator<?> i = list.iterator();

        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (i.hasNext()) {
                final IAEStack<?> g = (IAEStack<?>) i.next();
                g.setStackSize(1);
                inv.putAEStackInSlot(x, g);
            } else {
                inv.putAEStackInSlot(x, null);
            }
        }

        this.detectAndSendChanges();
    }

    public CopyMode getCopyMode() {
        return this.copyMode;
    }

    private void setCopyMode(final CopyMode copyMode) {
        this.copyMode = copyMode;
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.workBench.getAEInventoryByName(StorageName.NONE);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) {
            this.updateVirtualSlots(StorageName.NONE, config, this.configClientSlot);
        }
    }

    public StorageChannel getStorageChannel() {
        return this.workBench.getStorageChannel();
    }

    public IAEStackInventory getConfig() {
        return this.workBench.getAEInventoryByName(StorageName.NONE);
    }

    public class Upgrades implements IInventory {

        @Override
        public int getSizeInventory() {
            return ContainerCellWorkbench.this.getCellUpgradeInventory().getSizeInventory();
        }

        @Override
        public ItemStack getStackInSlot(final int i) {
            return ContainerCellWorkbench.this.getCellUpgradeInventory().getStackInSlot(i);
        }

        @Override
        public ItemStack decrStackSize(final int i, final int j) {
            final IInventory inv = ContainerCellWorkbench.this.getCellUpgradeInventory();
            final ItemStack is = inv.decrStackSize(i, j);
            inv.markDirty();
            return is;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(final int i) {
            final IInventory inv = ContainerCellWorkbench.this.getCellUpgradeInventory();
            final ItemStack is = inv.getStackInSlotOnClosing(i);
            inv.markDirty();
            return is;
        }

        @Override
        public void setInventorySlotContents(final int i, final ItemStack itemstack) {
            final IInventory inv = ContainerCellWorkbench.this.getCellUpgradeInventory();
            inv.setInventorySlotContents(i, itemstack);
            inv.markDirty();
        }

        @Override
        public String getInventoryName() {
            return "Upgrades";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUseableByPlayer(final EntityPlayer entityplayer) {
            return false;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
            return ContainerCellWorkbench.this.getCellUpgradeInventory().isItemValidForSlot(i, itemstack);
        }
    }
}
