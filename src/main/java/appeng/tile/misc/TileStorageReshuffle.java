package appeng.tile.misc;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.Actionable;
import appeng.api.config.HealthSortOrder;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.YesNo;
import appeng.api.implementations.ITypeFilterProvider;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.AELog;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.helpers.ScanTask;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.AEStackTypeFilter;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;

public class TileStorageReshuffle extends AENetworkTile
        implements IConfigurableObject, IConfigManagerHost, ITypeFilterProvider {

    private ReshuffleTask activeTask = null;
    private Map<IAEStackType<?>, IMEMonitor<?>> lockedMonitors = null;

    private final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
    private final ConfigManager cm = new ConfigManager(this);

    private ReshuffleReport reshuffleReport = null;
    private ScanTask lastScan = null;

    private final IItemList<IAEStack<?>> cantInject = new IAEStackList();

    public TileStorageReshuffle() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(4.0);

        this.cm.registerSetting(Settings.INCLUDE_SUBNETS, YesNo.YES);
        this.cm.registerSetting(Settings.CELL_HEALTH_SORT, HealthSortOrder.FILL_PCT);
        this.cm.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        final boolean current = this.getProxy().isActive();
        if (this.isActive != current) {
            this.isActive = current;
            this.markForUpdate();
        }
    }

    private boolean isActive = false;

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
    public void writeToStream_TileStorageReshuffle(final ByteBuf data) {
        data.writeBoolean(this.activeTask != null && this.activeTask.isRunning());
    }

    private boolean wasRunning = false;

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileStorageReshuffle(final ByteBuf data) {
        final boolean wasRunning = this.wasRunning;
        this.wasRunning = data.readBoolean();
        return wasRunning != this.wasRunning;
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

    int count = 0;

    @TileEvent(TileEventType.TICK)
    public void Tick_TileStorageReshuffle() {
        if (this.activeTask != null) {
            this.reshuffleReport = this.activeTask.getReport();
            if (this.activeTask.isRunning()) this.activeTask.processNextBatch();
            else {
                unlockStorage();
                this.activeTask = null;
            }

            this.markDirty();
            this.markForUpdate();
        }

        if (!this.cantInject.isEmpty() && count >= 240) {
            this.returnPendingItems();
        } else count++;
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

    public void startReshuffle() {
        if (!this.getProxy().isActive()) return;
        if (this.activeTask != null) return;

        try {
            final IStorageGrid storageGrid = this.getProxy().getStorage();
            if (storageGrid == null) return;
            final Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
            for (final Entry<IAEStackType<?>, Boolean> entry : this.getTypeFilters().getImmutableFilters().entrySet()) {
                if (!entry.getValue()) continue;
                IMEMonitor<?> monitor = storageGrid.getMEMonitor(entry.getKey());
                if (monitor != null) {
                    monitors.put(entry.getKey(), monitor);
                    if (monitor instanceof NetworkMonitor<?>nm) nm.setLocked(true);
                }
            }

            final boolean includeSubnets = this.cm.getSetting(Settings.INCLUDE_SUBNETS) == YesNo.YES;
            this.lockedMonitors = monitors;

            this.activeTask = new ReshuffleTask(
                    this.typeFilters,
                    this.getProxy().getStorage(),
                    this.cantInject,
                    new ReshuffleActionSource(this),
                    includeSubnets);

            try {
                this.activeTask.initialize();
            } catch (Exception e) {
                this.activeTask = null;
                unlockStorage();
                this.markDirty();
            }

            this.markForUpdate();

        } catch (GridAccessException ignored) {}
    }

    public void cancelReshuffle() {
        if (this.activeTask != null) {
            this.activeTask.cancel();
            unlockStorage();
            this.activeTask = null;
            this.markDirty();
            this.markForUpdate();
        }
    }

    public void scanNetwork() {
        if (!this.getProxy().isActive()) {
            this.lastScan = null;
            this.markDirty();
            return;
        }
        try {
            final IGrid grid = this.getProxy().getGrid();
            this.lastScan = new ScanTask();
            this.lastScan.scanGrid(grid);
            this.markDirty();
        } catch (GridAccessException e) {
            AELog.warn(e, "Failed to access grid for scan");
            this.lastScan = null;
            this.markDirty();
        }
    }

    private void returnPendingItems() {
        try {
            final Iterator<IAEStack<?>> i = this.cantInject.iterator();
            while (i.hasNext()) {
                final IAEStack<?> aes = i.next();
                final IMEMonitor monitor = this.getProxy().getStorage().getMEMonitor(aes.getStackType());

                if (monitor != null) {
                    final IAEStack<?> res = monitor
                            .injectItems(aes, Actionable.MODULATE, new ReshuffleActionSource(this));
                    if (res != null) this.cantInject.add(res);

                    i.remove();
                }
            }
        } catch (Exception ignored) {}
    }

    public ScanTask getScan() {
        return this.lastScan;
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
        return this.activeTask != null && this.activeTask.isRunning();
    }

    public ReshuffleReport getReshuffleReport() {
        return this.reshuffleReport;
    }
}
