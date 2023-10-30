/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

/**
 *
 */
package appeng.client.gui.implementations;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiCraftingCPUTable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ICraftingCPUTableHolder;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.parts.reporting.PartTerminal;

public class GuiCraftingStatus extends GuiCraftingCPU implements ICraftingCPUTableHolder {

    private final ContainerCraftingStatus status;
    private GuiButton selectCPU;
    private final GuiCraftingCPUTable cpuTable;

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;
    private ItemStack myIcon = null;
    private boolean tallMode;
    private GuiImgButton switchTallMode;

    public GuiCraftingStatus(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        this.tallMode = AEConfig.instance.getConfigManager().getSetting(Settings.TERMINAL_STYLE) == TerminalStyle.TALL;
        recalculateScreenSize();

        final Object target = this.status.getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        cpuTable = new GuiCraftingCPUTable(this, this.status.getCPUTable());

        if (target instanceof WirelessTerminalGuiObject) {
            for (final ItemStack wirelessTerminalStack : definitions.items().wirelessTerminal().maybeStack(1).asSet()) {
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
        } else if (btn == this.originalGuiBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(this.originalGui));
        } else if (btn == this.switchTallMode) {
            tallMode = !tallMode;
            switchTallMode.set(tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
            recalculateScreenSize();
            this.setWorldAndResolution(mc, width, height);
        }
    }

    @Override
    public void initGui() {
        recalculateScreenSize();
        super.initGui();
        this.setScrollBar();

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
        this.switchTallMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 166,
                Settings.TERMINAL_STYLE,
                tallMode ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.buttonList.add(switchTallMode);
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
        this.bindTexture("guis/craftingcpu.png");
        if (tallMode) {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, TEXTURE_BELOW_TOP_ROW_Y);
            int y = TEXTURE_BELOW_TOP_ROW_Y;
            // first and last row are pre-baked
            for (int row = 1; row < rows - 1; row++) {
                this.drawTexturedModalRect(
                        offsetX,
                        offsetY + y,
                        0,
                        TEXTURE_BELOW_TOP_ROW_Y,
                        this.xSize,
                        SECTION_HEIGHT);
                y += SECTION_HEIGHT;
            }
            this.drawTexturedModalRect(
                    offsetX,
                    offsetY + y,
                    0,
                    GUI_HEIGHT - TEXTURE_ABOVE_BOTTOM_ROW_Y,
                    this.xSize,
                    TEXTURE_ABOVE_BOTTOM_ROW_Y);
        } else {
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        }
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

    protected void recalculateScreenSize() {
        final int maxAvailableHeight = height - 64;
        this.xSize = GUI_WIDTH;
        if (tallMode) {
            this.rows = (maxAvailableHeight - (SCROLLBAR_TOP + SECTION_HEIGHT)) / SECTION_HEIGHT;
            this.ySize = SCROLLBAR_TOP + SECTION_HEIGHT + 5 + this.rows * SECTION_HEIGHT;
        } else {
            this.rows = DISPLAYED_ROWS;
            this.ySize = GUI_HEIGHT;
        }
    }

    private void setScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(ySize - 47);
        this.getScrollBar().setRange(0, (size + 2) / 3 - this.rows, 1);
    }

    @Override
    public void postUpdate(List<IAEItemStack> list, byte ref) {
        super.postUpdate(list, ref);
        setScrollBar();
    }
}
