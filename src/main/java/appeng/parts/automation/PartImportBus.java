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

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.OreFilteredList;

public class PartImportBus extends PartBaseImportBus<IAEItemStack> implements IInventoryDestination {

    @Reflected
    public PartImportBus(final ItemStack is) {
        super(is);
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }

        final IAEItemStack out = this.destination.injectItems(
                this.lastItemChecked = AEApi.instance().storage().createItemStack(stack),
                Actionable.SIMULATE,
                this.mySrc);
        if (out == null) {
            return true;
        }
        return out.getStackSize() != stack.stackSize;
    }

    @Override
    protected Object getTarget() {
        return this.getHandler();
    }

    @Override
    protected int getPowerMultiplier() {
        return 1;
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
    protected boolean importStuff(final Object myTarget, final IAEItemStack whatToImport,
            final IMEMonitor<IAEItemStack> inv, final IEnergySource energy, final FuzzyMode fzMode) {
        if (!(myTarget instanceof InventoryAdaptor myAdaptor)) return true;
        final int toSend = this.calculateMaximumAmountToImport(myAdaptor, whatToImport, inv, fzMode);
        final ItemStack newItems;

        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            newItems = myAdaptor.removeSimilarItems(
                    toSend,
                    whatToImport == null ? null : whatToImport.getItemStack(),
                    fzMode,
                    this.configDestination(inv));
        } else {
            newItems = myAdaptor.removeItems(
                    toSend,
                    whatToImport == null ? null : whatToImport.getItemStack(),
                    this.configDestination(inv));
        }

        if (newItems != null) {
            newItems.stackSize = (int) (Math.min(
                    newItems.stackSize,
                    energy.extractAEPower(newItems.stackSize, Actionable.SIMULATE, PowerMultiplier.CONFIG)) + 0.01);
            this.itemToSend -= newItems.stackSize;

            if (this.lastItemChecked == null || !this.lastItemChecked.isSameType(newItems)) {
                this.lastItemChecked = AEApi.instance().storage().createItemStack(newItems);
            } else {
                this.lastItemChecked.setStackSize(newItems.stackSize);
            }

            final IAEItemStack failed = Platform
                    .poweredInsert(energy, this.destination, this.lastItemChecked, this.mySrc);

            if (failed != null) {
                myAdaptor.addItems(failed.getItemStack());
                return true;
            } else {
                this.worked = true;
            }
        } else {
            return true;
        }

        return false;
    }

    private int calculateMaximumAmountToImport(final InventoryAdaptor myAdaptor, final IAEItemStack whatToImport,
            final IMEMonitor<IAEItemStack> inv, final FuzzyMode fzMode) {
        final int toSend = Math.min(this.itemToSend, 64);
        final ItemStack itemStackToImport;

        if (whatToImport == null) {
            itemStackToImport = null;
        } else {
            itemStackToImport = whatToImport.getItemStack();
        }

        final IAEItemStack itemAmountNotStorable;
        final ItemStack simResult;
        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            simResult = myAdaptor.simulateSimilarRemove(toSend, itemStackToImport, fzMode, this.configDestination(inv));
        } else {
            simResult = myAdaptor.simulateRemove(toSend, itemStackToImport, this.configDestination(inv));
        }
        itemAmountNotStorable = this.destination
                .injectItems(AEItemStack.create(simResult), Actionable.SIMULATE, this.mySrc);

        if (itemAmountNotStorable != null) {
            return (int) Math.min(simResult.stackSize - itemAmountNotStorable.getStackSize(), toSend);
        }

        return toSend;
    }

    private IInventoryDestination configDestination(final IMEMonitor<IAEItemStack> itemInventory) {
        this.destination = itemInventory;
        return this;
    }

    @Override
    protected boolean doOreDict(final Object myTarget, IMEMonitor<IAEItemStack> inv, final IEnergyGrid energy,
            final FuzzyMode fzMode) {
        if (!(myTarget instanceof InventoryAdaptor myAdaptor)) return false;
        if (!oreFilterString.isEmpty()) {
            if (filterPredicate == null) filterPredicate = OreFilteredList.makeFilter(oreFilterString);
            for (ItemSlot slot : myAdaptor) {
                if (this.itemToSend <= 0) break;
                if (slot.isExtractable() && filterPredicate != null && filterPredicate.test(slot.getAEItemStack())) {
                    while (this.itemToSend > 0) {
                        if (this.importStuff(myAdaptor, slot.getAEItemStack(), inv, energy, fzMode)) break;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected int getAdaptorFlags() {
        return InventoryAdaptor.ALLOW_ITEMS | InventoryAdaptor.FOR_EXTRACTS;
    }
}
