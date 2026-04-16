package appeng.container.implementations;

import java.io.IOException;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.HealthSortOrder;
import appeng.api.config.ReshufflePhase;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ScanTask;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.AEStackTypeFilter;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

public class ContainerStorageReshuffle extends AEBaseContainer implements IConfigManagerHost, IConfigurableObject {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    public boolean reshuffleRunning = false;

    @GuiSync(1)
    private boolean scanMode = false;

    @GuiSync(2)
    private boolean healthMode = false;

    @GuiSync(3)
    public ReshuffleReport report = null;

    @GuiSync(4)
    public ScanTask scanData = null;

    @GuiSync(5)
    public AEStackTypeFilter typeFilters;

    private IConfigManager serverCM;
    private final IConfigManager clientCM;
    private IConfigManagerHost gui;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.typeFilters = new AEStackTypeFilter(this.tile.getTypeFilters());

        if (Platform.isServer()) {
            this.serverCM = this.tile.getConfigManager();
        }
        this.clientCM = new ConfigManager(this);
        this.clientCM.registerSetting(Settings.INCLUDE_SUBNETS, YesNo.YES);
        this.clientCM.registerSetting(Settings.CELL_HEALTH_SORT, HealthSortOrder.FILL_PCT);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
    }

    public boolean isScanMode() {
        return scanMode;
    }

    public boolean isHealthMode() {
        return healthMode;
    }

    public HealthSortOrder getHealthSortOrder() {
        return (HealthSortOrder) this.clientCM.getSetting(Settings.CELL_HEALTH_SORT);
    }

    public SortDir getHealthSortDirOrder() {
        return (SortDir) this.clientCM.getSetting(Settings.SORT_DIRECTION);
    }

    public void setView(final String viewName) {
        switch (viewName) {
            case "reshuffle" -> {
                this.scanMode = false;
                this.healthMode = false;
            }
            case "scan" -> {
                this.scanMode = true;
                this.healthMode = false;
            }
            case "health" -> {
                this.scanMode = false;
                this.healthMode = true;
            }
        }
    }

    public AEStackTypeFilter getTypeFilters() {
        return this.typeFilters;
    }

    public void toggleTypeFilter(final String typeId) {
        if (typeId == null || typeId.isEmpty()) return;
        final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
        if (type == null) return;

        this.tile.getTypeFilters().toggle(type);
        this.typeFilters = new AEStackTypeFilter(this.tile.getTypeFilters());
        this.tile.onChangeTypeFilters();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            for (final Settings set : this.serverCM.getSettings()) {
                final Enum<?> sideLocal = this.serverCM.getSetting(set);
                final Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    this.clientCM.putSetting(set, sideLocal);
                    for (final Object crafter : this.crafters) {
                        try {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig(set.name(), sideLocal.name()),
                                    (EntityPlayerMP) crafter);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }

            this.report = this.tile.getReshuffleReport();
            this.scanData = this.tile.getScan();

            super.detectAndSendChanges();
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
            switch (field) {
                case "typeFilters" -> gui.onUpdateTypeFilters();
                case "report" -> gui.onReportUpdated();
                case "scanData" -> gui.onScanUpdated();
            }
        }
    }

    public void startReshuffle() {
        this.report = null;
        this.tile.startReshuffle();
    }

    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    public void performNetworkScan() {
        if (this.reshuffleRunning) return;
        if (!this.hasAccess(SecurityPermissions.BUILD, false)) return;
        this.tile.scanNetwork();
    }

    public ReshuffleReport getReshuffleReport() {
        return report;
    }

    public boolean reportRunning() {
        return this.report != null
                && (this.report.phase == ReshufflePhase.EXTRACTION || this.report.phase == ReshufflePhase.INJECTION);
    }

    public ScanTask getScanData() {
        return this.scanData;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    @Override
    @SuppressWarnings({ "rawtypes" })
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.gui != null) this.gui.updateSetting(manager, settingName, newValue);
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) return this.serverCM;
        return this.clientCM;
    }
}
