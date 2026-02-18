/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.tile.misc;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.core.AELog;
import appeng.helpers.CellScanner;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import io.netty.buffer.ByteBuf;

/**
 * Tile entity for the Storage Reshuffle block. Handles the reshuffling of storage contents based on priority. Can run
 * in the background without requiring the GUI to stay open.
 */
public class TileStorageReshuffle extends AENetworkTile implements ITerminalHost {

    private ReshuffleTask activeTask = null;
    private boolean isActive = false;

    // Configuration
    private Set<IAEStackType<?>> allowedTypes = new HashSet<>();
    private boolean voidProtection = true;
    private boolean overwriteProtection = false;

    // Progress tracking
    private int reshuffleProgress = 0;
    private int reshuffleTotalItems = 0;
    private boolean reshuffleRunning = false;
    private java.util.List<String> reshuffleReport = new java.util.ArrayList<>();
    private java.util.List<String> reshuffleTooltipReport = new java.util.ArrayList<>();

    public TileStorageReshuffle() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(4.0);

        // Default to all types
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.allowedTypes.add(type);
        }
    }

    @Override
    public AECableType getCableConnectionType(net.minecraftforge.common.util.ForgeDirection dir) {
        return AECableType.SMART;
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.isActive != currentActive) {
            this.isActive = currentActive;
            this.markForUpdate();
        }
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        final boolean currentActive = this.getProxy().isActive();
        if (this.isActive != currentActive) {
            this.isActive = currentActive;
            this.markForUpdate();

            // Cancel reshuffle if power is lost
            if (!currentActive && this.activeTask != null) {
                this.cancelReshuffle();
            }
        }
    }

    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (this.activeTask != null && this.activeTask.isRunning()) {
            try {
                this.activeTask.processNextBatch();
                this.reshuffleProgress = this.activeTask.getProgressPercent();
                this.reshuffleTotalItems = this.activeTask.getTotalItems();
                this.reshuffleRunning = this.activeTask.isRunning();

                if (!this.activeTask.isRunning()) {
                    // Task completed - get report and send to container
                    ReshuffleReport report = this.activeTask.getReport();
                    if (report != null) {
                        // Generate compact report for GUI display
                        java.util.List<String> reportLines = report.generateReportLines();
                        // Store report for sending to clients
                        this.reshuffleReport = reportLines;

                        // Also generate full tooltip report (with all items, no truncation)
                        java.util.List<String> tooltipReportLines = report.generateTooltipReportLines();
                        this.reshuffleTooltipReport = tooltipReportLines;

                        // Mark for NBT save so the report persists
                        this.markDirty();
                    }

                    // Unlock storage
                    unlockStorage();
                    this.activeTask = null;
                    this.markForUpdate();
                }
            } catch (Exception e) {
                AELog.error(e, "Error during reshuffle task processing in tile entity");
                // Cancel the task on error
                cancelReshuffle();
            }
        }
    }

    /**
     * Starts a reshuffle operation with the current configuration.
     *
     * @param player    The player initiating the reshuffle
     * @param confirmed Whether the operation has been confirmed (for large networks)
     * @return true if started successfully, false otherwise
     */
    public boolean startReshuffle(EntityPlayer player, boolean confirmed) {
        if (!this.getProxy().isActive()) {
            return false;
        }

        if (this.activeTask != null && this.activeTask.isRunning()) {
            return false; // Already running
        }

        try {
            IGrid grid = this.getProxy().getGrid();

            // Check for running crafting jobs
            appeng.api.networking.crafting.ICraftingGrid craftingGrid = grid
                    .getCache(appeng.api.networking.crafting.ICraftingGrid.class);
            if (craftingGrid != null) {
                for (appeng.api.networking.crafting.ICraftingCPU cpu : craftingGrid.getCpus()) {
                    if (cpu.isBusy()) {
                        // Cannot reshuffle while crafting - notify via report
                        return false;
                    }
                }
            }

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);

            // Try to lock storage
            appeng.me.cache.GridStorageCache cache = (appeng.me.cache.GridStorageCache) storageGrid;
            if (!cache.lockStorage(this)) {
                return false; // Another operation is running
            }

            // ...existing code...

            // Get monitors
            Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
            for (IAEStackType<?> type : this.allowedTypes) {
                IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
                if (monitor != null) {
                    monitors.put(type, monitor);
                }
            }

            // Create action source
            BaseActionSource actionSource = player != null ? new PlayerSource(player, this) : new MachineSource(this);

            // Create task
            this.activeTask = new ReshuffleTask(
                    monitors,
                    actionSource,
                    player,
                    this.allowedTypes,
                    this.voidProtection,
                    this.overwriteProtection,
                    true // generate report
            );

            int totalItems = this.activeTask.initialize();

            // Check confirmation for large networks
            if (!confirmed && totalItems >= ReshuffleTask.LARGE_NETWORK_THRESHOLD) {
                this.activeTask = null;
                cache.unlockStorage(this);
                this.reshuffleTotalItems = totalItems;
                return false; // Needs confirmation
            }

            if (totalItems == 0) {
                this.activeTask = null;
                cache.unlockStorage(this);
                return false; // Nothing to do
            }

            // Start the reshuffle
            this.reshuffleTotalItems = totalItems;
            this.reshuffleProgress = 0;
            this.reshuffleRunning = true;
            this.markForUpdate();

            return true;

        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for reshuffle");
            return false;
        }
    }

    /**
     * Cancels any running reshuffle operation.
     */
    public void cancelReshuffle() {
        if (this.activeTask != null && this.activeTask.isRunning()) {
            this.activeTask.cancel();
            unlockStorage();
            this.activeTask = null;
            this.reshuffleRunning = false;
            this.markForUpdate();
        }
    }

    /**
     * Scans the ME Network storage and generates a detailed report using the comprehensive CellScanner
     *
     * @return List of formatted report strings for display in GUI
     */
    public java.util.List<String> scanNetwork() {
        if (!this.getProxy().isActive()) {
            java.util.List<String> error = new java.util.ArrayList<>();
            error.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.networkNotActive"));
            return error;
        }

        try {
            IGrid grid = this.getProxy().getGrid();
            java.util.List<String> scanReport = CellScanner.generateReport(grid);
            java.util.List<String> scanTooltip = CellScanner.generateTooltipReport(grid);

            // Store both the GUI report and the tooltip report
            this.reshuffleReport = new java.util.ArrayList<>(scanReport);
            this.reshuffleTooltipReport = new java.util.ArrayList<>(scanTooltip);

            // Mark for NBT save
            this.markDirty();

            return scanReport;
        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for scan");
            java.util.List<String> error = new java.util.ArrayList<>();
            error.add(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.scanFailed"));
            return error;
        }
    }

    private void unlockStorage() {
        try {
            if (this.getProxy().getGrid() != null) {
                IStorageGrid storageGrid = this.getProxy().getGrid().getCache(IStorageGrid.class);
                if (storageGrid instanceof appeng.me.cache.GridStorageCache cache) {
                    cache.unlockStorage(this);
                }
            }
        } catch (Exception e) {
            AELog.warn(e, "Failed to unlock storage");
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileStorageReshuffle(final ByteBuf data) {
        final boolean wasActive = this.isActive;
        final boolean wasRunning = this.reshuffleRunning;

        this.isActive = data.readBoolean();
        this.reshuffleRunning = data.readBoolean();
        this.reshuffleProgress = data.readInt();
        this.voidProtection = data.readBoolean();
        this.overwriteProtection = data.readBoolean();

        // Read allowed types mask
        int typesMask = data.readByte();
        this.allowedTypes.clear();
        int index = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if ((typesMask & (1 << index)) != 0) {
                this.allowedTypes.add(type);
            }
            index++;
        }

        return wasActive != this.isActive || wasRunning != this.reshuffleRunning;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileStorageReshuffle(final ByteBuf data) {
        data.writeBoolean(this.getProxy().isActive());
        data.writeBoolean(this.reshuffleRunning);
        data.writeInt(this.reshuffleProgress);
        data.writeBoolean(this.voidProtection);
        data.writeBoolean(this.overwriteProtection);

        // Write allowed types as bitmask
        int typesMask = 0;
        int index = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (this.allowedTypes.contains(type)) {
                typesMask |= (1 << index);
            }
            index++;
        }
        data.writeByte(typesMask);
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileStorageReshuffle(final NBTTagCompound data) {
        data.setBoolean("voidProtection", this.voidProtection);
        data.setBoolean("overwriteProtection", this.overwriteProtection);

        // Save allowed types
        StringBuilder types = new StringBuilder();
        for (IAEStackType<?> type : this.allowedTypes) {
            if (types.length() > 0) types.append(",");
            types.append(type.getClass().getSimpleName());
        }
        data.setString("allowedTypes", types.toString());

        // Save the last generated reports (both compact and tooltip versions)
        if (!this.reshuffleReport.isEmpty()) {
            NBTTagCompound reportNBT = new NBTTagCompound();
            reportNBT.setInteger("lineCount", this.reshuffleReport.size());
            for (int i = 0; i < this.reshuffleReport.size(); i++) {
                reportNBT.setString("line" + i, this.reshuffleReport.get(i));
            }
            data.setTag("lastReport", reportNBT);
        }

        if (!this.reshuffleTooltipReport.isEmpty()) {
            NBTTagCompound tooltipReportNBT = new NBTTagCompound();
            tooltipReportNBT.setInteger("lineCount", this.reshuffleTooltipReport.size());
            for (int i = 0; i < this.reshuffleTooltipReport.size(); i++) {
                tooltipReportNBT.setString("line" + i, this.reshuffleTooltipReport.get(i));
            }
            data.setTag("lastTooltipReport", tooltipReportNBT);
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileStorageReshuffle(final NBTTagCompound data) {
        this.voidProtection = data.getBoolean("voidProtection");
        this.overwriteProtection = data.getBoolean("overwriteProtection");

        // Load allowed types (if saved)
        if (data.hasKey("allowedTypes")) {
            String typesStr = data.getString("allowedTypes");
            this.allowedTypes.clear();
            if (!typesStr.isEmpty()) {
                // For now, just load all types - proper type deserialization can be added later
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    this.allowedTypes.add(type);
                }
            }
        }

        // Load the last generated reports
        this.reshuffleReport.clear();
        if (data.hasKey("lastReport")) {
            NBTTagCompound reportNBT = data.getCompoundTag("lastReport");
            int lineCount = reportNBT.getInteger("lineCount");
            for (int i = 0; i < lineCount; i++) {
                if (reportNBT.hasKey("line" + i)) {
                    this.reshuffleReport.add(reportNBT.getString("line" + i));
                }
            }
        }

        this.reshuffleTooltipReport.clear();
        if (data.hasKey("lastTooltipReport")) {
            NBTTagCompound tooltipReportNBT = data.getCompoundTag("lastTooltipReport");
            int lineCount = tooltipReportNBT.getInteger("lineCount");
            for (int i = 0; i < lineCount; i++) {
                if (tooltipReportNBT.hasKey("line" + i)) {
                    this.reshuffleTooltipReport.add(tooltipReportNBT.getString("line" + i));
                }
            }
        }
    }

    // Getters and setters for configuration
    public Set<IAEStackType<?>> getAllowedTypes() {
        return new HashSet<>(this.allowedTypes);
    }

    public void setAllowedTypes(Set<IAEStackType<?>> types) {
        this.allowedTypes = new HashSet<>(types);
        this.markDirty();
        this.markForUpdate();
    }

    public boolean isVoidProtection() {
        return this.voidProtection;
    }

    public void setVoidProtection(boolean voidProtection) {
        this.voidProtection = voidProtection;
        this.markDirty();
        this.markForUpdate();
    }

    public boolean isOverwriteProtection() {
        return this.overwriteProtection;
    }

    public void setOverwriteProtection(boolean overwriteProtection) {
        this.overwriteProtection = overwriteProtection;
        this.markDirty();
        this.markForUpdate();
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

    // ITerminalHost implementation
    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        try {
            return this.getProxy().getStorage().getItemInventory();
        } catch (GridAccessException e) {
            return null;
        }
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        try {
            return this.getProxy().getStorage().getFluidInventory();
        } catch (GridAccessException e) {
            return null;
        }
    }

    @Override
    public IMEMonitor<?> getMEMonitor(IAEStackType<?> type) {
        try {
            return this.getProxy().getStorage().getMEMonitor(type);
        } catch (GridAccessException e) {
            return null;
        }
    }

    public boolean hasPermission(EntityPlayer player, SecurityPermissions permission) {
        try {
            return this.getProxy().getSecurity().hasPermission(player, permission);
        } catch (GridAccessException e) {
            return false;
        }
    }

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        // Storage reshuffle doesn't need a config manager for settings
        // Configuration is stored directly in the tile entity
        return null;
    }

    public java.util.List<String> getReshuffleReport() {
        return new java.util.ArrayList<>(this.reshuffleReport);
    }

    public java.util.List<String> getReshuffleTooltipReport() {
        return new java.util.ArrayList<>(this.reshuffleTooltipReport);
    }

    /**
     * Get the full item name from a truncated version shown in the report. This is used for tooltip display when
     * hovering over truncated names.
     *
     * @param truncatedName The truncated name (without "...")
     * @return The full item name, or null if not found
     */
    public String getFullItemNameFromTruncated(String truncatedName) {
        if (this.activeTask == null || truncatedName == null) {
            return null;
        }

        // Get the full item name map from the task's report
        ReshuffleReport report = this.activeTask.getReport();
        if (report != null) {
            return report.getFullNameForTruncated(truncatedName);
        }

        return null;
    }

    /**
     * Get the list of hidden items for a "... and X more" line. This is used to show tooltips with the complete list.
     *
     * @param lineIndex   The index of the "... and X more" line in the report
     * @param reportLines All report lines
     * @return List of hidden item entries, or null if not found
     */
    public java.util.List<String> getHiddenItemsForMoreLine(int lineIndex, java.util.List<String> reportLines) {
        if (this.activeTask == null || lineIndex < 0 || lineIndex >= reportLines.size()) {
            return null;
        }

        // Determine which section this "more" line belongs to by looking at previous lines
        String moreLine = reportLines.get(lineIndex);

        // Find the section header before this line
        String sectionType = null;
        for (int i = lineIndex - 1; i >= 0; i--) {
            String line = reportLines.get(i);
            String cleanLine = line.replaceAll("§.", "");

            if (cleanLine.contains("Top Lost Items")) {
                sectionType = "lost";
                break;
            } else if (cleanLine.contains("Top Gained Items")) {
                sectionType = "gained";
                break;
            }
        }

        if (sectionType == null) {
            return null;
        }

        // Get the hidden items from the report
        ReshuffleReport report = this.activeTask.getReport();
        if (report != null) {
            return report.getHiddenItems(sectionType);
        }

        return null;
    }
}
