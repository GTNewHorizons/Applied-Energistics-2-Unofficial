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
                        java.util.List<String> reportLines = report.generateReportLines();
                        // Store report for sending to clients
                        this.reshuffleReport = reportLines;
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
     * Scans the ME Network storage and generates a detailed report
     *
     * @return Formatted report string
     */
    public String scanNetwork() {
        if (!this.getProxy().isActive()) {
            return net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.report.networkNotActive");
        }

        try {
            IGrid grid = this.getProxy().getGrid();
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            appeng.me.cache.GridStorageCache cache = (appeng.me.cache.GridStorageCache) storageGrid;

            StringBuilder report = new StringBuilder();
            report.append("§3═══════ "); // Dark aqua instead of gold
            report.append(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.header"));
            report.append(" ═══════\n\n");

            // Count different stack types
            long itemTypes = 0;
            long fluidTypes = 0;
            long essentiaTypes = 0;
            long totalItems = 0;
            long totalFluids = 0;
            long totalEssentia = 0;

            // Get monitors for each type
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
                if (monitor != null) {
                    var list = monitor.getStorageList();
                    if (type == appeng.util.item.AEItemStackType.ITEM_STACK_TYPE) {
                        itemTypes = list.size();
                        for (var stack : list) {
                            totalItems += stack.getStackSize();
                        }
                    } else if (type == appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE) {
                        fluidTypes = list.size();
                        for (var stack : list) {
                            totalFluids += stack.getStackSize();
                        }
                    } else if (type.toString().contains("Essentia")) {
                        essentiaTypes = list.size();
                        for (var stack : list) {
                            totalEssentia += stack.getStackSize();
                        }
                    }
                }
            }

            report.append("§b── ");
            report.append(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.stackTypes"));
            report.append(" ──\n");
            report.append("§7  "); // Gray instead of white
            report.append(
                    net.minecraft.util.StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.reshuffle.report.items",
                            itemTypes,
                            totalItems));
            report.append("\n");
            report.append("§7  "); // Gray instead of white
            report.append(
                    net.minecraft.util.StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.reshuffle.report.fluids",
                            fluidTypes,
                            totalFluids));
            report.append("\n");
            if (essentiaTypes > 0) {
                report.append("§7  "); // Gray instead of white
                report.append(
                        net.minecraft.util.StatCollector.translateToLocalFormatted(
                                "gui.appliedenergistics2.reshuffle.report.essentia",
                                essentiaTypes,
                                totalEssentia));
                report.append("\n");
            }
            report.append("\n");

            // Analyze storage cells
            var cellStats = analyzeCells(cache);
            report.append("§3── "); // Dark aqua instead of bright aqua
            report.append(
                    net.minecraft.util.StatCollector
                            .translateToLocal("gui.appliedenergistics2.reshuffle.report.storageCells"));
            report.append(" ──\n");
            report.append("§7  "); // Gray instead of white
            report.append(
                    net.minecraft.util.StatCollector.translateToLocalFormatted(
                            "gui.appliedenergistics2.reshuffle.report.totalCells",
                            cellStats.totalCells));
            report.append("\n\n");

            // Cell type breakdown
            if (!cellStats.cellsByType.isEmpty()) {
                report.append("§3── "); // Dark aqua
                report.append(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.report.cellTypes"));
                report.append(" ──\n");
                for (var entry : cellStats.cellsByType.entrySet()) {
                    report.append("§7  "); // Gray
                    report.append(
                            net.minecraft.util.StatCollector.translateToLocalFormatted(
                                    "gui.appliedenergistics2.reshuffle.report.cellType",
                                    entry.getKey(),
                                    entry.getValue()));
                    report.append("\n");
                }
                report.append("\n");
            }

            // Estimate potential savings from reshuffle
            long estimatedFreedBytes = estimateReshuffleSavings(cache);
            if (estimatedFreedBytes > 0) {
                report.append("§3── "); // Dark aqua
                report.append(
                        net.minecraft.util.StatCollector
                                .translateToLocal("gui.appliedenergistics2.reshuffle.report.reshuffleEstimate"));
                report.append(" ──\n");
                report.append("§7  "); // Gray
                report.append(
                        net.minecraft.util.StatCollector.translateToLocalFormatted(
                                "gui.appliedenergistics2.reshuffle.report.potentialFreed",
                                estimatedFreedBytes));
                report.append("\n");
            }

            report.append("\n§3═══════════════════════════════════"); // Dark aqua

            return report.toString();

        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for scan");
            return net.minecraft.util.StatCollector
                    .translateToLocal("gui.appliedenergistics2.reshuffle.report.scanFailed");
        }
    }

    private static class CellStats {

        int totalCells = 0;
        long itemCount = 0;
        long fluidCount = 0;
        Map<String, Integer> cellsByType = new java.util.HashMap<>();
    }

    private CellStats analyzeCells(appeng.me.cache.GridStorageCache cache) {
        CellStats stats = new CellStats();

        try {
            // Get all cell providers (drives, chests, etc.)
            var cellProviders = cache.getAllCellProviders();

            for (var provider : cellProviders) {
                // Count item cells
                var itemHandlers = provider.getCellArray(appeng.util.item.AEItemStackType.ITEM_STACK_TYPE);
                stats.totalCells += itemHandlers.size();
                stats.cellsByType.merge("Item Cells", itemHandlers.size(), Integer::sum);
                for (var handler : itemHandlers) {
                    if (handler != null) {
                        var list = appeng.util.item.AEItemStackType.ITEM_STACK_TYPE.createList();
                        handler.getAvailableItems(list);
                        stats.itemCount += list.size();
                    }
                }

                // Count fluid cells
                var fluidHandlers = provider.getCellArray(appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE);
                stats.totalCells += fluidHandlers.size();
                stats.cellsByType.merge("Fluid Cells", fluidHandlers.size(), Integer::sum);
                for (var handler : fluidHandlers) {
                    if (handler != null) {
                        var list = appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE.createList();
                        handler.getAvailableItems(list);
                        stats.fluidCount += list.size();
                    }
                }
            }

        } catch (Exception e) {
            AELog.warn(e, "Failed to analyze cells");
        }

        return stats;
    }

    private long estimateReshuffleSavings(appeng.me.cache.GridStorageCache cache) {
        // Estimate how many bytes could be saved by optimizing storage
        // This is a rough estimate based on fragmentation
        try {
            long potentialSavings = 0;

            // Check for items spread across multiple cells that could be consolidated
            var cellProviders = cache.getAllCellProviders();
            Map<String, Integer> itemCellCount = new java.util.HashMap<>();

            for (var provider : cellProviders) {
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    var handlers = provider.getCellArray(type);
                    for (var handler : handlers) {
                        if (handler != null) {
                            var list = type.createList();
                            handler.getAvailableItems(list);
                            for (var stack : list) {
                                String key = stack.toString();
                                itemCellCount.merge(key, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            // Items in multiple cells = fragmentation = potential savings
            for (var count : itemCellCount.values()) {
                if (count > 1) {
                    potentialSavings += (count - 1) * 8; // Rough estimate: 8 bytes per type overhead
                }
            }

            return potentialSavings;

        } catch (Exception e) {
            AELog.warn(e, "Failed to estimate reshuffle savings");
            return 0;
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
}
