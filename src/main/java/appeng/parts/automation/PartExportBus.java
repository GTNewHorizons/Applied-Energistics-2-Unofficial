/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import java.util.ArrayList;
import java.util.Collection;

import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;
import appeng.util.InventoryAdaptor;
import appeng.util.IterationCounter;
import appeng.util.prioitylist.OreFilteredList;

public class PartExportBus extends PartBaseExportBus<IAEItemStack> implements ICraftingRequester {

    @Reflected
    public PartExportBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public int calculateAmountToSend() {
        int toSend = switch (this.getInstalledUpgrades(Upgrades.SPEED)) {
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
            default -> 1;
        };

        switch (this.getInstalledUpgrades(Upgrades.SUPERSPEED)) {
            case 1 -> toSend += 16;
            case 2 -> toSend += 16 * 8;
            case 3 -> toSend += 16 * 8 * 8;
            case 4 -> toSend += 16 * 8 * 8 * 8;
        }

        switch (this.getInstalledUpgrades(Upgrades.SUPERLUMINALSPEED)) {
            case 1 -> toSend += 131_072;
            case 2 -> toSend += 131_072 * 8;
            case 3 -> toSend += 131_072 * 8 * 8;
            case 4 -> toSend += 131_072 * 8 * 8 * 8;
        }

        return toSend;
    }

    @Override
    protected boolean supportFuzzy() {
        return true;
    }

    @Override
    protected boolean supportOreDict() {
        return true;
    }

    @Override
    protected IMEMonitor<IAEItemStack> getMonitor() {
        try {
            return this.getProxy().getStorage().getItemInventory();
        } catch (final GridAccessException e) {
            return null;
        }
    }

    @Override
    protected void doFuzzy(IAEItemStack aes, FuzzyMode fzMode, InventoryAdaptor destination, IEnergyGrid energy,
            IMEMonitor<IAEItemStack> gridInv) {
        /*
         * This actually returns a NetworkInventoryHandler object. The method .getSortedFuzzyItems() used is the
         * overriden one found in the .java file.
         */
        if (gridInv instanceof NetworkMonitor<?>) {
            final Collection<IAEItemStack> fzlist = ((NetworkMonitor<IAEItemStack>) gridInv).getHandler()
                    .getSortedFuzzyItems(new ArrayList<>(), aes, fzMode, IterationCounter.fetchNewId());

            for (final IAEItemStack o : fzlist) {
                this.pushItemIntoTarget(destination, energy, gridInv, o);
            }
        }
    }

    @Override
    protected void doOreDict(InventoryAdaptor destination, IEnergyGrid energy, IMEMonitor<IAEItemStack> gridInv) {
        if (!oreFilterString.isEmpty()) {
            if (filterPredicate == null) filterPredicate = OreFilteredList.makeFilter(oreFilterString);

            for (IAEItemStack stack : gridInv.getStorageList()) {
                if (stack == null || filterPredicate == null || !this.filterPredicate.test(stack)) continue;
                this.pushItemIntoTarget(destination, energy, gridInv, stack);
                if (this.itemToSend <= 0) break;
            }
        }
    }

    @Override
    protected int getAdaptorFlags() {
        return InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_INSERTS;
    }
}
