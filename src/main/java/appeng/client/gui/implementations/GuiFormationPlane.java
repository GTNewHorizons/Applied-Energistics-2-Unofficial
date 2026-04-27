/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerFormationPlane;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.parts.automation.PartBaseFormationPlane;
import appeng.tile.inventory.IAEStackInventory;

public class GuiFormationPlane extends GuiUpgradeable {

    private GuiTabButton priority;
    private GuiImgButton placeMode;
    private VirtualMEPhantomSlot[] configSlots;
    protected final ContainerFormationPlane cfp;

    public GuiFormationPlane(final InventoryPlayer inventoryPlayer, final PartBaseFormationPlane te) {
        super(new ContainerFormationPlane(inventoryPlayer, te));
        this.ySize = 251;
        this.cfp = (ContainerFormationPlane) this.cvb;
    }

    @Override
    public void initGui() {
        super.initGui();
        initVirtualSlots();
    }

    @Override
    protected void addButtons() {
        if (this.cfp.supportItemDrop()) {
            this.placeMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.PLACE_BLOCK, YesNo.YES);
            this.buttonList.add(this.placeMode);
        }

        this.fuzzyMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 48,
                Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);

        this.buttonList.add(
                this.priority = new GuiTabButton(
                        this.guiLeft + 154,
                        this.guiTop,
                        2 + 4 * 16,
                        GuiText.Priority.getLocal(),
                        itemRender));

        this.buttonList.add(this.fuzzyMode);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.FormationPlane.getLocal()),
                8,
                6,
                GuiColors.FormationPlaneTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.FormationPlaneInventory.getColor());

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.placeMode != null) {
            this.placeMode.set(this.cfp.getPlaceMode());
        }

        this.updateSlotVisibility();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        final int capacity = this.cvb.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        for (int i = 0; i < 7; i++) {
            if (i >= capacity + 2) {
                // fadeout slots
                this.drawTexturedModalRect(offsetX + 7, offsetY + 28 + (18 * i), 7, 46, 162, 18);
            } else {
                // normal slots
                this.drawTexturedModalRect(offsetX + 7, offsetY + 28 + (18 * i), 7, 28, 162, 18);
            }
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        } else if (btn == this.placeMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.placeMode.getSetting(), backwards));
        }
    }

    private void initVirtualSlots() {
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory inputInv = this.cfp.getConfig();
        final int xo = 8;
        final int yo = -133;

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        xo + x * 18,
                        yo + y * 18 + 9 * 18,
                        inputInv,
                        x + y * 9,
                        this::acceptType);
                this.configSlots[x + y * 9] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    protected void updateSlotVisibility() {
        final int capacity = this.cfp.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        for (VirtualMEPhantomSlot slot : this.configSlots) {
            slot.setHidden(slot.getSlotIndex() >= (18 + (9 * capacity)));
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == this.cfp.getStackType();
    }
}
