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

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.automation.PartBaseFormationPlane;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerFormationPlane extends ContainerUpgradeable implements IVirtualSlotHolder {

    @GuiSync(10)
    public YesNo placeMode;
    public final PartBaseFormationPlane te;
    private final IAEStack<?>[] configClientSlot = new IAEStack[63];

    public ContainerFormationPlane(final InventoryPlayer ip, final PartBaseFormationPlane te) {
        super(ip, te);
        this.te = te;
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
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setPlaceMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.PLACE_BLOCK));

            final IAEStackInventory config = this.te.getAEInventoryByName(StorageName.CONFIG);
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.te.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) {
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }
    }

    public YesNo getPlaceMode() {
        return this.placeMode;
    }

    private void setPlaceMode(final YesNo placeMode) {
        this.placeMode = placeMode;
    }

    public IAEStackType<?> getStackType() {
        return this.te.getStackType();
    }

    public IAEStackInventory getConfig() {
        return this.te.getAEInventoryByName(StorageName.CONFIG);
    }

    public boolean supportItemDrop() {
        return this.te.supportItemDrop();
    }

    public boolean supportFuzzy() {
        return this.te.supportFuzzy();
    }
}
