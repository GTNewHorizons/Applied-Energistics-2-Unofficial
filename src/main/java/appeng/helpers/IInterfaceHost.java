/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import java.util.EnumSet;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.util.IIfaceTermViewable;

public interface IInterfaceHost extends ICraftingProvider, IUpgradeableHost, ICraftingRequester, IIfaceTermViewable {

    DualityInterface getInterfaceDuality();

    EnumSet<ForgeDirection> getTargets();

    TileEntity getTileEntity();

    void saveChanges();

    @Override
    default IInventory getPatterns() {
        return getInterfaceDuality().getPatterns();
    }

    @Override
    default boolean shouldDisplay() {
        return getInterfaceDuality().getConfigManager().getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES;
    }

    @Override
    default String getName() {
        return getInterfaceDuality().getTermName();
    }

    @Override
    default int rows() {
        return getInterfaceDuality().getInstalledUpgrades(Upgrades.PATTERN_CAPACITY) + 1;
    }

    @Override
    default int rowSize() {
        return DualityInterface.NUMBER_OF_PATTERN_SLOTS;
    }

    @Override
    default ItemStack getDisplayRep() {
        return getInterfaceDuality().getCrafterIcon();
    }
}
