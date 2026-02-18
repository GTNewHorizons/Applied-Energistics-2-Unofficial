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

    // Layout constants (v8c Tall layout)
    private static final int REPORT_LABEL_Y = 230;
    private static final int REPORT_VIEW_X = 12;
    private static final int REPORT_VIEW_Y = 242;
    private static final int REPORT_VIEW_W = 200;
    private static final int REPORT_VIEW_H = 104;

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

    // Pagination for "... x more" tooltip
    private int tooltipPage = 0;
    private int maxTooltipPage = 0;

    // Full report button
    private GuiImgButton fullReportButton;
    private boolean fullReportMode = false;

    // Scrollbar dragging state
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;
    private int dragStartScrollPos = 0;

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

        // Full report toggle button in Report header area (right side of "Report:" label)
        // Using EXTRA_OPTIONS icon from states.png for "more details"
        this.fullReportButton = new GuiImgButton(
                this.guiLeft + REPORT_VIEW_X + REPORT_VIEW_W + 4,
                this.guiTop + REPORT_LABEL_Y,
                Settings.ACTIONS,
                ActionItems.EXTRA_OPTIONS);
        this.fullReportButton.setHalfSize(true);
        this.buttonList.add(this.fullReportButton);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.filterAllButton) {
                // -1 = All types
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "-1"));
            } else if (btn == this.filterItemsButton) {
                // 0 = First type (typically Items)
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "0"));
            } else if (btn == this.filterFluidsButton) {
                // 1 = Second type (typically Fluids)
                NetworkHandler.instance.sendToServer(new PacketValueConfig("Reshuffle.SetTypeFilter", "1"));
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
            } else if (btn == this.fullReportButton) {
                // Toggle full report mode (shows full untruncated report as tooltip)
                fullReportMode = !fullReportMode;
                this.tooltipPage = 0; // Reset pagination when toggling
            }
        } catch (final IOException e) {
            // Handle silently
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) { // Left click
            // Check if clicking on scrollbar
            java.util.List<String> reportLines = this.container.getReportLines();
            int maxVisibleLines = 13;
            int totalLines = reportLines.size();

            if (totalLines > maxVisibleLines) {
                int scrollbarX = this.guiLeft + 216;
                int scrollbarY = this.guiTop + 242;
                int scrollbarHeight = 104;
                int scrollbarWidth = 12;

                // Calculate knob position
                int knobHeight = 15;
                int scrollPos = this.container.getReportScrollPosition();
                int maxScroll = Math.max(0, totalLines - maxVisibleLines);
                int knobOffset = maxScroll > 0 ? (scrollPos * (scrollbarHeight - knobHeight) / maxScroll) : 0;
                int knobY = scrollbarY + knobOffset;

                // Check if clicking on knob
                if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth
                        && mouseY >= knobY
                        && mouseY <= knobY + knobHeight) {
                    this.isDraggingScrollbar = true;
                    this.dragStartY = mouseY;
                    this.dragStartScrollPos = scrollPos;
                }
            }
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) { // Left button released
            this.isDraggingScrollbar = false;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceClick) {
        super.mouseClickMove(mouseX, mouseY, mouseButton, timeSinceClick);

        if (this.isDraggingScrollbar && mouseButton == 0) {
            java.util.List<String> reportLines = this.container.getReportLines();
            int maxVisibleLines = 13;
            int totalLines = reportLines.size();
            int maxScroll = Math.max(0, totalLines - maxVisibleLines);

            if (maxScroll > 0) {
                int scrollbarHeight = 104;
                int knobHeight = 15;
                int dragDeltaY = mouseY - this.dragStartY;

                // Convert pixel delta to scroll position delta
                int scrollDelta = (dragDeltaY * maxScroll) / (scrollbarHeight - knobHeight);
                int newScrollPos = Math.max(0, Math.min(maxScroll, this.dragStartScrollPos + scrollDelta));

                this.container.setReportScrollPosition(newScrollPos);
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Handle pagination keys for tooltip
        if (keyCode == Keyboard.KEY_Z) {
            // Previous page
            if (this.tooltipPage > 0) {
                this.tooltipPage--;
            }
        } else if (keyCode == Keyboard.KEY_X) {
            // Next page
            if (this.tooltipPage < this.maxTooltipPage) {
                this.tooltipPage++;
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
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

        // Full report toggle button tooltip
        int fullReportBtnX = REPORT_VIEW_X + REPORT_VIEW_W + 4;
        int fullReportBtnY = REPORT_LABEL_Y;
        if (relX >= fullReportBtnX && relX <= (fullReportBtnX + 8)
                && relY >= fullReportBtnY
                && relY <= (fullReportBtnY + 8)) {

            // Show the full untruncated report as a paginated tooltip
            java.util.List<String> fullReportLines = new java.util.ArrayList<>();

            // Get the full report (works for both scan and reshuffle reports)
            java.util.List<String> fullReport = this.container.getFullReportLines();

            if (fullReport != null && !fullReport.isEmpty()) {
                // Show ALL items with pagination for very large reports
                int itemsPerPage = 30; // Items per page
                this.maxTooltipPage = Math.max(0, (fullReport.size() - 1) / itemsPerPage);

                // Clamp tooltip page
                if (this.tooltipPage > this.maxTooltipPage) {
                    this.tooltipPage = this.maxTooltipPage;
                }

                // Add header with consistent color scheme
                fullReportLines.add("§3§lFull Report"); // Dark aqua to match report style
                fullReportLines.add("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");

                // Calculate page range
                int startIdx = this.tooltipPage * itemsPerPage;
                int endIdx = Math.min(startIdx + itemsPerPage, fullReport.size());

                // Add items for current page - preserve all original colors
                for (int i = startIdx; i < endIdx; i++) {
                    String line = fullReport.get(i);
                    // Keep original colors from report generation
                    fullReportLines.add(line);
                }

                // Add pagination info if needed
                if (this.maxTooltipPage > 0) {
                    fullReportLines.add("");
                    fullReportLines.add("§8━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    fullReportLines.add("§7Page " + (this.tooltipPage + 1) + " / " + (this.maxTooltipPage + 1));
                    fullReportLines.add("§ePress Z/X to navigate pages");
                }
            } else {
                // No report data available
                fullReportLines.add("§7No scan data available");
                fullReportLines.add("§7Click 'Scan' to generate report");
            }

            this.drawHoveringText(fullReportLines, mouseX, mouseY, this.fontRendererObj);
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

        // Handle report area tooltips for truncated text and "... x more" (lowest priority)
        // Only show if not hovering over any GUI buttons
        boolean overGuiButton = false;
        for (Object obj : this.buttonList) {
            if (obj instanceof GuiButton) {
                GuiButton btn = (GuiButton) obj;
                if (relX >= btn.xPosition - this.guiLeft && relX < btn.xPosition - this.guiLeft + btn.width
                        && relY >= btn.yPosition - this.guiTop
                        && relY < btn.yPosition - this.guiTop + btn.height) {
                    overGuiButton = true;
                    break;
                }
            }
        }

        if (!overGuiButton) {
            renderReportTooltips(mouseX, mouseY);
        }
    }

    /**
     * Render tooltips for the report area - shows expanded "... x more" lists
     */
    private void renderReportTooltips(int mouseX, int mouseY) {
        int reportX = this.guiLeft + 14;
        int reportY = this.guiTop + 246;
        int reportW = 196;
        int reportH = 98;

        // Check if mouse is in report area
        if (mouseX < reportX || mouseX > reportX + reportW || mouseY < reportY || mouseY > reportY + reportH) {
            return;
        }

        java.util.List<String> reportLines = this.container.getReportLines();
        if (reportLines.isEmpty()) {
            return;
        }

        int scrollPos = this.container.getReportScrollPosition();
        int lineHeight = 7;
        int maxVisibleLines = 13;

        // Calculate which line the mouse is hovering over
        // Account for the 4px top padding added to fix text cutoff
        int relativeY = mouseY - reportY;
        int hoveredLineIndex = (int) (relativeY / (lineHeight * 0.8125f)); // Account for 0.8125 scale

        if (hoveredLineIndex < 0 || hoveredLineIndex >= maxVisibleLines) {
            return;
        }

        int actualLineIndex = scrollPos + hoveredLineIndex;
        if (actualLineIndex >= reportLines.size()) {
            return;
        }

        String hoveredLine = reportLines.get(actualLineIndex);

        // Strip color codes for detection
        String cleanLine = hoveredLine.replaceAll("§.", "");

        // Check if it's a "... and X more" line (case insensitive, flexible spacing)
        if (cleanLine.toLowerCase().contains("...") && cleanLine.toLowerCase().contains("more")) {
            showMoreItemsTooltip(actualLineIndex, mouseX, mouseY);
        }
    }

    /**
     * Show tooltip for "... and X more" lines with pagination
     */
    private void showMoreItemsTooltip(int lineIndex, int mouseX, int mouseY) {
        java.util.List<String> hiddenItems = this.container.getHiddenItemsForMoreLine(lineIndex);

        if (hiddenItems == null || hiddenItems.isEmpty()) {
            return;
        }

        // Pagination settings
        int itemsPerPage = 10;
        this.maxTooltipPage = Math.max(0, (hiddenItems.size() - 1) / itemsPerPage);

        // Clamp tooltip page
        if (this.tooltipPage > this.maxTooltipPage) {
            this.tooltipPage = this.maxTooltipPage;
        }

        java.util.List<String> tooltip = new java.util.ArrayList<>();

        // Add header with cyan color
        tooltip.add(
                "§b" + net.minecraft.util.StatCollector
                        .translateToLocal("gui.appliedenergistics2.reshuffle.report.hiddenItems"));
        tooltip.add("§8" + "━━━━━━━━━━━━━━━━━━━━━━━━"); // Separator

        // Calculate page range
        int startIdx = this.tooltipPage * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, hiddenItems.size());

        // Add items for current page
        for (int i = startIdx; i < endIdx; i++) {
            tooltip.add(hiddenItems.get(i));
        }

        // Add pagination info if needed
        if (this.maxTooltipPage > 0) {
            tooltip.add(""); // Blank line
            tooltip.add("§8" + "━━━━━━━━━━━━━━━━━━━━━━━━"); // Separator
            tooltip.add(
                    "§7" + net.minecraft.util.StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.reshuffle.report.pagination",
                            this.tooltipPage + 1,
                            this.maxTooltipPage + 1));
            tooltip.add(
                    "§e" + net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.paginationHelp"));
        }

        this.drawHoveringText(tooltip, mouseX, mouseY, this.fontRendererObj);
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
                    "§8" + net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.statusIdle"),
                    12,
                    statusY,
                    0xFFFFFF);
            this.startButton.displayString = net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.start");
            this.startButton.enabled = true;
        }

        String percentText = this.container.reshuffleProgress + "%";
        this.fontRendererObj.drawString(percentText, 196, 183, 0x808080); // Neutral gray like §8

        int processed = this.container.reshuffleProgress * this.container.reshuffleTotalItems / 100;
        String counterText = net.minecraft.util.StatCollector.translateToLocalFormatted(
                "gui.appliedenergistics2.reshuffle.processed",
                processed,
                this.container.reshuffleTotalItems);
        this.fontRendererObj.drawString(counterText, 12, 200, 0x303030);

        this.fontRendererObj.drawString("?", 217, 8, 0x202020);

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.filter"),
                12,
                34,
                0x202020);

        // Draw filter labels with scaled text (0.8x)
        GL11.glPushMatrix();
        GL11.glScalef(0.8f, 0.8f, 0.8f);

        String allLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.all");
        String allLabel = allActive ? "§a" + allLabelText : "§8" + allLabelText;
        this.fontRendererObj.drawString(allLabel, (int) (44 / 0.8f), (int) (54 / 0.8f), 0xFFFFFF);

        String itemsLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.items");
        String itemsLabel = itemsActive ? "§a" + itemsLabelText : "§8" + itemsLabelText;
        this.fontRendererObj.drawString(itemsLabel, (int) (44 / 0.8f), (int) (74 / 0.8f), 0xFFFFFF);

        String fluidsLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.filter.fluids");
        String fluidsLabel = fluidsActive ? "§a" + fluidsLabelText : "§8" + fluidsLabelText;
        this.fontRendererObj.drawString(fluidsLabel, (int) (44 / 0.8f), (int) (94 / 0.8f), 0xFFFFFF);

        GL11.glPopMatrix();

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.protection"),
                124,
                34,
                0x202020);

        // Draw protection labels with scaled text (0.8x)
        GL11.glPushMatrix();
        GL11.glScalef(0.8f, 0.8f, 0.8f);

        // Label uses neutral gray (§8), only state (ON/OFF) is colored
        String voidLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.voidProtection");
        String voidState = this.container.voidProtection ? "§aON" : "§cOFF";
        this.fontRendererObj
                .drawString("§8" + voidLabelText + ": " + voidState, (int) (156 / 0.8f), (int) (54 / 0.8f), 0xFFFFFF);

        String overwriteLabelText = net.minecraft.util.StatCollector
                .translateToLocal("gui.appliedenergistics2.reshuffle.overwriteProtection");
        String overwriteState = this.container.overwriteProtection ? "§aON" : "§cOFF";
        this.fontRendererObj.drawString(
                "§8" + overwriteLabelText + ": " + overwriteState,
                (int) (156 / 0.8f),
                (int) (74 / 0.8f),
                0xFFFFFF);

        GL11.glPopMatrix();

        this.fontRendererObj.drawString(
                net.minecraft.util.StatCollector.translateToLocal("gui.appliedenergistics2.reshuffle.report"),
                12,
                230,
                0x202020);

        // Render scrollable report content
        java.util.List<String> reportLines = this.container.getReportLines();
        if (!reportLines.isEmpty()) {
            int scrollPos = this.container.getReportScrollPosition();
            int reportX = 14; // 2px padding from left edge (was 12)
            int reportY = 246; // 4px padding from top (was 242) to prevent text cutoff
            int lineHeight = 7; // Reduced from 8 for smaller font
            int maxVisibleLines = 13; // Can fit more lines with smaller font

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
            GL11.glScalef(0.8125f, 0.8125f, 1.0f); // 81.25% scale = roughly 1.5 points smaller

            float invScale = 1.0f / 0.8125f;
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

        // Draw scrollbar knob for report area (using AE2 style from creative tabs texture)
        java.util.List<String> reportLines = this.container.getReportLines();
        if (!reportLines.isEmpty()) {
            int maxVisibleLines = 13;
            int totalLines = reportLines.size();

            if (totalLines > maxVisibleLines) {
                // Scrollbar is needed
                int scrollbarX = offsetX + 216;
                int scrollbarY = offsetY + 242;
                int scrollbarHeight = 104;
                int scrollbarWidth = 12;

                // Calculate knob position (knob is fixed 15px height like AE2 standard)
                int knobHeight = 15;
                int scrollPos = this.container.getReportScrollPosition();
                int maxScroll = Math.max(0, totalLines - maxVisibleLines);
                int knobOffset = maxScroll > 0 ? (scrollPos * (scrollbarHeight - knobHeight) / maxScroll) : 0;
                int knobY = scrollbarY + knobOffset;

                // Bind the creative inventory tabs texture (same as GuiScrollbar)
                this.bindTexture("minecraft", "gui/container/creative_inventory/tabs.png");
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                // Draw the scrollbar knob using the texture
                // Texture coordinates: (232, 0) for active scrollbar
                this.drawTexturedModalRect(scrollbarX, knobY, 232, 0, scrollbarWidth, knobHeight);
            }
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
