/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import appeng.api.config.*;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.*;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.util.Platform;
import java.util.ArrayList;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class ContainerInterface extends ContainerUpgradeable implements IOptionalSlotHost {

    private final DualityInterface myDuality;

    @GuiSync(3)
    public YesNo bMode = YesNo.NO;

    @GuiSync(4)
    public YesNo iTermMode = YesNo.YES;

    @GuiSync(8)
    public InsertionMode insertionMode = InsertionMode.DEFAULT;

    @GuiSync(7)
    public int patternRows;

    public ContainerInterface(final InventoryPlayer ip, final IInterfaceHost te) {
        super(ip, te.getInterfaceDuality().getHost());

        this.myDuality = te.getInterfaceDuality();
        patternRows = getPatternCapacityCardsInstalled();

        for (int row = 0; row < 4; ++row) {
            for (int x = 0; x < DualityInterface.NUMBER_OF_PATTERN_SLOTS; x++) {
                this.addSlotToContainer(new OptionalSlotRestrictedInput(
                                SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                                this.myDuality.getPatterns(),
                                this,
                                x + row * DualityInterface.NUMBER_OF_PATTERN_SLOTS,
                                8 + 18 * x,
                                108 - row * 18,
                                row,
                                this.getInventoryPlayer())
                        .setStackLimit(1));
            }
        }

        for (int x = 0; x < DualityInterface.NUMBER_OF_CONFIG_SLOTS; x++) {
            this.addSlotToContainer(new SlotFake(this.myDuality.getConfig(), x, 8 + 18 * x, 15));
        }

        for (int x = 0; x < DualityInterface.NUMBER_OF_STORAGE_SLOTS; x++) {
            this.addSlotToContainer(new SlotNormal(this.myDuality.getStorage(), x, 8 + 18 * x, 15 + 18));
        }
    }

    @Override
    protected int getHeight() {
        return 211;
    }

    @Override
    protected void setupConfig() {
        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 4;
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if (Platform.isClient() && field.equals("patternRows")) getRemovedPatterns();
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (patternRows != getPatternCapacityCardsInstalled()) patternRows = getPatternCapacityCardsInstalled();

        final ArrayList<ItemStack> drops = getRemovedPatterns();
        if (!drops.isEmpty()) {
            TileEntity te = myDuality.getHost().getTile();
            if (te != null) Platform.spawnDrops(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord, drops);
        }
        super.detectAndSendChanges();
    }

    private ArrayList<ItemStack> getRemovedPatterns() {
        final ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        for (final Object o : this.inventorySlots) {
            if (o instanceof OptionalSlotRestrictedInput) {
                final OptionalSlotRestrictedInput fs = (OptionalSlotRestrictedInput) o;
                if (!fs.isEnabled()) {
                    ItemStack s = fs.inventory.getStackInSlot(fs.getSlotIndex());
                    if (s != null) {
                        drops.add(s);
                        fs.inventory.setInventorySlotContents(fs.getSlotIndex(), null);
                        fs.clearStack();
                    }
                }
            }
        }
        return drops;
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        this.setBlockingMode((YesNo) cm.getSetting(Settings.BLOCK));
        this.setInterfaceTerminalMode((YesNo) cm.getSetting(Settings.INTERFACE_TERMINAL));
        this.setInsertionMode((InsertionMode) cm.getSetting(Settings.INSERTION_MODE));
    }

    public YesNo getBlockingMode() {
        return this.bMode;
    }

    private void setBlockingMode(final YesNo bMode) {
        this.bMode = bMode;
    }

    public YesNo getInterfaceTerminalMode() {
        return this.iTermMode;
    }

    private void setInterfaceTerminalMode(final YesNo iTermMode) {
        this.iTermMode = iTermMode;
    }

    public InsertionMode getInsertionMode() {
        return this.insertionMode;
    }

    private void setInsertionMode(final InsertionMode insertionMode) {
        this.insertionMode = insertionMode;
    }

    public int getPatternCapacityCardsInstalled() {
        if (myDuality == null) return 0;
        return myDuality.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY);
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        return myDuality.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY) >= idx;
    }
}
