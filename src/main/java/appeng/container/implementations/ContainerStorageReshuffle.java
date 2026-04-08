package appeng.container.implementations;

import static net.minecraft.item.Item.itemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.CellScanTask;
import appeng.helpers.ReshuffleReport;
import appeng.tile.misc.TileStorageReshuffle;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

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
    public CellScanTask scanData = null;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.voidProtection = this.tile.isVoidProtection();
    }

    public Reference2BooleanMap<IAEStackType<?>> getTypeFilters() {
        return this.tile.getTypeFilters().getImmutableFilters();
    }

    public void toggleTypeFilter(final String typeId) {
        final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
        if (type == null) return;
        final Reference2BooleanMap<IAEStackType<?>> map = this.getTypeFilters();
        map.put(type, !map.getBoolean(type));
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

        final ReshuffleReport current = this.tile.getReshuffleReport();
        if (current != this.report) {
            this.report = current;
        }

        final CellScanTask currentScan = this.tile.getScanDuplicates();
        if (currentScan != this.scanData) {
            this.scanData = currentScan;
        }

        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
            if (field.equals("report")) {
                gui.onReportUpdated();
            } else if (field.equals("scanData")) {
                gui.onScanUpdated();
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

    private static String encodeScanData(final Map<String, List<CellScanTask.CellRecord>> duplicates) {
        final List<String> lines = new ArrayList<>();
        for (final List<CellScanTask.CellRecord> cells : duplicates.values()) {
            if (cells.isEmpty()) continue;
            final CellScanTask.CellRecord first = cells.get(0);
            // if (first.partitionedItemStacks.isEmpty()) continue;

            final ItemStack repItem = null; // first.partitionedItemStacks.get(0);
            final String repId = itemRegistry.getNameForObject(repItem.getItem());
            if (repId == null) continue;

            final StringBuilder sb = new StringBuilder();
            sb.append(repId).append('@').append(repItem.getItemDamage()).append('@').append(cells.size());

            for (final CellScanTask.CellRecord cell : cells) {
                sb.append('@').append(cell.x).append(',').append(cell.y).append(',').append(cell.z).append(',')
                        .append(cell.dim).append(',').append(cell.slot).append(',').append(cell.typesUsed).append('|')
                        .append(cell.cellDisplayName);
            }
            lines.add(sb.toString());
        }
        return String.join("\n", lines);
    }

    public List<String> getReportLines() {
        if (this.report == null) return new ArrayList<>();
        return report.generateReportLines();
    }

    public CellScanTask getScanData() {
        return this.scanData;
    }
}
