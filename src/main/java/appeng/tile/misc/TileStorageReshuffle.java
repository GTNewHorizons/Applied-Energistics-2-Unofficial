package appeng.tile.misc;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.ITypeFilterProvider;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.AELog;
import appeng.helpers.PartitionScanTask;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.AEStackTypeFilter;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import io.netty.buffer.ByteBuf;

public class TileStorageReshuffle extends AENetworkTile
        implements IConfigurableObject, IConfigManagerHost, ITypeFilterProvider {

    private ReshuffleTask activeTask = null;
    private Map<IAEStackType<?>, IMEMonitor<?>> lockedMonitors = null;
    private boolean isActive = false;

    private final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
    private final ConfigManager cm = new ConfigManager(this);

    private int reshuffleProgress = 0;
    private int reshuffleTotalItems = 0;
    private int reshuffleProcessedItems = 0;
    private boolean reshuffleRunning = false;
    private boolean reshuffleFailed = false;
    private boolean reshuffleCancelled = false;
    private boolean reshuffleComplete = false;
    private ReshuffleReport reshuffleReport = null;
    private PartitionScanTask lastScanDuplicates = null;

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
                final long deadline = System.nanoTime() + ReshuffleTask.TICK_BUDGET_NS;
                while (this.activeTask.isRunning() && System.nanoTime() < deadline) {
                    this.activeTask.processNextBatch();
                }
                this.reshuffleProgress = this.activeTask.getProgressPercent();
                this.reshuffleTotalItems = this.activeTask.getTotalItems();
                this.reshuffleProcessedItems = this.activeTask.getProcessedItems();
                this.reshuffleRunning = this.activeTask.isRunning();
                if (!this.activeTask.isRunning()) {
                    final ReshuffleReport report = this.activeTask.getReport();
                    if (report != null) {
                        this.reshuffleReport = report;
                        this.markDirty();
                    }
                    unlockStorage();
                    this.activeTask = null;
                    this.reshuffleComplete = true;
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
    public AEStackTypeFilter getTypeFilters() {
        return this.typeFilters;
    }

    @Override
    public void onChangeTypeFilters() {
        this.saveChanges();
        this.markForUpdate();
    }

    public void startReshuffle(boolean confirmed) {
        if (!this.getProxy().isActive()) return;
        if (this.activeTask != null && this.activeTask.isRunning()) return;

        try {
            final IStorageGrid storageGrid = this.getProxy().getStorage();
            final Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
            for (final Entry<IAEStackType<?>, Boolean> entry : this.getTypeFilters().getImmutableFilters().entrySet()) {
                if (!entry.getValue()) continue;
                IMEMonitor<?> monitor = storageGrid.getMEMonitor(entry.getKey());
                if (monitor != null) {
                    monitors.put(entry.getKey(), monitor);
                    if (monitor instanceof NetworkMonitor<?>nm) nm.setLocked(true);
                }
            }

            BaseActionSource actionSource = new ReshuffleActionSource(this);
            final boolean voidProtection = this.cm.getSetting(Settings.VOID_PROTECTION) == YesNo.YES;
            this.lockedMonitors = monitors;
            this.activeTask = new ReshuffleTask(monitors, actionSource, this.getTypeFilters(), voidProtection);

            int totalItems = this.activeTask.initialize();
            if (!confirmed && totalItems >= 3000) {
                this.activeTask = null;
                unlockMonitors(monitors);
                this.reshuffleTotalItems = totalItems;
                return;
            }
            if (totalItems == 0) {
                this.activeTask = null;
                unlockMonitors(monitors);
                this.reshuffleFailed = true;
                this.reshuffleReport = null;
                this.markDirty();
                return;
            }

            this.reshuffleTotalItems = totalItems;
            this.reshuffleProgress = 0;
            this.reshuffleRunning = true;
            this.reshuffleFailed = false;
            this.reshuffleCancelled = false;
            this.reshuffleComplete = false;
            this.markForUpdate();

        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for reshuffle");
        }
    }

    public void cancelReshuffle() {
        if (this.activeTask != null && this.activeTask.isRunning()) {
            this.activeTask.cancel();
            unlockStorage();
            this.activeTask = null;
            this.reshuffleRunning = false;
            this.reshuffleCancelled = true;
            this.reshuffleFailed = false;
            this.markForUpdate();
        }
    }

    public void scanNetwork() {
        if (!this.getProxy().isActive()) {
            this.lastScanDuplicates = null;
            this.markDirty();
            return;
        }
        try {
            final IGrid grid = this.getProxy().getGrid();
            this.lastScanDuplicates = new PartitionScanTask();
            this.lastScanDuplicates.scanGrid(grid);
            this.markDirty();
        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for scan");
            this.lastScanDuplicates = null;
            this.markDirty();
        }
    }

    public PartitionScanTask getScanDuplicates() {
        return this.lastScanDuplicates;
    }

    private void unlockStorage() {
        if (this.lockedMonitors != null) {
            unlockMonitors(this.lockedMonitors);
            this.lockedMonitors = null;
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

    public boolean isReshuffleComplete() {
        return this.reshuffleComplete;
    }

    public boolean isReshuffleFailed() {
        return this.reshuffleFailed;
    }

    public boolean isReshuffleCancelled() {
        return this.reshuffleCancelled;
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

    public ReshuffleReport getReshuffleReport() {
        return this.reshuffleReport;
    }
}
