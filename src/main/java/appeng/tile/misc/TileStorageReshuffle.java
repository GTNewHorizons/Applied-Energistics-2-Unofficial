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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalTypeFilterProvider;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.helpers.CellScanReport;
import appeng.helpers.CellScanTask;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.MonitorableTypeFilter;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class TileStorageReshuffle extends AENetworkTile
        implements ITerminalTypeFilterProvider, IConfigurableObject, IConfigManagerHost {

    private ReshuffleTask activeTask = null;
    private boolean isActive = false;

    private final MonitorableTypeFilter typeFilters = new MonitorableTypeFilter();
    private final ConfigManager cm = new ConfigManager(this);

    private int reshuffleProgress = 0;
    private int reshuffleTotalItems = 0;
    private int reshuffleProcessedItems = 0;
    private boolean reshuffleRunning = false;
    private List<String> reshuffleReport = new ArrayList<>();

    public TileStorageReshuffle() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(4.0);
        this.cm.registerSetting(Settings.VOID_PROTECTION, YesNo.YES);
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        final boolean current = this.getProxy().isActive();
        if (this.isActive != current) {
            this.isActive = current;
            this.markForUpdate();
        }
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        final boolean current = this.getProxy().isActive();
        if (this.isActive != current) {
            this.isActive = current;
            this.markForUpdate();
            if (!current && this.activeTask != null) this.cancelReshuffle();
        }
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream(final ByteBuf data) {
        data.writeBoolean(this.reshuffleRunning);
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream(final ByteBuf data) {
        final boolean wasRunning = this.reshuffleRunning;
        this.reshuffleRunning = data.readBoolean();
        return wasRunning != this.reshuffleRunning;
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileStorageReshuffle(final NBTTagCompound data) {
        this.cm.writeToNBT(data);
        this.typeFilters.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileStorageReshuffle(final NBTTagCompound data) {
        this.cm.readFromNBT(data);
        this.typeFilters.readFromNBT(data);
    }

    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (this.activeTask != null && this.activeTask.isRunning()) {
            try {
                this.activeTask.processNextBatch();
                this.reshuffleProgress = this.activeTask.getProgressPercent();
                this.reshuffleTotalItems = this.activeTask.getTotalItems();
                this.reshuffleProcessedItems = this.activeTask.getProcessedItems();
                this.reshuffleRunning = this.activeTask.isRunning();
                if (!this.activeTask.isRunning()) {
                    ReshuffleReport report = this.activeTask.getReport();
                    if (report != null) {
                        this.reshuffleReport = report.generateReportLines();
                        this.markDirty();
                    }
                    unlockStorage();
                    this.activeTask = null;
                    this.markForUpdate();
                }
            } catch (Exception e) {
                AELog.error(e, "Error during reshuffle task processing");
                cancelReshuffle();
            }
        }
    }

    @Override
    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getTypeFilter(final EntityPlayer player) {
        return this.typeFilters.getFilters(player);
    }

    @Override
    public void saveTypeFilter() {
        this.saveChanges();
        this.markForUpdate();
    }

    public boolean startReshuffle(EntityPlayer player, boolean confirmed) {
        if (!this.getProxy().isActive()) return false;
        if (this.activeTask != null && this.activeTask.isRunning()) return false;

        try {
            final ICraftingGrid craftingGrid = this.getProxy().getCrafting();
            for (final ICraftingCPU cpu : craftingGrid.getCpus()) {
                if (cpu.isBusy()) {
                    this.reshuffleReport = java.util.Arrays.asList(
                            GuiText.ReshuffleReportActiveCrafts.getLocal(),
                            GuiText.ReshuffleReportActiveCraftsDetail.getLocal());
                    this.markDirty();
                    return false;
                }
            }

            final IStorageGrid storageGrid = this.getProxy().getStorage();

            final Reference2BooleanMap<IAEStackType<?>> filters = this.typeFilters.getFilters(player);
            final Set<IAEStackType<?>> allowedTypes = new HashSet<>();
            for (Reference2BooleanMap.Entry<IAEStackType<?>> e : filters.reference2BooleanEntrySet()) {
                if (e.getBooleanValue()) allowedTypes.add(e.getKey());
            }

            Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
            for (IAEStackType<?> type : allowedTypes) {
                IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
                if (monitor != null) {
                    monitors.put(type, monitor);
                    if (monitor instanceof NetworkMonitor<?>nm) nm.setLocked(true);
                }
            }

            BaseActionSource actionSource = new ReshuffleActionSource(player, this);
            final boolean voidProtection = this.cm.getSetting(Settings.VOID_PROTECTION) == YesNo.YES;
            this.activeTask = new ReshuffleTask(monitors, actionSource, player, allowedTypes, voidProtection, true);

            int totalItems = this.activeTask.initialize();
            if (!confirmed && totalItems >= 3000) {
                this.activeTask = null;
                unlockMonitors(monitors);
                this.reshuffleTotalItems = totalItems;
                return false;
            }
            if (totalItems == 0) {
                this.activeTask = null;
                unlockMonitors(monitors);
                return false;
            }

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

    public void cancelReshuffle() {
        if (this.activeTask != null && this.activeTask.isRunning()) {
            this.activeTask.cancel();
            unlockStorage();
            this.activeTask = null;
            this.reshuffleRunning = false;
            this.markForUpdate();
        }
    }

    public void scanNetwork() {
        if (!this.getProxy().isActive()) {
            this.reshuffleReport = Collections.singletonList(GuiText.ReshuffleReportNetworkNotActive.getLocal());
            this.markDirty();
            return;
        }
        try {
            final IGrid grid = this.getProxy().getGrid();
            final List<CellScanTask.CellRecord> cells = CellScanTask.scanGrid(grid);
            this.reshuffleReport = new CellScanReport(cells).generateReportLines();
            this.markDirty();
        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for scan");
            this.reshuffleReport = Collections.singletonList(GuiText.ReshuffleReportScanFailed.getLocal());
            this.markDirty();
        }
    }

    private void unlockStorage() {
        try {
            final IStorageGrid storageGrid = this.getProxy().getStorage();
            for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final IMEMonitor<?> monitor = storageGrid.getMEMonitor(type);
                if (monitor instanceof NetworkMonitor<?>nm) nm.setLocked(false);
            }
        } catch (Exception e) {
            AELog.warn(e, "Failed to unlock storage monitors");
        }
    }

    private void unlockMonitors(Map<IAEStackType<?>, IMEMonitor<?>> monitors) {
        for (IMEMonitor<?> monitor : monitors.values()) {
            if (monitor instanceof NetworkMonitor<?>nm) nm.setLocked(false);
        }
    }

    public boolean isVoidProtection() {
        return this.cm.getSetting(Settings.VOID_PROTECTION) == YesNo.YES;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.saveChanges();
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

    public int getReshuffleProcessedItems() {
        return this.reshuffleProcessedItems;
    }

    public List<String> getReshuffleReport() {
        return new ArrayList<>(this.reshuffleReport);
    }
}
