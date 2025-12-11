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

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.implementations.ContainerPriority;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPriorityHost;
import appeng.util.calculators.ArithHelper;
import appeng.util.calculators.Calculator;

public class GuiPriority extends GuiAmount {

    public GuiPriority(final InventoryPlayer inventoryPlayer, final IPriorityHost te) {
        super(new ContainerPriority(inventoryPlayer, te));
    }

    protected GuiPriority(final ContainerPriority container) {
        super(container);
    }

    @Override
    public void initGui() {
        super.initGui();

        // Hide and remove the "Next" button; priority GUI doesn't use it.
        this.nextBtn.enabled = false;
        this.nextBtn.visible = false;
        this.buttonList.remove(this.nextBtn);

        // Hook the container up with our amount field for client sync updates.
        ((ContainerPriority) this.inventorySlots).setTextField(this.amountTextField);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(GuiText.Priority.getLocal(), 8, 6, GuiColors.PriorityTitle.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.amountTextField.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        super.keyTyped(character, key);
        try {
            NetworkHandler.instance
                    .sendToServer(new PacketValueConfig("PriorityHost.Priority", String.valueOf(getAmount())));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // Use priority-specific button quantities
    @Override
    protected int getButtonQtyByIndex(int index) {
        return AEConfig.instance.priorityByStacksAmounts(index);
    }

    @Override
    protected void addAmount(int i) {
        String result = Long.toString(this.getAmount() + i);

        this.amountTextField.setText(result);
        this.amountTextField.setCursorPositionEnd();
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("PriorityHost.Priority", result));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // Allow negative priorities and no clamping
    @Override
    protected int getAmount() {
        try {
            String out = this.amountTextField.getText();
            double result = Calculator.conversion(out);
            if (Double.isNaN(result)) {
                return 0;
            } else {
                return (int) ArithHelper.round(result, 0);
            }
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    protected String getBackground() {
        return "guis/priority.png";
    }
}
