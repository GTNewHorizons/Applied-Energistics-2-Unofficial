package appeng.container.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.PartitionScanTask;
import appeng.helpers.ReshuffleReport;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.AEStackTypeFilter;

public class ContainerStorageReshuffle extends AEBaseContainer {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    public boolean voidProtection;

    @GuiSync(1)
    public boolean reshuffleRunning = false;

    @GuiSync(2)
    public int reshuffleTotalItems = 0;

    @GuiSync(3)
    public ReshuffleReport report = null;

    @GuiSync(4)
    public int reshuffleProgress = 0;

    @GuiSync(5)
    public int reshuffleProcessedItems = 0;

    @GuiSync(6)
    public boolean scanMode = false;

    @GuiSync(7)
    public boolean reshuffleFailed = false;

    @GuiSync(8)
    public boolean reshuffleCancelled = false;

    @GuiSync(9)
    public boolean reshuffleComplete = false;

    @GuiSync(10)
    public PartitionScanTask scanData = null;

    @GuiSync(11)
    public AEStackTypeFilter typeFilters;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.typeFilters = new AEStackTypeFilter(this.tile.getTypeFilters());
        this.voidProtection = this.tile.isVoidProtection();
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

    public void toggleVoidProtection() {
        this.tile.getConfigManager()
                .putSetting(Settings.VOID_PROTECTION, this.tile.isVoidProtection() ? YesNo.NO : YesNo.YES);
    }

    public void setScanMode(final boolean scan) {
        this.scanMode = scan;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        this.voidProtection = this.tile.isVoidProtection();

        this.reshuffleRunning = this.tile.isReshuffleRunning();
        this.reshuffleFailed = this.tile.isReshuffleFailed();
        this.reshuffleCancelled = this.tile.isReshuffleCancelled();
        this.reshuffleComplete = this.tile.isReshuffleComplete();
        this.reshuffleTotalItems = this.tile.getReshuffleTotalItems();
        this.reshuffleProgress = this.tile.getReshuffleProgress();
        this.reshuffleProcessedItems = this.tile.getReshuffleProcessedItems();
        this.report = this.tile.getReshuffleReport();
        this.scanData = this.tile.getScanDuplicates();

        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
            switch (field) {
                case "report" -> gui.onReportUpdated();
                case "scanData" -> gui.onScanUpdated();
                case "typeFilters" -> gui.onUpdateTypeFilters();
            }
        }
    }

    public void startReshuffle(boolean confirmed) {
        this.report = null;
        this.tile.startReshuffle(confirmed);
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

    public PartitionScanTask getScanData() {
        return this.scanData;
    }
}
