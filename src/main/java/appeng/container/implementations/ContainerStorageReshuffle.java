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

        // If reshuffle just completed, get the report and send to client
        if (wasRunning && !this.reshuffleRunning) {
            java.util.List<String> report = this.tile.getReshuffleReport();
            if (report != null && !report.isEmpty()) {
                this.reportLines.clear();
                this.reportLines.addAll(report);
                this.reportScrollPosition = 0;

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
     * Sets the type filter to a specific mode.
     *
     * @param mode "ALL", "ITEMS", or "FLUIDS"
     */
    public void setTypeFilterMode(String mode) {
        Set<IAEStackType<?>> allowedTypes = new HashSet<>();

        if ("ALL".equals(mode)) {
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                allowedTypes.add(type);
            }
        } else if ("ITEMS".equals(mode)) {
            allowedTypes.add(appeng.util.item.AEItemStackType.ITEM_STACK_TYPE);
        } else if ("FLUIDS".equals(mode)) {
            allowedTypes.add(appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE);
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

        String scanReport = this.tile.scanNetwork();

        // Split report into lines for display
        this.reportLines.clear();
        String[] lines = scanReport.split("\n");
        for (String line : lines) {
            this.reportLines.add(line);
        }
        this.reportScrollPosition = 0;

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
            this.reportLines.clear();
            String[] lines = reportData.split("\n");

            // Wrap lines to fit in 190-pixel wide report area (approximately 35 characters)
            for (String line : lines) {
                if (line.isEmpty()) {
                    this.reportLines.add(line);
                    continue;
                }

                // Simple word wrapping - split at ~35 characters
                int maxWidth = 35;
                while (line.length() > maxWidth) {
                    // Find last space before maxWidth
                    int breakPoint = maxWidth;
                    int lastSpace = line.lastIndexOf(' ', maxWidth);
                    if (lastSpace > 0 && lastSpace > maxWidth / 2) {
                        breakPoint = lastSpace;
                    }

                    this.reportLines.add(line.substring(0, breakPoint));
                    line = line.substring(breakPoint).trim();
                }
                if (!line.isEmpty()) {
                    this.reportLines.add(line);
                }
            }
            this.reportScrollPosition = 0;
        }
    }

    /**
     * Get the report lines for display
     */
    public java.util.List<String> getReportLines() {
        return this.reportLines;
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
}
