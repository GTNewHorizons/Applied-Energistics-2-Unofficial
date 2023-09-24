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

import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerInterface;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.IInterfaceHost;

public class GuiInterface extends GuiUpgradeable {

    private GuiTabButton priority;
    private GuiImgButton BlockMode;
    private GuiToggleButton interfaceMode;
    private GuiImgButton insertionMode;

    private GuiImgButton advancedBlockingMode;

    public GuiInterface(final InventoryPlayer inventoryPlayer, final IInterfaceHost te) {
        super(new ContainerInterface(inventoryPlayer, te));
        this.ySize = 211;
    }

    @Override
    protected void addButtons() {
        this.priority = new GuiTabButton(
                this.guiLeft + 154,
                this.guiTop,
                2 + 4 * 16,
                GuiText.Priority.getLocal(),
                itemRender);
        this.buttonList.add(this.priority);

        this.BlockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.BLOCK, YesNo.NO);
        this.buttonList.add(this.BlockMode);

        this.interfaceMode = new GuiToggleButton(
                this.guiLeft - 18,
                this.guiTop + 26,
                84,
                85,
                GuiText.InterfaceTerminal.getLocal(),
                GuiText.InterfaceTerminalHint.getLocal());
        this.buttonList.add(this.interfaceMode);

        this.insertionMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 44,
                Settings.INSERTION_MODE,
                InsertionMode.DEFAULT);
        this.buttonList.add(this.insertionMode);

        this.advancedBlockingMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 62,
                Settings.ADVANCED_BLOCKING_MODE,
                AdvancedBlockingMode.DEFAULT);
        this.advancedBlockingMode.visible = this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0;
        this.buttonList.add(advancedBlockingMode);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.BlockMode != null) {
            this.BlockMode.set(((ContainerInterface) this.cvb).getBlockingMode());
        }

        if (this.interfaceMode != null) {
            this.interfaceMode.setState(((ContainerInterface) this.cvb).getInterfaceTerminalMode() == YesNo.YES);
        }

        if (this.insertionMode != null) {
            this.insertionMode.set(((ContainerInterface) this.cvb).getInsertionMode());
        }

        if (this.advancedBlockingMode != null) {
            this.advancedBlockingMode.set(((ContainerInterface) this.cvb).getAdvancedBlockingMode());
        }

        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.Interface.getLocal()),
                8,
                6,
                GuiColors.InterfaceTitle.getColor());
    }

    @Override
    protected String getBackground() {
        return switch (((ContainerInterface) this.cvb).getPatternCapacityCardsInstalled()) {
            case -1 -> "guis/interfacenone.png";
            case 1 -> "guis/interface2.png";
            case 2 -> "guis/interface3.png";
            case 3 -> "guis/interface4.png";
            default -> "guis/interface.png";
        };
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        }

        if (btn == this.interfaceMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
        }

        if (btn == this.BlockMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.BlockMode.getSetting(), backwards));
        }

        if (btn == this.insertionMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.insertionMode.getSetting(), backwards));
        }

        if (btn == this.advancedBlockingMode) {
            NetworkHandler.instance
                    .sendToServer(new PacketConfigButton(this.advancedBlockingMode.getSetting(), backwards));
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.advancedBlockingMode != null) {
            this.advancedBlockingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0);
        }
    }
}
