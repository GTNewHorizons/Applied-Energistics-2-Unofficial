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

/**
 *
 */
package appeng.client.gui.implementations;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ICraftingCPUTableHolder;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

public class GuiCraftingStatus extends GuiCraftingCPU implements ICraftingCPUTableHolder {
    private final ContainerCraftingStatus status;
    private GuiButton selectCPU;
    private final GuiCraftingCPUTable cpuTable;

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;
    private ItemStack myIcon = null;

    public GuiCraftingStatus(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        final Object target = this.status.getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        cpuTable = new GuiCraftingCPUTable(this, this.status.getCPUTable());

        if (target instanceof WirelessTerminalGuiObject) {
            for (final ItemStack wirelessTerminalStack :
                    definitions.items().wirelessTerminal().maybeStack(1).asSet()) {
                this.myIcon = wirelessTerminalStack;
            }

            this.originalGui = GuiBridge.GUI_WIRELESS_TERM;
        }

        if (target instanceof PartTerminal) {
            for (final ItemStack stack : parts.terminal().maybeStack(1).asSet()) {
                this.myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_ME;
        }

        if (target instanceof PartCraftingTerminal) {
            for (final ItemStack stack : parts.craftingTerminal().maybeStack(1).asSet()) {
                this.myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            for (final ItemStack stack : parts.patternTerminal().maybeStack(1).asSet()) {
                this.myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }
        if (target instanceof PartPatternTerminalEx) {
            for (final ItemStack stack : parts.patternTerminalEx().maybeStack(1).asSet()) {
                this.myIcon = stack;
            }
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL_EX;
        }
    }

    @Override
    public GuiCraftingCPUTable getCPUTable() {
        return cpuTable;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.selectCPU) {
            cpuTable.cycleCPU(backwards);
        }

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(this.originalGui));
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.selectCPU = new GuiButton(
                0,
                this.guiLeft + 8,
                this.guiTop + this.ySize - 25,
                150,
                20,
                GuiText.CraftingCPU.getLocal() + ": " + GuiText.NoCraftingCPUs);
        this.buttonList.add(this.selectCPU);

        if (this.myIcon != null) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(
                            this.guiLeft + 213,
                            this.guiTop - 4,
                            this.myIcon,
                            this.myIcon.getDisplayName(),
                            itemRender));
            this.originalGuiBtn.setHideEdge(13);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cpuTable.drawScreen();
        this.updateCPUButtonText();
        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.cpuTable.drawFG(offsetX, offsetY, mouseX, mouseY, guiLeft, guiTop);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.cpuTable.drawBG(offsetX, offsetY);
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) {
        super.mouseClicked(xCoord, yCoord, btn);
        cpuTable.mouseClicked(xCoord - guiLeft, yCoord - guiTop, btn);
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        cpuTable.mouseClickMove(x - guiLeft, y - guiTop);
    }

    @Override
    public void handleMouseInput() {
        if (cpuTable.handleMouseInput(guiLeft, guiTop)) {
            return;
        }
        super.handleMouseInput();
    }

    public boolean hideItemPanelSlot(int x, int y, int w, int h) {
        return cpuTable.hideItemPanelSlot(x - guiLeft, y - guiTop, w, h);
    }

    private void updateCPUButtonText() {
        String btnTextText = GuiText.NoCraftingJobs.getLocal();

        final int selectedSerial = this.cpuTable.getContainer().selectedCpuSerial;
        if (selectedSerial >= 0) {
            String selectedCPUName = cpuTable.getSelectedCPUName();
            if (selectedCPUName != null && selectedCPUName.length() > 0) {
                final String name = selectedCPUName.substring(0, Math.min(20, selectedCPUName.length()));
                btnTextText = GuiText.CPUs.getLocal() + ": " + name;
            } else {
                btnTextText = GuiText.CPUs.getLocal() + ": #" + selectedSerial;
            }
        }

        if (this.status.getCPUs().isEmpty()) {
            btnTextText = GuiText.NoCraftingJobs.getLocal();
        }

        this.selectCPU.displayString = btnTextText;
    }

    @Override
    protected String getGuiDisplayName(final String in) {
        return in; // the cup name is on the button
    }
}
