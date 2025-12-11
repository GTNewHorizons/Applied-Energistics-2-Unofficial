/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.tile.misc;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.ICellWorkbench;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.util.IConfigManager;
import appeng.helpers.ICellRestriction;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.tile.AEBaseTile;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;

public class TileCellWorkbench extends AEBaseTile implements ICellWorkbench, IPrimaryGuiIconProvider {

    private final AppEngInternalInventory cell = new AppEngInternalInventory(this, 1);
    private final IAEStackInventory config = new IAEStackInventory(this, 63);
    private final ConfigManager manager = new ConfigManager(this);

    private IInventory cacheUpgrades = null;
    private IAEStackInventory cacheConfig = null;
    private boolean locked = false;
    private long cellRestrictAmount;
    private byte cellRestrictTypes;
    private StorageChannel storageChannel;

    public TileCellWorkbench() {
        this.manager.registerSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE);
        this.cell.setEnableClientEvents(true);
    }

    public IInventory getCellUpgradeInventory() {
        if (this.cacheUpgrades == null) {
            final ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            final ItemStack is = this.cell.getStackInSlot(0);
            if (is == null) {
                return null;
            }

            final IInventory inv = cell.getUpgradesInventory(is);
            if (inv == null) {
                return null;
            }

            return this.cacheUpgrades = inv;
        }
        return this.cacheUpgrades;
    }

    public ICellWorkbenchItem getCell() {
        if (this.cell.getStackInSlot(0) == null) {
            return null;
        }

        if (this.cell.getStackInSlot(0).getItem() instanceof ICellWorkbenchItem) {
            return ((ICellWorkbenchItem) this.cell.getStackInSlot(0).getItem());
        }

        return null;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileCellWorkbench(final NBTTagCompound data) {
        this.cell.writeToNBT(data, "cell");
        this.config.writeToNBT(data, "config");
        this.manager.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileCellWorkbench(final NBTTagCompound data) {
        this.cell.readFromNBT(data, "cell");
        this.config.readFromNBT(data, "config");
        this.manager.readFromNBT(data);
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("cell")) {
            return this.cell;
        }

        return null;
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        final IInventory inv = getCellUpgradeInventory();
        if (inv != null) {
            for (int x = 0; x < inv.getSizeInventory(); x++) {
                final ItemStack is = inv.getStackInSlot(x);
                if (is != null && is.getItem() instanceof IUpgradeModule) {
                    if (((IUpgradeModule) is.getItem()).getType(is) == u) return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.cell && !this.locked) {
            this.locked = true;

            this.cacheUpgrades = null;
            this.cacheConfig = null;

            ItemStack is = this.cell.getStackInSlot(0);
            if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.KEEP_ON_REMOVE) {
                if (is != null && is.getItem() instanceof ICellRestriction icr)
                    icr.setCellRestriction(is, cellRestrictTypes + "," + cellRestrictAmount);
            }

            if (is != null && is.getItem() instanceof ICellWorkbenchItem wi) {
                if (wi.getStorageChannel() != this.storageChannel) {
                    this.storageChannel = wi.getStorageChannel();

                    for (int x = 0; x < this.config.getSizeInventory(); x++) {
                        this.config.putAEStackInSlot(x, null);
                    }
                }
            }

            final IAEStackInventory configInventory = this.getCellConfigInventory();
            if (configInventory != null) {
                boolean cellHasConfig = false;
                for (int x = 0; x < configInventory.getSizeInventory(); x++) {
                    if (configInventory.getAEStackInSlot(x) != null) {
                        cellHasConfig = true;
                        break;
                    }
                }

                if (cellHasConfig) {
                    for (int x = 0; x < this.config.getSizeInventory(); x++) {
                        this.config.putAEStackInSlot(x, configInventory.getAEStackInSlot(x));
                    }
                } else {
                    for (int x = 0; x < this.config.getSizeInventory(); x++) {
                        configInventory.putAEStackInSlot(x, this.config.getAEStackInSlot(x));
                    }

                    configInventory.markDirty();
                }
            } else if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.CLEAR_ON_REMOVE) {
                for (int x = 0; x < this.config.getSizeInventory(); x++) {
                    this.config.putAEStackInSlot(x, null);
                }

                this.markDirty();
            }

            this.locked = false;
        } else if (inv == cacheUpgrades) {
            if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) setFilter("");
        }
    }

    private IAEStackInventory getCellConfigInventory() {
        if (this.cacheConfig == null) {
            final ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            final ItemStack is = this.cell.getStackInSlot(0);
            if (is == null) {
                return null;
            }

            final IAEStackInventory inv = cell.getConfigAEInventory(is);
            if (inv == null) {
                return null;
            }

            this.cacheConfig = inv;
        }
        return this.cacheConfig;
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        super.getDrops(w, x, y, z, drops);

        if (this.cell.getStackInSlot(0) != null) {
            drops.add(this.cell.getStackInSlot(0));
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        // nothing here..
    }

    @Override
    public String getFilter() {
        ItemStack is = this.cell.getStackInSlot(0);
        if (is != null && is.getItem() instanceof ICellWorkbenchItem)
            return ((ICellWorkbenchItem) is.getItem()).getOreFilter(is);
        else return "";
    }

    @Override
    public void setFilter(String filter) {
        ItemStack is = this.cell.getStackInSlot(0);
        if (is != null && is.getItem() instanceof ICellWorkbenchItem)
            ((ICellWorkbenchItem) is.getItem()).setOreFilter(is, filter);
    }

    @Override
    public String getCellData(ItemStack n) {
        ItemStack is = this.cell.getStackInSlot(0);
        if (is != null && is.getItem() instanceof ICellRestriction icr) return icr.getCellData(is);
        return null;
    }

    @Override
    public void setCellRestriction(ItemStack n, String newData) {
        if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.KEEP_ON_REMOVE) {
            String[] s = newData.split(",", 2);
            cellRestrictTypes = Byte.parseByte(s[0]);
            cellRestrictAmount = Long.parseLong(s[1]);
        }

        ItemStack is = this.cell.getStackInSlot(0);
        if (is != null && is.getItem() instanceof ICellRestriction icr) icr.setCellRestriction(is, newData);
    }

    @Override
    public boolean isSame(ICellWorkbench cellWorkbench) {
        return this == getWorldObj().getTileEntity(xCoord, yCoord, zCoord);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().blocks().cellWorkbench().maybeStack(1).orNull();
    }

    @Override
    public void saveAEStackInv() {
        if (!this.locked) {
            final IAEStackInventory c = this.getCellConfigInventory();
            if (c != null) {
                for (int x = 0; x < this.config.getSizeInventory(); x++) {
                    c.putAEStackInSlot(x, this.config.getAEStackInSlot(x));
                }

                c.markDirty();
            }
        }
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return this.config;
    }

    public StorageChannel getStorageChannel() {
        return this.storageChannel;
    }
}
