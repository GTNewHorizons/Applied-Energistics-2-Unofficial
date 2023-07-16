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

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.Reflected;

public class GuiPatternTerm extends GuiBasePatternTerm {

    private static final String CRAFTMODE_CRFTING = "1";
    private static final String CRAFTMODE_PROCESSING = "0";

    private final GuiTabButton tabCraftButton;
    private final GuiTabButton tabProcessButton;

    @Reflected
    public GuiPatternTerm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTerm(inventoryPlayer, te));
        this.btnArrLeft = 84;

        this.tabCraftButton = new GuiTabButton(
                this.guiLeft + 173,
                this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.crafting_table),
                GuiText.CraftingPattern.getLocal(),
                itemRender);
        this.tabProcessButton = new GuiTabButton(
                this.guiLeft + 173,
                this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.furnace),
                GuiText.ProcessingPattern.getLocal(),
                itemRender);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (this.tabCraftButton == btn || this.tabProcessButton == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminal.CraftMode",
                                this.tabProcessButton == btn ? CRAFTMODE_CRFTING : CRAFTMODE_PROCESSING));
                tabCraftButton.visible = !tabCraftButton.visible;
                tabProcessButton.visible = !tabProcessButton.visible;
                doubleBtn.visible = !doubleBtn.visible;
            } else if (this.encodeBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminal.Encode",
                                isCtrlKeyDown() ? (isShiftKeyDown() ? "6" : "1") : (isShiftKeyDown() ? "2" : "1")));
            } else if (this.clearBtn == btn) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            } else if (this.subsEnabledBtn == btn || this.subsDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminal.Substitute",
                                this.subsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            } else if (this.amSubEnabledBtn == btn || this.amSubDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminal.BeSubstitute",
                                this.amSubEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            } else if (doubleBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminal.Double",
                                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? "1" : "0"));
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initLayout() {
        btnArrLeft = this.guiLeft + 84;
        btnArrTop = this.guiTop + this.ySize - 163;
        btnOffsetX = 10;
        btnOffsetY = 10;

        tabCraftButton.xPosition = this.guiLeft + 173;
        tabCraftButton.yPosition = this.guiTop + this.ySize - 177;
        tabProcessButton.xPosition = this.guiLeft + 173;
        tabProcessButton.yPosition = this.guiTop + this.ySize - 177;
    }

    @Override
    public void initGui() {
        super.initGui();

        buttonList.add(tabCraftButton);
        buttonList.add(tabProcessButton);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (!((ContainerPatternTerm) this.container).craftingMode) {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.doubleBtn.visible = true;
        } else {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.doubleBtn.visible = false;
        }

        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRendererObj.drawString(
                GuiText.PatternTerminal.getLocal(),
                8,
                this.ySize - 96 + 2 - this.getReservedSpace(),
                GuiColors.PatternTerminalTitle.getColor());
    }

    @Override
    protected String getBackground() {
        if (((ContainerPatternTerm) this.container).craftingMode) {
            return "guis/pattern.png";
        }
        return "guis/pattern2.png";
    }
}
