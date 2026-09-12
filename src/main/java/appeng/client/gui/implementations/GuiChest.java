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

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerChest;
import appeng.core.localization.ColorUtils;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.tile.storage.TileChest;

public class GuiChest extends AEBaseGui {

    private GuiTabButton priority;
    private GuiImgButton reshuffleAccess;

    public GuiChest(final InventoryPlayer inventoryPlayer, final TileChest te) {
        super(new ContainerChest(inventoryPlayer, te));
        this.ySize = 166;
    }

    @Override
    protected void actionPerformed(final GuiButton par1GuiButton) {
        super.actionPerformed(par1GuiButton);

        final boolean backwards = Mouse.isButtonDown(1);

        if (par1GuiButton == this.priority) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        }
        if (par1GuiButton == this.reshuffleAccess) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.RESHUFFLE_ACCESS, backwards));
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(
                this.priority = new GuiTabButton(
                        this.guiLeft + 154,
                        this.guiTop,
                        2 + 4 * 16,
                        GuiText.Priority.getLocal(),
                        itemRender));
        this.buttonList.add(
                this.reshuffleAccess = new GuiImgButton(
                        this.guiLeft - 18,
                        this.guiTop + 8,
                        Settings.RESHUFFLE_ACCESS,
                        AccessRestriction.READ_WRITE));
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.reshuffleAccess.set(((ContainerChest) this.inventorySlots).getReshuffleAccess());
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.Chest.getLocal()),
                8,
                6,
                ColorUtils.guiTextColorGray.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                ColorUtils.guiTextColorGray.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/chest.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}
