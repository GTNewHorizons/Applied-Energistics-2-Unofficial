/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.storage;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.features.AEFeature;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellUpgrades;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.IPartitionList;
import appeng.util.prioitylist.MergedPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;

public class ItemViewCell extends AEBaseItem implements ICellWorkbenchItem {

    // TODO idk how deal with this
    public ItemViewCell() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    public static IPartitionList<IAEItemStack> createFilter(final ItemStack[] list) {
        IPartitionList<IAEItemStack> myPartitionList = null;

        final MergedPriorityList<IAEItemStack> myMergedList = new MergedPriorityList<>();

        for (final ItemStack currentViewCell : list) {
            if (currentViewCell == null) {
                continue;
            }

            if ((currentViewCell.getItem() instanceof ItemViewCell vc)) {
                if (!vc.getViewMode(currentViewCell)) continue;
                final IInventory upgrades = vc.getUpgradesInventory(currentViewCell);
                final IAEStackInventory config = vc.getConfigAEInventory(currentViewCell);
                final FuzzyMode fzMode = vc.getFuzzyMode(currentViewCell);
                final String filter = vc.getOreFilter(currentViewCell);

                boolean hasInverter = false;
                boolean hasFuzzy = false;
                boolean hasOreFilter = false;
                for (int x = 0; x < upgrades.getSizeInventory(); x++) {
                    final ItemStack is = upgrades.getStackInSlot(x);
                    if (is != null && is.getItem() instanceof IUpgradeModule) {
                        final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                        if (u != null) {
                            switch (u) {
                                case FUZZY -> hasFuzzy = true;
                                case INVERTER -> hasInverter = true;
                                case ORE_FILTER -> hasOreFilter = true;
                                default -> {}
                            }
                        }
                    }
                }

                if (hasOreFilter && !filter.isEmpty()) {
                    myMergedList.addNewList(new OreFilteredList(filter), !hasInverter);
                } else {
                    final IItemList priorityList = AEApi.instance().storage().createAEStackList();

                    for (int x = 0; x < config.getSizeInventory(); x++) {
                        final IAEStack<?> aes = config.getAEStackInSlot(x);
                        if (aes != null) {
                            priorityList.add(aes);
                        }
                    }

                    if (!priorityList.isEmpty()) {
                        if (hasFuzzy) {
                            myMergedList.addNewList(new FuzzyPriorityList(priorityList, fzMode), !hasInverter);
                        } else {
                            myMergedList.addNewList(new PrecisePriorityList(priorityList), !hasInverter);
                        }
                    }
                }
                myPartitionList = myMergedList;
            }
        }

        return myPartitionList;
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack is, final World w, final EntityPlayer p) {
        if (Platform.isServer()) {
            toggleViewMode(is);
        }
        return is;
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IInventory getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IInventory getConfigInventory(final ItemStack is) {
        return new CellConfigLegacy(new CellConfig(is), StorageChannel.ITEMS);
    }

    @Override
    public IAEStackInventory getConfigAEInventory(ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        return FuzzyMode.fromItemStack(is);
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    @Override
    public String getOreFilter(ItemStack is) {
        return Platform.openNbtData(is).getString("OreFilter");
    }

    @Override
    public void setOreFilter(ItemStack is, String filter) {
        Platform.openNbtData(is).setString("OreFilter", filter);
    }

    public void toggleViewMode(final ItemStack is) {
        Platform.openNbtData(is).setBoolean("ViewMode", !getViewMode(is));
    }

    public boolean getViewMode(final ItemStack is) {
        NBTTagCompound nbt = Platform.openNbtData(is);
        // If key is not set, then view mode is "enabled," which is opposite of default missing NBT-key
        if (!nbt.hasKey("ViewMode")) return true;
        return nbt.getBoolean("ViewMode");
    }

    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        boolean viewMode = getViewMode(stack);
        if (viewMode) {
            lines.add(EnumChatFormatting.GREEN + ButtonToolTips.Enable.getLocal());
        } else {
            lines.add(EnumChatFormatting.RED + ButtonToolTips.Disabled.getLocal());
        }
        lines.add(GuiText.ViewCellToggleKey.getLocal());

        String filter = getOreFilter(stack);
        if (!filter.isEmpty()) lines.add(GuiText.PartitionedOre.getLocal() + " : " + filter);
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }
}
