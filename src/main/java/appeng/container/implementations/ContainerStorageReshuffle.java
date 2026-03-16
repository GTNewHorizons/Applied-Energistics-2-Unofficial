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

import static net.minecraft.item.Item.itemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.HealthSortOrder;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.CellScanTask;
import appeng.tile.misc.TileStorageReshuffle;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class ContainerStorageReshuffle extends AEBaseContainer {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    private boolean voidProtection;

    @GuiSync(1)
    private boolean includeSubnets;

    @GuiSync(2)
    private boolean scanMode = false;

    @GuiSync(3)
    private boolean healthMode = false;

    @GuiSync(4)
    private int healthSortOrdinal = 0; // HealthSortOrder.FILL_PCT.ordinal()

    @GuiSync(5)
    private int healthSortDirOrdinal = 0; // SortDir.ASCENDING.ordinal()

    @GuiSync(6)
    private ReshuffleState reshuffleState = ReshuffleState.IDLE;

    @GuiSync(7)
    private ScanState scanState = ScanState.EMPTY;

    @GuiSync(8)
    private HealthState healthState = HealthState.EMPTY;

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.voidProtection = this.tile.isVoidProtection();
        this.includeSubnets = this.tile.isIncludeSubnets();
    }

    public boolean isVoidProtection() {
        return voidProtection;
    }

    public boolean isIncludeSubnets() {
        return includeSubnets;
    }

    public boolean isScanMode() {
        return scanMode;
    }

    public boolean isHealthMode() {
        return healthMode;
    }

    public HealthSortOrder getHealthSortOrder() {
        final HealthSortOrder[] vals = HealthSortOrder.values();
        return (healthSortOrdinal >= 0 && healthSortOrdinal < vals.length) ? vals[healthSortOrdinal]
                : HealthSortOrder.FILL_PCT;
    }

    public SortDir getHealthSortDir() {
        return healthSortDirOrdinal == 1 ? SortDir.DESCENDING : SortDir.ASCENDING;
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

    public void setHealthSort(final String value) {
        try {
            this.healthSortOrdinal = HealthSortOrder.valueOf(value).ordinal();
        } catch (IllegalArgumentException ignored) {}
    }

    public void setHealthSortDir(final String value) {
        try {
            this.healthSortDirOrdinal = SortDir.valueOf(value).ordinal();
        } catch (IllegalArgumentException ignored) {}
    }

    public ReshuffleState getReshuffleState() {
        return reshuffleState;
    }

    public ScanState getScanState() {
        return scanState;
    }

    public HealthState getHealthState() {
        return healthState;
    }

    public Reference2BooleanMap<IAEStackType<?>> getTypeFilters() {
        return this.tile.getTypeFilter(this.getPlayerInv().player);
    }

    public void toggleTypeFilter(final String typeId) {
        final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
        if (type == null) return;
        final Reference2BooleanMap<IAEStackType<?>> map = this.tile.getTypeFilter(this.getPlayerInv().player);
        map.put(type, !map.getBoolean(type));
        this.tile.saveTypeFilter();
    }

    public void toggleVoidProtection() {
        this.tile.getConfigManager()
                .putSetting(Settings.VOID_PROTECTION, this.tile.isVoidProtection() ? YesNo.NO : YesNo.YES);
    }

    public void toggleIncludeSubnets() {
        this.tile.getConfigManager()
                .putSetting(Settings.INCLUDE_SUBNETS, this.tile.isIncludeSubnets() ? YesNo.NO : YesNo.YES);
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        this.voidProtection = this.tile.isVoidProtection();
        this.includeSubnets = this.tile.isIncludeSubnets();

        this.reshuffleState = new ReshuffleState(
                this.tile.isReshuffleRunning(),
                this.tile.isReshuffleFailed(),
                this.tile.isReshuffleCancelled(),
                this.tile.isReshuffleComplete(),
                this.tile.isReshuffleExtracting(),
                this.tile.getReshuffleTotalItems(),
                this.tile.getReshuffleProcessedItems(),
                this.tile.getReshuffleProgress(),
                this.tile.getReshufflePhaseProcessed(),
                this.tile.getReshufflePhaseTotal(),
                this.tile.getReshuffleTypeCount(),
                this.tile.getReshuffleReport());

        this.scanState = new ScanState(encodeScanData(this.tile.getScanDuplicates()));
        this.healthState = new HealthState(encodeHealthData(this.tile.getHealthCells()));

        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
            switch (field) {
                case "reshuffleState" -> gui.onReportUpdated();
                case "scanState" -> gui.onScanUpdated();
                case "healthState" -> gui.onHealthUpdated();
            }
        }
    }

    public void startReshuffle(final EntityPlayer player) {
        this.tile.startReshuffle(player);
    }

    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    public void performNetworkScan() {
        if (this.reshuffleState.running) return;
        if (!this.hasAccess(SecurityPermissions.BUILD, false)) return;
        this.tile.scanNetwork();
    }

    public String getHealthData() {
        return this.healthState.healthData;
    }

    public List<String> getReportLines() {
        return this.reshuffleState.getReportLines();
    }

    public String getScanData() {
        return this.scanState.scanData;
    }

    private static String encodeScanData(final Map<String, List<CellScanTask.CellRecord>> duplicates) {
        final List<String> lines = new ArrayList<>();
        for (final List<CellScanTask.CellRecord> cells : duplicates.values()) {
            if (cells.isEmpty()) continue;
            final CellScanTask.CellRecord first = cells.get(0);
            if (first.partitionedItemStacks.isEmpty()) continue;

            final ItemStack repItem = first.partitionedItemStacks.get(0);
            final String repId = itemRegistry.getNameForObject(repItem.getItem());
            if (repId == null) continue;

            final StringBuilder sb = new StringBuilder();
            sb.append(repId).append('@').append(repItem.getItemDamage()).append('@').append(cells.size());
            for (final CellScanTask.CellRecord cell : cells) {
                sb.append('@').append(cell.x).append(',').append(cell.y).append(',').append(cell.z).append(',')
                        .append(cell.dim).append(',').append(cell.slot).append(',').append((int) cell.typesUsed)
                        .append('|').append(cell.cellDisplayName);
            }
            lines.add(sb.toString());
        }
        return String.join("\n", lines);
    }

    private static String encodeHealthData(final List<CellScanTask.CellRecord> cells) {
        if (cells == null || cells.isEmpty()) return "";
        final List<String> lines = new ArrayList<>();
        for (final CellScanTask.CellRecord cell : cells) {
            final String cellItemId = cell.cellItemId != null ? cell.cellItemId : "";
            lines.add(
                    cellItemId + '\t'
                            + cell.cellMeta
                            + '\t'
                            + cell.cellDisplayName
                            + '\t'
                            + cell.x
                            + '\t'
                            + cell.y
                            + '\t'
                            + cell.z
                            + '\t'
                            + cell.dim
                            + '\t'
                            + cell.slot
                            + '\t'
                            + (long) cell.bytesUsed
                            + '\t'
                            + (long) cell.bytesTotal
                            + '\t'
                            + (int) cell.typesUsed
                            + '\t'
                            + (int) cell.typesTotal
                            + '\t'
                            + encodeTopItems(cell.topStoredItems)
                            + '\t'
                            + cell.stackTypeId);
        }
        return String.join("\n", lines);
    }

    private static String encodeTopItems(final List<CellScanTask.StoredItemEntry> topItems) {
        if (topItems == null || topItems.isEmpty()) return "";
        final StringBuilder sb = new StringBuilder();
        for (final CellScanTask.StoredItemEntry entry : topItems) {
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.displayName.replace(';', ',')).append(';').append(entry.count);
        }
        return sb.toString();
    }
}
