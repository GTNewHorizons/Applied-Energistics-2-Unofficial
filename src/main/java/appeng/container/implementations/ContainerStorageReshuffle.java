package appeng.container.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.HealthSortOrder;
import appeng.api.config.ReshufflePhase;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.SortDir;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ScanTask;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;

public class ContainerStorageReshuffle extends AEBaseContainer implements IConfigurableObject {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    private boolean scanMode = false;

    @GuiSync(1)
    private boolean healthMode = false;

    @GuiSync(2)
    public ReshuffleReport report = null;

    @GuiSync(3)
    public ScanTask scanData = null;

    @GuiSync(4)
    public AEStackTypeFilter typeFilters;

    @GuiSync(5)
    public YesNo includeSubnets;

    @GuiSync(6)
    public YesNo insertOrder;

    @GuiSync(7)
    public HealthSortOrder healthSortOrder;

    @GuiSync(8)
    public SortDir healthSortDir;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.typeFilters = new AEStackTypeFilter(this.tile.getTypeFilters());
    }

    public boolean isScanMode() {
        return this.scanMode;
    }

    public boolean isHealthMode() {
        return this.healthMode;
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
            this.verifyPermissions(SecurityPermissions.BUILD, true);
            this.report = this.tile.getReshuffleReport();
            this.scanData = this.tile.getScan();

            this.includeSubnets = this.tile.getIncludeSubnets();
            this.insertOrder = this.tile.getInsertOrder();
            this.healthSortOrder = this.tile.getCellHealthSort();
            this.healthSortDir = this.tile.getCellHeathSortDir();

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
                case "includeSubnets", "insertOrder", "healthSortOrder", "healthSortDir" -> gui.onSettingsUpdated();
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.tile.getConfigManager();
    }

    public void startReshuffle() {
        this.report = null;
        this.tile.startReshuffle();
    }

    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    public void performNetworkScan() {
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
}
