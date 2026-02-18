/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.SecurityPermissions;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.misc.TileStorageReshuffle;

/**
 * Container for the Storage Reshuffle block. Provides access to reshuffle configuration and status.
 */
public class ContainerStorageReshuffle extends AEBaseContainer {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    public boolean voidProtection = true;

    @GuiSync(1)
    public boolean overwriteProtection = false;

    @GuiSync(2)
    public int typeFilterMask = 0; // Bitmask for which types are enabled

    @GuiSync(3)
    public boolean reshuffleRunning = false;

    @GuiSync(4)
    public int reshuffleProgress = 0;

    @GuiSync(5)
    public int reshuffleTotalItems = 0;

    @GuiSync(6)
    public boolean scanRunning = false;

    @GuiSync(7)
    public int scanProgress = 0;

    // Report lines for display in GUI
    private java.util.List<String> reportLines = new java.util.ArrayList<>();
    private String rawReportData = ""; // Store raw report before wrapping (for GUI)
    private String fullTooltipReportData = ""; // Store full tooltip report (no truncation)
    private int reportScrollPosition = 0;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);

        this.tile = (TileStorageReshuffle) monitorable;

        // Load initial values
        this.voidProtection = this.tile.isVoidProtection();
        this.overwriteProtection = this.tile.isOverwriteProtection();
        this.updateTypeFilterMask();
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        // Update state from tile
        this.voidProtection = this.tile.isVoidProtection();
        this.overwriteProtection = this.tile.isOverwriteProtection();

        boolean wasRunning = this.reshuffleRunning;
        this.reshuffleRunning = this.tile.isReshuffleRunning();
        this.reshuffleProgress = this.tile.getReshuffleProgress();
        this.reshuffleTotalItems = this.tile.getReshuffleTotalItems();

        // Send persisted report to new clients (when GUI first opens)
        // This happens when container is first created and detectAndSendChanges is called
        if (this.reportLines.isEmpty() && !this.crafters.isEmpty()) {
            java.util.List<String> persistedReport = this.tile.getReshuffleReport();
            java.util.List<String> persistedTooltipReport = this.tile.getReshuffleTooltipReport();

            if (persistedReport != null && !persistedReport.isEmpty()) {
                this.reportLines.clear();
                this.reportLines.addAll(persistedReport);
                this.reportScrollPosition = 0;

                if (persistedTooltipReport != null && !persistedTooltipReport.isEmpty()) {
                    StringBuilder tooltipData = new StringBuilder();
                    for (String line : persistedTooltipReport) {
                        if (tooltipData.length() > 0) tooltipData.append("\n");
                        tooltipData.append(line);
                    }
                    this.fullTooltipReportData = tooltipData.toString();
                }

                // Send to clients
                StringBuilder reportData = new StringBuilder();
                for (String line : persistedReport) {
                    if (reportData.length() > 0) reportData.append("\n");
                    reportData.append(line);
                }

                try {
                    for (Object crafter : this.crafters) {
                        if (crafter instanceof EntityPlayerMP) {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig("Reshuffle.Report", reportData.toString()),
                                    (EntityPlayerMP) crafter);
                            if (persistedTooltipReport != null && !persistedTooltipReport.isEmpty()) {
                                NetworkHandler.instance.sendTo(
                                        new PacketValueConfig("Reshuffle.TooltipReport", this.fullTooltipReportData),
                                        (EntityPlayerMP) crafter);
                            }
                        }
                    }
                } catch (Exception e) {
                    AELog.error(e, "Failed to send persisted report to client");
                }
            }
        }

        // If reshuffle just completed, get the report and send to client
        if (wasRunning && !this.reshuffleRunning) {
            java.util.List<String> report = this.tile.getReshuffleReport();
            java.util.List<String> tooltipReport = this.tile.getReshuffleTooltipReport();

            if (report != null && !report.isEmpty()) {
                this.reportLines.clear();
                this.reportLines.addAll(report);
                this.reportScrollPosition = 0;

                // Store the full tooltip report (untruncated)
                if (tooltipReport != null && !tooltipReport.isEmpty()) {
                    StringBuilder tooltipData = new StringBuilder();
                    for (String line : tooltipReport) {
                        if (tooltipData.length() > 0) tooltipData.append("\n");
                        tooltipData.append(line);
                    }
                    this.fullTooltipReportData = tooltipData.toString();
                }

                // Send report to all viewing clients
                if (!this.crafters.isEmpty()) {
                    // Join report lines with delimiter
                    StringBuilder reportData = new StringBuilder();
                    for (String line : report) {
                        if (reportData.length() > 0) reportData.append("\n");
                        reportData.append(line);
                    }

                    try {
                        for (Object crafter : this.crafters) {
                            if (crafter instanceof EntityPlayerMP) {
                                NetworkHandler.instance.sendTo(
                                        new PacketValueConfig("Reshuffle.Report", reportData.toString()),
                                        (EntityPlayerMP) crafter);
                                // Also send the full tooltip report
                                if (tooltipReport != null && !tooltipReport.isEmpty()) {
                                    NetworkHandler.instance.sendTo(
                                            new PacketValueConfig(
                                                    "Reshuffle.TooltipReport",
                                                    this.fullTooltipReportData),
                                            (EntityPlayerMP) crafter);
                                }
                            }
                        }
                    } catch (Exception e) {
                        AELog.error(e, "Failed to send reshuffle report to client");
                    }
                }
            }
        }

        this.updateTypeFilterMask();

        super.detectAndSendChanges();
    }

    private void updateTypeFilterMask() {
        Set<IAEStackType<?>> allowedTypes = this.tile.getAllowedTypes();
        this.typeFilterMask = 0;

        int bitIndex = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (allowedTypes.contains(type)) {
                this.typeFilterMask |= (1 << bitIndex);
            }
            bitIndex++;
        }
    }

    /**
     * Starts the reshuffle operation.
     *
     * @param player    The player initiating the action
     * @param confirmed Whether the operation has been confirmed
     * @return true if started, false if failed
     */
    public boolean startReshuffle(EntityPlayer player, boolean confirmed) {
        // Don't allow reshuffle to start while scanning
        if (this.scanRunning) {
            return false;
        }

        boolean result = this.tile.startReshuffle(player, confirmed);

        // If reshuffle failed to start due to crafting, show message in report
        if (!result && !this.reshuffleRunning) {
            // Check if it's due to crafting jobs
            try {
                if (this.tile.getProxy() != null && this.tile.getProxy().isActive()) {
                    var grid = this.tile.getProxy().getGrid();
                    if (grid != null) {
                        appeng.api.networking.crafting.ICraftingGrid craftingGrid = grid
                                .getCache(appeng.api.networking.crafting.ICraftingGrid.class);
                        if (craftingGrid != null) {
                            for (appeng.api.networking.crafting.ICraftingCPU cpu : craftingGrid.getCpus()) {
                                if (cpu.isBusy()) {
                                    // Show crafting error in report
                                    this.reportLines.clear();
                                    this.reportLines.add(
                                            "§c" + net.minecraft.util.StatCollector.translateToLocal(
                                                    "gui.appliedenergistics2.reshuffle.error.craftingActive"));
                                    this.reportLines.add("");
                                    this.reportLines.add(
                                            "§7" + net.minecraft.util.StatCollector.translateToLocal(
                                                    "gui.appliedenergistics2.reshuffle.error.craftingActiveDesc"));

                                    // Send to client
                                    if (!this.crafters.isEmpty() && player instanceof EntityPlayerMP) {
                                        String reportData = String.join("\n", this.reportLines);
                                        try {
                                            NetworkHandler.instance.sendTo(
                                                    new PacketValueConfig("Reshuffle.Report", reportData),
                                                    (EntityPlayerMP) player);
                                        } catch (Exception e) {
                                            // Ignore
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        return result;
    }

    /**
     * Cancels the current reshuffle operation.
     */
    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    /**
     * Toggles void protection on/off.
     */
    public void toggleVoidProtection() {
        this.tile.setVoidProtection(!this.tile.isVoidProtection());
        this.voidProtection = this.tile.isVoidProtection();
    }

    /**
     * Toggles overwrite protection on/off.
     */
    public void toggleOverwriteProtection() {
        this.tile.setOverwriteProtection(!this.tile.isOverwriteProtection());
        this.overwriteProtection = this.tile.isOverwriteProtection();
    }

    /**
     * Toggles a specific stack type in the filter.
     *
     * @param typeIndex The index of the type to toggle
     */
    public void toggleTypeFilter(int typeIndex) {
        Set<IAEStackType<?>> allowedTypes = new HashSet<>(this.tile.getAllowedTypes());

        int currentIndex = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (currentIndex == typeIndex) {
                if (allowedTypes.contains(type)) {
                    allowedTypes.remove(type);
                } else {
                    allowedTypes.add(type);
                }
                break;
            }
            currentIndex++;
        }

        this.tile.setAllowedTypes(allowedTypes);
        this.updateTypeFilterMask();
    }

    /**
     * Sets the type filter to a specific mode by index. Dynamically supports any stack types registered in
     * AEStackTypeRegistry without requiring code changes when new types are added.
     *
     * @param typeIndex The index of the type to set exclusively, or -1 for all types
     */
    public void setTypeFilterMode(int typeIndex) {
        Set<IAEStackType<?>> allowedTypes = new HashSet<>();

        if (typeIndex == -1) {
            // -1 means "All" - enable all types
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                allowedTypes.add(type);
            }
        } else {
            // Enable only the specified type
            int currentIndex = 0;
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                if (currentIndex == typeIndex) {
                    allowedTypes.add(type);
                    break;
                }
                currentIndex++;
            }
        }

        this.tile.setAllowedTypes(allowedTypes);
        this.updateTypeFilterMask();
    }

    /**
     * Scans the network and stores the report for GUI display
     */
    public void performNetworkScan() {
        // Don't allow scan to start while reshuffling
        if (this.reshuffleRunning) {
            return;
        }

        this.scanRunning = true;
        this.scanProgress = 0;

        java.util.List<String> scanReportLines = this.tile.scanNetwork();
        java.util.List<String> scanTooltipReportLines = this.tile.getReshuffleTooltipReport();

        // Store report
        this.reportLines.clear();
        this.reportLines.addAll(scanReportLines);
        this.reportScrollPosition = 0;

        // Convert to raw string format for network transmission
        String scanReport = String.join("\n", scanReportLines);
        this.rawReportData = scanReport;

        // Store the full tooltip report (untruncated)
        if (scanTooltipReportLines != null && !scanTooltipReportLines.isEmpty()) {
            StringBuilder tooltipData = new StringBuilder();
            for (String line : scanTooltipReportLines) {
                if (tooltipData.length() > 0) tooltipData.append("\n");
                tooltipData.append(line);
            }
            this.fullTooltipReportData = tooltipData.toString();
        }

        this.scanRunning = false;
        this.scanProgress = 100;

        // Send scan report to all viewing clients
        if (!this.crafters.isEmpty()) {
            try {
                for (Object crafter : this.crafters) {
                    if (crafter instanceof EntityPlayerMP) {
                        NetworkHandler.instance.sendTo(
                                new PacketValueConfig("Reshuffle.Report", scanReport),
                                (EntityPlayerMP) crafter);
                        // Also send the full tooltip report
                        if (scanTooltipReportLines != null && !scanTooltipReportLines.isEmpty()) {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig("Reshuffle.TooltipReport", this.fullTooltipReportData),
                                    (EntityPlayerMP) crafter);
                        }
                    }
                }
            } catch (Exception e) {
                AELog.error(e, "Failed to send scan report to client");
            }
        }
    }

    /**
     * Update report from server (called when report packet is received)
     */
    public void updateReport(String reportData) {
        if (reportData != null && !reportData.isEmpty()) {
            // Store raw report data
            this.rawReportData = reportData;

            this.reportLines.clear();
            String[] lines = reportData.split("\n");

            // Wrap lines to fit in 190-pixel wide report area (approximately 35 characters)
            for (String line : lines) {
                if (line.isEmpty()) {
                    this.reportLines.add(line);
                    continue;
                }

                // Word wrapping with color code preservation
                int maxWidth = 48; // Increased from 35 for smaller font (81.25% scale)
                String currentLine = line;
                String lastColorCode = ""; // Track the last color code used

                while (getVisibleLength(currentLine) > maxWidth) {
                    // Find break point considering visible characters only
                    int breakPoint = findBreakPoint(currentLine, maxWidth);

                    // Extract the line up to break point
                    String segment = currentLine.substring(0, breakPoint);

                    // Remember the last color code in this segment
                    String segmentLastColor = extractLastColorCode(segment);
                    if (!segmentLastColor.isEmpty()) {
                        lastColorCode = segmentLastColor;
                    }

                    this.reportLines.add(segment);

                    // Continue with remaining text, prepending last color code
                    currentLine = currentLine.substring(breakPoint).trim();
                    if (!currentLine.isEmpty() && !lastColorCode.isEmpty()) {
                        // Only prepend if the next line doesn't start with a color code
                        if (!currentLine.startsWith("§")) {
                            currentLine = lastColorCode + currentLine;
                        }
                    }
                }
                if (!currentLine.isEmpty()) {
                    this.reportLines.add(currentLine);
                }
            }
            this.reportScrollPosition = 0;
        }
    }

    /**
     * Update full tooltip report from server (called when tooltip report packet is received)
     */
    public void updateTooltipReport(String reportData) {
        if (reportData != null && !reportData.isEmpty()) {
            this.fullTooltipReportData = reportData;
        }
    }

    /**
     * Get the visible length of a string, excluding Minecraft color codes (§x)
     */
    private int getVisibleLength(String text) {
        int length = 0;
        boolean skipNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (c == '§' && i + 1 < text.length()) {
                skipNext = true;
                continue;
            }
            length++;
        }
        return length;
    }

    /**
     * Find the break point for line wrapping, considering visible characters only
     */
    private int findBreakPoint(String text, int maxVisibleWidth) {
        int visibleCount = 0;
        int lastSpacePos = -1;
        int lastSpaceVisiblePos = -1;
        boolean skipNext = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (skipNext) {
                skipNext = false;
                continue;
            }

            if (c == '§' && i + 1 < text.length()) {
                skipNext = true;
                i++; // Skip the next character too
                continue;
            }

            if (c == ' ') {
                lastSpacePos = i;
                lastSpaceVisiblePos = visibleCount;
            }

            visibleCount++;

            if (visibleCount >= maxVisibleWidth) {
                // Try to break at last space if it's reasonable
                if (lastSpacePos > 0 && lastSpaceVisiblePos > maxVisibleWidth / 2) {
                    return lastSpacePos;
                }
                return i + 1;
            }
        }

        return text.length();
    }

    /**
     * Extract the last color code (§x) from a string
     */
    private String extractLastColorCode(String text) {
        String lastColor = "";
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '§') {
                lastColor = text.substring(i, i + 2);
            }
        }
        return lastColor;
    }

    /**
     * Get the report lines for display
     */
    public java.util.List<String> getReportLines() {
        return this.reportLines;
    }

    /**
     * Get the full untruncated report lines (for full report tooltip)
     */
    public java.util.List<String> getFullReportLines() {
        // Use the full tooltip report if available, otherwise fall back to raw report
        String dataToUse = (this.fullTooltipReportData != null && !this.fullTooltipReportData.isEmpty())
                ? this.fullTooltipReportData
                : this.rawReportData;

        if (dataToUse == null || dataToUse.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        String[] lines = dataToUse.split("\n");
        return java.util.Arrays.asList(lines);
    }

    /**
     * Get the scan report lines (unwrapped, with original formatting)
     */
    public java.util.List<String> getScanReportLines() {
        return getFullReportLines(); // Same as full report lines
    }

    /**
     * Get current scroll position in report
     */
    public int getReportScrollPosition() {
        return this.reportScrollPosition;
    }

    /**
     * Set scroll position in report
     */
    public void setReportScrollPosition(int position) {
        this.reportScrollPosition = Math.max(0, Math.min(position, getMaxScrollPosition()));
    }

    /**
     * Get maximum scroll position based on report size
     */
    public int getMaxScrollPosition() {
        // Assuming 12 lines visible at once (104px height / ~9px per line)
        int visibleLines = 11;
        return Math.max(0, this.reportLines.size() - visibleLines);
    }

    public boolean isReshuffleRunning() {
        return this.reshuffleRunning;
    }

    public int getReshuffleProgress() {
        return this.reshuffleProgress;
    }

    public int getReshuffleTotalItems() {
        return this.reshuffleTotalItems;
    }

    /**
     * Get the full item name for a truncated line in the report. This is used to show tooltips for truncated item
     * names.
     *
     * @param line The report line that may contain a truncated name
     * @return The full item name, or null if not found
     */
    public String getFullItemNameForLine(String line) {
        if (this.tile == null) {
            return null;
        }

        // Extract the identifier from the line
        // Format: "§color • §0ItemName... §color+/-value §0(from → to)"

        // Remove color codes
        String cleanLine = line.replaceAll("§.", "");

        // Extract the item name part (between "• " and the first number or "+"/"-")
        int bulletIdx = cleanLine.indexOf("• ");
        if (bulletIdx == -1) {
            return null;
        }

        String afterBullet = cleanLine.substring(bulletIdx + 2).trim();

        // Find where the value part starts (+ or - followed by number)
        int valueStartIdx = -1;
        for (int i = 0; i < afterBullet.length(); i++) {
            char c = afterBullet.charAt(i);
            if ((c == '+' || c == '-') && i + 1 < afterBullet.length()
                    && Character.isDigit(afterBullet.charAt(i + 1))) {
                valueStartIdx = i;
                break;
            }
        }

        if (valueStartIdx == -1) {
            return null;
        }

        String truncatedName = afterBullet.substring(0, valueStartIdx).trim();

        // Ask the tile entity to find the full name
        return this.tile.getFullItemNameFromTruncated(truncatedName);
    }

    /**
     * Get the list of hidden items for a "... and X more" line. This is used to show tooltips with the complete list,
     * paginated if necessary.
     *
     * @param lineIndex The index of the "... and X more" line in the report
     * @return List of hidden item lines, or null if not found
     */
    public java.util.List<String> getHiddenItemsForMoreLine(int lineIndex) {
        if (this.tile == null || lineIndex < 0 || lineIndex >= this.reportLines.size()) {
            return null;
        }

        // Ask the tile entity for the hidden items list
        return this.tile.getHiddenItemsForMoreLine(lineIndex, this.reportLines);
    }
}
