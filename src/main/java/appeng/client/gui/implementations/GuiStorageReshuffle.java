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

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerStorageReshuffle;
import appeng.core.localization.GuiColors;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

/**
 * GUI for the Storage Reshuffle block.
 */
public class GuiStorageReshuffle extends AEBaseGui {

    private final ContainerStorageReshuffle container;

    // Filter buttons
    private GuiImgButton filterAllButton;
    private GuiImgButton filterItemsButton;
    private GuiImgButton filterFluidsButton;

    // Protection buttons
    private GuiImgButton voidProtectionButton;
    private GuiImgButton overwriteProtectionButton;

    // Action buttons
    private GuiButton startButton;
    private GuiButton scanButton;

    public GuiStorageReshuffle(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerStorageReshuffle(inventoryPlayer, te));
        this.container = (ContainerStorageReshuffle) this.inventorySlots;
        this.xSize = 232; // v8c Tall layout width
        this.ySize = 360; // v8c Tall layout height
    }

    @Override
    public void initGui() {
        super.initGui();

        // Filter Mode buttons (16×16 icons, vertical layout at x=24)
        this.filterAllButton = new GuiImgButton(
                this.guiLeft + 24,
                this.guiTop + 50,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_STORAGE_ALL);
        this.buttonList.add(this.filterAllButton);

        this.filterItemsButton = new GuiImgButton(
                this.guiLeft + 24,
                this.guiTop + 70,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_STORAGE_ITEMS);
        this.buttonList.add(this.filterItemsButton);

        this.filterFluidsButton = new GuiImgButton(
                this.guiLeft + 24,
                this.guiTop + 90,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_STORAGE_FLUIDS);
        this.buttonList.add(this.filterFluidsButton);

        // Protection buttons (16×16 icons, vertical layout at x=136)
        this.voidProtectionButton = new GuiImgButton(
                this.guiLeft + 136,
                this.guiTop + 50,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_VOID_PROTECTION);
        this.buttonList.add(this.voidProtectionButton);

        this.overwriteProtectionButton = new GuiImgButton(
                this.guiLeft + 136,
                this.guiTop + 70,
                Settings.ACTIONS,
                ActionItems.RESHUFFLE_OVERWRITE_PROTECTION);
        this.buttonList.add(this.overwriteProtectionButton);

        // Start/Cancel button in Actions card (96×20 at x=24, y=130)
        this.startButton = new GuiButton(
                2,
                this.guiLeft + 24,
                this.guiTop + 130,
                96,
                20,
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.start"));
        this.buttonList.add(this.startButton);

        // Scan button in Actions card (72×20 at x=132, y=130)
        this.scanButton = new GuiButton(
                3,
                this.guiLeft + 132,
                this.guiTop + 130,
                72,
                20,
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.scan"));
        this.buttonList.add(this.scanButton);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.filterAllButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "ALL"));
            } else if (btn == this.filterItemsButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "ITEMS"));
            } else if (btn == this.filterFluidsButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "FLUIDS"));
            } else if (btn == this.voidProtectionButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.ToggleVoidProtection", ""));
            } else if (btn == this.overwriteProtectionButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.ToggleOverwriteProtection", ""));
            } else if (btn == this.startButton) {
                if (this.container.reshuffleRunning) {
                    NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Cancel", ""));
                } else {
                    boolean confirmed = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    NetworkHandler.instance
                            .sendToServer(new PacketValueConfig("Reshuffle.Start", confirmed ? "confirmed" : ""));
                }
            } else if (btn == this.scanButton) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.Scan", ""));
            }
        } catch (final IOException e) {
            // Handle silently
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        // Handle mouse wheel scrolling in report area
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            // Check if mouse is in report viewport (12, 242, 200, 104)
            int reportX = this.guiLeft + 12;
            int reportY = this.guiTop + 242;
            int reportW = 200;
            int reportH = 104;

            if (mouseX >= reportX && mouseX <= reportX + reportW && mouseY >= reportY && mouseY <= reportY + reportH) {
                int scrollDelta = wheel > 0 ? -1 : 1;
                int newPos = this.container.getReportScrollPosition() + scrollDelta;
                this.container.setReportScrollPosition(newPos);
            }
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Add tooltips for buttons
        int relX = mouseX - this.guiLeft;
        int relY = mouseY - this.guiTop;

        // Help icon tooltip
        if (relX >= 214 && relX <= 226 && relY >= 6 && relY <= 18) {
            java.util.List<String> helpLines = new java.util.ArrayList<>();
            helpLines.add(
                    net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.help.title"));
            helpLines.add("");
            helpLines.add(
                    net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.help.desc1"));
            helpLines.add(
                    net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.help.desc2"));
            helpLines.add(
                    net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.help.desc3"));
            helpLines.add("");
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.filterHeader"));
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.filterAll"));
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.filterItems"));
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.filterFluids"));
            helpLines.add("");
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.protectionHeader"));
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.protectionVoid"));
            helpLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.help.protectionOverwrite"));
            this.drawHoveringText(helpLines, mouseX, mouseY, this.fontRendererObj);
            return;
        }

        // Start/Cancel button tooltip
        if (relX >= 24 && relX <= 120 && relY >= 130 && relY <= 150) {
            java.util.List<String> startLines = new java.util.ArrayList<>();
            if (this.container.reshuffleRunning) {
                startLines.add(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.cancel.title"));
                startLines.add(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.cancel.desc1"));
            } else {
                startLines.add(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.start.title"));
                startLines.add(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.start.desc1"));
                startLines.add(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.start.desc2"));
            }
            this.drawHoveringText(startLines, mouseX, mouseY, this.fontRendererObj);
            return;
        }

        // Scan button tooltip
        if (relX >= 132 && relX <= 204 && relY >= 130 && relY <= 150) {
            java.util.List<String> scanLines = new java.util.ArrayList<>();
            scanLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.scan.title"));
            scanLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.scan.desc1"));
            scanLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.scan.desc2"));
            scanLines.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.tooltip.scan.desc3"));
            this.drawHoveringText(scanLines, mouseX, mouseY, this.fontRendererObj);
            return;
        }

        // Note: Filter and protection button tooltips are handled by GuiImgButton automatically
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(
                        net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.title")),
                8,
                6,
                GuiColors.SecurityCardEditorTitle.getColor());

        boolean allActive = isFilterModeAll();
        boolean itemsActive = isFilterModeItems();
        boolean fluidsActive = isFilterModeFluids();

        int statusY = 166;
        if (this.container.scanRunning) {
            // Show scanning status
            this.fontRendererObj.drawString(
                    "§b" + net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.statusScanning"),
                    12,
                    statusY,
                    0xFFFFFF);
            this.startButton.displayString = net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.start");
            this.startButton.enabled = false; // Disable start button while scanning
        } else if (this.container.reshuffleRunning) {
            this.fontRendererObj.drawString(
                    "§e" + net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.statusRunning"),
                    12,
                    statusY,
                    0xFFFFFF);
            this.startButton.displayString = net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.cancel");
            this.startButton.enabled = true;
        } else {
            this.fontRendererObj.drawString(
                    "§7" + net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.statusIdle"),
                    12,
                    statusY,
                    0xFFFFFF);
            this.startButton.displayString = net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.start");
            this.startButton.enabled = true;
        }

        String percentText = this.container.reshuffleProgress + "%";
        this.fontRendererObj.drawString(percentText, 196, 183, 0xFFFFFF);

        int processed = this.container.reshuffleProgress * this.container.reshuffleTotalItems / 100;
        String counterText = net.minecraft.util.StatCollector.translateToLocalFormatted(
                "gui.appliedenergistics2.reshuffle.processed",
                processed,
                this.container.reshuffleTotalItems);
        this.fontRendererObj.drawString(counterText, 12, 200, 0x808080);

        this.fontRendererObj.drawString("?", 217, 8, 0x404040);

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.filter"),
                12,
                34,
                0x404040);

        // Draw filter labels with scaled text (0.8x)
        GL11.glPushMatrix();
        GL11.glScalef(0.8f, 0.8f, 0.8f);

        String allLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.all");
        String allLabel = allActive ? "§a" + allLabelText : "§7" + allLabelText;
        this.fontRendererObj.drawString(allLabel, (int) (44 / 0.8f), (int) (54 / 0.8f), 0xFFFFFF);

        String itemsLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.items");
        String itemsLabel = itemsActive ? "§a" + itemsLabelText : "§7" + itemsLabelText;
        this.fontRendererObj.drawString(itemsLabel, (int) (44 / 0.8f), (int) (74 / 0.8f), 0xFFFFFF);

        String fluidsLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.fluids");
        String fluidsLabel = fluidsActive ? "§a" + fluidsLabelText : "§7" + fluidsLabelText;
        this.fontRendererObj.drawString(fluidsLabel, (int) (44 / 0.8f), (int) (94 / 0.8f), 0xFFFFFF);

        GL11.glPopMatrix();

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.protection"),
                124,
                34,
                0x404040);

        // Draw protection labels with scaled text (0.8x)
        GL11.glPushMatrix();
        GL11.glScalef(0.8f, 0.8f, 0.8f);

        String voidLabel = this.container.voidProtection ? "§aON" : "§cOFF";
        this.fontRendererObj
                .drawString(
                        net.minecraft.util.StatCollector.translateToLocal(
                                "gui.appliedenergistics2.reshuffle.voidProtection") + ": " + voidLabel,
                        (int) (156 / 0.8f),
                        (int) (54 / 0.8f),
                        0xFFFFFF);

        String overwriteLabel = this.container.overwriteProtection ? "§aON" : "§cOFF";
        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal(
                        "gui.appliedenergistics2.reshuffle.overwriteProtection") + ": " + overwriteLabel,
                (int) (156 / 0.8f),
                (int) (74 / 0.8f),
                0xFFFFFF);

        GL11.glPopMatrix();

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.report"),
                12,
                230,
                0x404040);

        // Render scrollable report content
        java.util.List<String> reportLines = this.container.getReportLines();
        if (!reportLines.isEmpty()) {
            int scrollPos = this.container.getReportScrollPosition();
            int reportX = 14; // 2px padding from left edge (was 12)
            int reportY = 246; // 4px padding from top (was 242) to prevent text cutoff
            int lineHeight = 8; // Reduced from 9 to fit smaller font
            int maxVisibleLines = 12; // Can fit more lines with smaller font

            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            int scale = new net.minecraft.client.gui.ScaledResolution(
                    this.mc,
                    this.mc.displayWidth,
                    this.mc.displayHeight).getScaleFactor();
            int scissorX = (this.guiLeft + reportX) * scale;
            int scissorY = this.mc.displayHeight - (this.guiTop + reportY + 98) * scale; // Adjusted for 4px top padding
            int scissorW = 196 * scale; // Reduced from 200 to account for left/right padding
            int scissorH = 98 * scale; // Reduced to account for 4px top padding
            GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

            // Scale down text rendering for smaller font
            GL11.glPushMatrix();
            GL11.glScalef(0.875f, 0.875f, 1.0f); // 87.5% scale = roughly 1 point smaller

            float invScale = 1.0f / 0.875f;
            for (int i = 0; i < maxVisibleLines && (scrollPos + i) < reportLines.size(); i++) {
                String line = reportLines.get(scrollPos + i);
                int scaledX = (int) (reportX * invScale);
                int scaledY = (int) ((reportY + (i * lineHeight)) * invScale);
                this.fontRendererObj.drawString(line, scaledX, scaledY, 0xFFFFFF);
            }

            GL11.glPopMatrix();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Draw main GUI background
        this.bindTexture("guis/reshuffle.png");
        this.drawScaledTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize, 256, 512);

        // Draw progress bar
        int barX = offsetX + 12;
        int barY = offsetY + 184;
        int barWidth = 176;
        int barHeight = 7;

        if (this.container.reshuffleProgress > 0) {
            int fillWidth = (int) Math.floor(this.container.reshuffleProgress * barWidth / 100.0);

            int startColor = 0xFF00FFFF; // Cyan
            int endColor = getProgressColor(this.container.reshuffleProgress);

            this.drawGradientRect(barX + 1, barY + 1, barX + 1 + fillWidth, barY + barHeight - 1, startColor, endColor);

            int knobX = barX + fillWidth;
            int knobWidth = 3;
            this.drawRect(knobX, barY, knobX + knobWidth, barY + barHeight, 0xFFFFFFFF); // White knob
        }
    }

    /**
     * Draw a textured rectangle with proper scaling for textures larger than 256×256
     */
    private void drawScaledTexturedModalRect(int x, int y, int u, int v, int width, int height, int textureWidth,
            int textureHeight) {
        float f = 1.0F / textureWidth;
        float f1 = 1.0F / textureHeight;
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, this.zLevel, u * f, (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y + height, this.zLevel, (u + width) * f, (v + height) * f1);
        tessellator.addVertexWithUV(x + width, y, this.zLevel, (u + width) * f, v * f1);
        tessellator.addVertexWithUV(x, y, this.zLevel, u * f, v * f1);
        tessellator.draw();
    }

    /**
     * Get progress bar color based on percentage - Cyan (0%) -> Green (100%)
     */
    private int getProgressColor(int progress) {
        // Cyan (0x00FFFF) to Green (0x00FF00)
        // Remove blue component gradually
        int blue = 255 - (progress * 255 / 100);
        return 0xFF00FF00 | (blue); // Green + decreasing blue
    }

    /**
     * Check if ALL filter mode is active
     */
    private boolean isFilterModeAll() {
        // Count how many types are enabled
        int enabledCount = Integer.bitCount(this.container.typeFilterMask);
        int totalTypes = appeng.api.storage.data.AEStackTypeRegistry.getAllTypes().size();
        return enabledCount >= totalTypes;
    }

    /**
     * Check if ITEMS only filter mode is active
     */
    private boolean isFilterModeItems() {
        // Only item stack type bit should be set
        return this.container.typeFilterMask == 1; // First bit = items
    }

    /**
     * Check if FLUIDS only filter mode is active
     */
    private boolean isFilterModeFluids() {
        // Only fluid stack type bit should be set
        return this.container.typeFilterMask == 2; // Second bit = fluids
    }

    protected String getBackground() {
        return "guis/reshuffle.png";
    }
}
