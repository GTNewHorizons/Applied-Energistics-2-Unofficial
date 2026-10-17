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

import org.jetbrains.annotations.Nullable;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.ICellWorkbench;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.helpers.CellWorkbenchState;
import appeng.helpers.ICellRestriction.CellData;
import appeng.helpers.ICellRestriction.CellRestrictionData;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.tile.AEBaseTile;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

public class TileCellWorkbench extends AEBaseTile implements ICellWorkbench, IPrimaryGuiIconProvider {

    protected final CellWorkbenchState state = new CellWorkbenchState(this, this, this);

    @Override
    public IInventory getCellUpgradeInventory() {
        return this.state.getCellUpgradeInventory();
    }

    @Override
    public ICellWorkbenchItem getCell() {
        return this.state.getCell();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileCellWorkbench(NBTTagCompound data) {
        this.state.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileCellWorkbench(NBTTagCompound data) {
        this.state.readFromNBT(data);
    }

    @Override
    public IInventory getInventoryByName(String name) {
        return "cell".equals(name) ? this.state.getCellInventory() : null;
    }

    @Override
    public int getInstalledUpgrades(Upgrades upgrade) {
        return this.state.getInstalledUpgrades(upgrade);
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation, ItemStack removedStack,
            ItemStack newStack) {
        this.state.onChangeInventory(inventory, operation);
        if (operation == InvOperation.markDirty) {
            this.saveChanges();
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.state.getConfigManager();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        if (Platform.isServer()) {
            this.saveChanges();
        }
    }

    @Override
    public String getFilter() {
        return this.state.getFilter();
    }

    @Override
    public void setFilter(String filter) {
        this.state.setFilter(filter);
        this.saveChanges();
    }

    @Override
    @Nullable
    public CellData getCellData(ItemStack stack) {
        return this.state.getCellData();
    }

    @Override
    @Nullable
    public CellRestrictionData getCellRestrictionData(ItemStack stack) {
        return this.state.getCellRestrictionData();
    }

    @Override
    public void setCellRestriction(ItemStack stack, CellRestrictionData restriction) {
        this.state.setCellRestriction(restriction);
        this.saveChanges();
    }

    @Override
    public boolean isSame(ICellWorkbench cellWorkbench) {
        return this == this.getWorldObj().getTileEntity(this.xCoord, this.yCoord, this.zCoord);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().blocks().cellWorkbench().maybeStack(1).orNull();
    }

    @Override
    public void saveAEStackInv() {
        this.state.saveAEStackInv();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return this.state.getAEInventoryByName(name);
    }

    @Override
    @Nullable
    public IAEStackType<?> getStackType() {
        return this.state.getStackType();
    }

    @Override
    public void getDrops(World world, int x, int y, int z, List<ItemStack> drops) {
        super.getDrops(world, x, y, z, drops);
        ItemStack stack = this.state.getCellInventory().getStackInSlot(0);
        if (stack != null) {
            drops.add(stack);
        }
    }
}
