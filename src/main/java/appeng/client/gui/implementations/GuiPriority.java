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
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.container.implementations.ContainerPriority;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPriorityHost;
import appeng.parts.automation.PartFormationPlane;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
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
    protected void setOriginGUI(Object target) {
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();
        final IBlocks blocks = definitions.blocks();

        if (target instanceof PartStorageBus) {
            for (final ItemStack storageBusStack : parts.storageBus().maybeStack(1).asSet()) {
                myIcon = storageBusStack;
            }
            this.originalGui = GuiBridge.GUI_STORAGEBUS;
        }

        if (target instanceof PartFormationPlane) {
            for (final ItemStack formationPlaneStack : parts.formationPlane().maybeStack(1).asSet()) {
                myIcon = formationPlaneStack;
            }
            this.originalGui = GuiBridge.GUI_FORMATION_PLANE;
        }

        if (target instanceof TileDrive) {
            for (final ItemStack driveStack : blocks.drive().maybeStack(1).asSet()) {
                myIcon = driveStack;
            }
            this.originalGui = GuiBridge.GUI_DRIVE;
        }

        if (target instanceof TileChest) {
            for (final ItemStack chestStack : blocks.chest().maybeStack(1).asSet()) {
                myIcon = chestStack;
            }
            this.originalGui = GuiBridge.GUI_CHEST;
        }

        if (target instanceof TileInterface) {
            for (final ItemStack interfaceStack : blocks.iface().maybeStack(1).asSet()) {
                myIcon = interfaceStack;
            }
            this.originalGui = GuiBridge.GUI_INTERFACE;
        }

        if (target instanceof PartInterface) {
            for (final ItemStack interfaceStack : parts.iface().maybeStack(1).asSet()) {
                myIcon = interfaceStack;
            }
            this.originalGui = GuiBridge.GUI_INTERFACE;
        }
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
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10
                || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            try {
                NetworkHandler.instance
                        .sendToServer(new PacketValueConfig("PriorityHost.Priority", String.valueOf(getAmount())));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
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
