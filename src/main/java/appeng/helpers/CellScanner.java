/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellCacheRegistry;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

public final class CellScanner {

    private CellScanner() {}

    private static final String GUI_HEADER = "§1";
    private static final String GUI_LABEL = "§1";
    private static final String GUI_VALUE = "§8";
    private static final String GUI_SECONDARY = "§8";
    private static final String GUI_WARN = "§6";
    private static final String GUI_ERROR = "§4";
    private static final String GUI_GOOD = "§2";
    private static final String GUI_CHANNEL_ITEM = "§8";
    private static final String GUI_CHANNEL_FLUID = "§1";
    private static final String GUI_CHANNEL_ESSENTIA = "§5";

    private static final String TT_HEADER = "§b";
    private static final String TT_LABEL = "§b";
    private static final String TT_VALUE = "§f";
    private static final String TT_SECONDARY = "§7";
    private static final String TT_WARN = "§e";
    private static final String TT_ERROR = "§c";
    private static final String TT_GOOD = "§a"
    private static final String TT_DIVIDER = "§7";s
    private static final String TT_CHANNEL_ITEM = "§f";
    private static final String TT_CHANNEL_FLUID = "§b";
    private static final String TT_CHANNEL_ESSENTIA = "§d";
    
    public static final class CellKey {

        public final String itemId;
        public final int meta;
        public final ICellCacheRegistry.TYPE cellType;
        public final String displayName;

        public CellKey(String itemId, int meta, ICellCacheRegistry.TYPE cellType, String displayName) {
            this.itemId = itemId;
            this.meta = meta;
            this.cellType = cellType;
            this.displayName = displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CellKey)) return false;
            CellKey key = (CellKey) o;
            return meta == key.meta && Objects.equals(itemId, key.itemId) && cellType == key.cellType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, meta, cellType);
        }

        @Override
        public String toString() {
            return displayName + " (" + cellType + ")";
        }
    }

    public static final class CellRecord {

        public final String deviceType;
        public final String deviceId;
        public final int slot;
        public final String cellItemId;
        public final int cellMeta;
        public final String cellDisplayName;
        public final ICellCacheRegistry.TYPE cellType;
        public final double bytesTotal;
        public final double bytesUsed;
        public final double typesTotal;
        public final double typesUsed;
        public final boolean isPartitioned;
        public final List<String> partitionedItems;

        public CellRecord(String deviceType, String deviceId, int slot, String cellItemId, int cellMeta,
                String cellDisplayName, ICellCacheRegistry.TYPE cellType, double bytesTotal, double bytesUsed,
                double typesTotal, double typesUsed, boolean isPartitioned, List<String> partitionedItems) {
            this.deviceType = deviceType;
            this.deviceId = deviceId;
            this.slot = slot;
            this.cellItemId = cellItemId;
            this.cellMeta = cellMeta;
            this.cellDisplayName = cellDisplayName;
            this.cellType = cellType;
            this.bytesTotal = bytesTotal;
            this.bytesUsed = bytesUsed;
            this.typesTotal = typesTotal;
            this.typesUsed = typesUsed;
            this.isPartitioned = isPartitioned;
            this.partitionedItems = partitionedItems != null ? partitionedItems : new ArrayList<>();
        }

        public double bytesFree() {
            return bytesTotal - bytesUsed;
        }

        public double typesFree() {
            return typesTotal - typesUsed;
        }

        public double bytesUtilPct() {
            return bytesTotal > 0 ? bytesUsed / bytesTotal : 0;
        }

        public double typesUtilPct() {
            return typesTotal > 0 ? typesUsed / typesTotal : 0;
        }

        public boolean typeLocked() {
            return typesTotal > 0 && typesUsed >= typesTotal && bytesFree() > 0;
        }

        public double typeLockedBytes() {
            return typeLocked() ? bytesFree() : 0;
        }

        public boolean byteLocked() {
            return bytesTotal > 0 && bytesUsed >= bytesTotal && typesFree() > 0;
        }

        public boolean isEmpty() {
            return bytesUsed == 0 && typesUsed == 0;
        }

        public double avgBytesPerType() {
            return typesUsed > 0 ? bytesUsed / typesUsed : 0;
        }

        public boolean isSingularityCell() {
            return typesTotal == 1;
        }
    }

    public static final class Summary {

        public int numCells;
        public int numEmpty;
        public int numTypeLocked;
        public int numByteLocked;

        public double sumBytesTotal;
        public double sumBytesUsed;
        public double sumBytesFree;

        public double sumTypesTotal;
        public double sumTypesUsed;
        public double sumTypesFree;

        public double sumTypeLockedBytes;

        public double weightedBytesUtil;
        public double weightedTypesUtil;

        public double bytesP10;
        public double bytesP25;
        public double bytesP50;
        public double bytesP75;
        public double bytesP90;
        public double bytesP95;
        public double bytesP99;
        public double bytesMax;

        public double typesP10;
        public double typesP25;
        public double typesP50;
        public double typesP75;
        public double typesP90;
        public double typesP95;
        public double typesP99;
        public double typesMax;
    }

    public static List<CellRecord> scanGrid(IGrid grid) {
        List<CellRecord> records = new ArrayList<>();

        try {
            var driveSet = grid.getMachines(TileDrive.class);
            AELog.info("CellScanner: Found " + driveSet.size() + " ME Drives in grid");

            int cellsFound = 0;

            for (Object obj : driveSet) {
                try {
                    TileDrive drive = (obj instanceof IGridNode) ? (TileDrive) ((IGridNode) obj).getMachine()
                            : (TileDrive) obj;

                    int dimension = drive.getWorldObj() != null ? drive.getWorldObj().provider.dimensionId : 0;
                    String deviceId = String
                            .format("Drive@%d,%d,%d (Dim %d)", drive.xCoord, drive.yCoord, drive.zCoord, dimension);
                    IInventory inv = drive.getInternalInventory();

                    for (int slot = 0; slot < 10; slot++) {
                        ItemStack cellStack = inv.getStackInSlot(slot);
                        if (cellStack == null) continue;

                        appeng.api.storage.ICellHandler cellHandler = appeng.api.AEApi.instance().registries().cell()
                                .getHandler(cellStack);
                        if (cellHandler == null) continue;

                        for (appeng.api.storage.data.IAEStackType<?> stackType : appeng.api.storage.data.AEStackTypeRegistry
                                .getAllTypes()) {
                            try {
                                appeng.api.storage.IMEInventoryHandler<?> cellInv = cellHandler
                                        .getCellInventory(cellStack, drive, stackType);
                                if (cellInv == null) continue;

                                if (cellInv instanceof ICellCacheRegistry reg && reg.canGetInv()) {
                                    String displayName = cellStack.getDisplayName();

                                    boolean isPartitioned = false;
                                    List<String> partitionedItems = new ArrayList<>();

                                    if (cellStack.getItem() instanceof appeng.api.storage.ICellWorkbenchItem) {
                                        appeng.api.storage.ICellWorkbenchItem cellItem = (appeng.api.storage.ICellWorkbenchItem) cellStack
                                                .getItem();
                                        net.minecraft.inventory.IInventory configInv = cellItem
                                                .getConfigInventory(cellStack);

                                        if (configInv != null) {
                                            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                                                ItemStack filterStack = configInv.getStackInSlot(i);
                                                if (filterStack != null) {
                                                    isPartitioned = true;
                                                    partitionedItems.add(filterStack.getDisplayName());
                                                }
                                            }
                                        }
                                    }

                                    records.add(
                                            new CellRecord(
                                                    "DRIVE",
                                                    deviceId,
                                                    slot,
                                                    Item.itemRegistry.getNameForObject(cellStack.getItem()),
                                                    cellStack.getItemDamage(),
                                                    displayName,
                                                    reg.getCellType(),
                                                    reg.getTotalBytes(),
                                                    reg.getUsedBytes(),
                                                    reg.getTotalTypes(),
                                                    reg.getUsedTypes(),
                                                    isPartitioned,
                                                    partitionedItems));
                                    cellsFound++;
                                    break;
                                }
                            } catch (Exception e) {
                                // This stack type doesn't match, try next
                            }
                        }
                    }
                } catch (Exception e) {
                    AELog.warn(e, "Error scanning individual drive");
                }
            }

            AELog.info("CellScanner: Found " + cellsFound + " cells in drives");

            var chestSet = grid.getMachines(TileChest.class);
            AELog.info("CellScanner: Found " + chestSet.size() + " ME Chests in grid");

            for (Object obj : chestSet) {
                try {
                    TileChest chest = (obj instanceof IGridNode) ? (TileChest) ((IGridNode) obj).getMachine()
                            : (TileChest) obj;

                    int dimension = chest.getWorldObj() != null ? chest.getWorldObj().provider.dimensionId : 0;
                    String deviceId = String
                            .format("Chest@%d,%d,%d (Dim %d)", chest.xCoord, chest.yCoord, chest.zCoord, dimension);
                    IInventory inv = chest.getInternalInventory();

                    ItemStack cellStack = inv.getStackInSlot(1);
                    if (cellStack == null) continue;

                    appeng.api.storage.ICellHandler cellHandler = appeng.api.AEApi.instance().registries().cell()
                            .getHandler(cellStack);
                    if (cellHandler == null) continue;

                    for (appeng.api.storage.data.IAEStackType<?> stackType : appeng.api.storage.data.AEStackTypeRegistry
                            .getAllTypes()) {
                        try {
                            appeng.api.storage.IMEInventoryHandler<?> cellInv = cellHandler
                                    .getCellInventory(cellStack, chest, stackType);
                            if (cellInv == null) continue;

                            if (cellInv instanceof ICellCacheRegistry reg && reg.canGetInv()) {
                                String displayName = cellStack.getDisplayName();

                                boolean isPartitioned = false;
                                List<String> partitionedItems = new ArrayList<>();

                                if (cellStack.getItem() instanceof appeng.api.storage.ICellWorkbenchItem) {
                                    appeng.api.storage.ICellWorkbenchItem cellItem = (appeng.api.storage.ICellWorkbenchItem) cellStack
                                            .getItem();
                                    net.minecraft.inventory.IInventory configInv = cellItem
                                            .getConfigInventory(cellStack);

                                    if (configInv != null) {
                                        for (int i = 0; i < configInv.getSizeInventory(); i++) {
                                            ItemStack filterStack = configInv.getStackInSlot(i);
                                            if (filterStack != null) {
                                                isPartitioned = true;
                                                partitionedItems.add(filterStack.getDisplayName());
                                            }
                                        }
                                    }
                                }

                                records.add(
                                        new CellRecord(
                                                "CHEST",
                                                deviceId,
                                                1,
                                                Item.itemRegistry.getNameForObject(cellStack.getItem()),
                                                cellStack.getItemDamage(),
                                                displayName,
                                                reg.getCellType(),
                                                reg.getTotalBytes(),
                                                reg.getUsedBytes(),
                                                reg.getTotalTypes(),
                                                reg.getUsedTypes(),
                                                isPartitioned,
                                                partitionedItems));
                                cellsFound++;
                                break;
                            }
                        } catch (Exception e) {
                            // This stack type not supported, try next
                        }
                    }
                } catch (Exception e) {
                    AELog.warn(e, "Error scanning individual chest");
                }
            }

            AELog.info("CellScanner: Total cells found: " + records.size());

        } catch (Exception e) {
            AELog.error(e, "Error scanning grid cells");
        }

        return records;
    }

    public static Summary summarize(List<CellRecord> cells) {
        Summary s = new Summary();

        if (cells.isEmpty()) {
            return s;
        }

        s.numCells = cells.size();

        for (CellRecord c : cells) {
            s.sumBytesTotal += c.bytesTotal;
            s.sumBytesUsed += c.bytesUsed;
            s.sumBytesFree += c.bytesFree();

            s.sumTypesTotal += c.typesTotal;
            s.sumTypesUsed += c.typesUsed;
            s.sumTypesFree += c.typesFree();

            if (c.isEmpty()) s.numEmpty++;
            if (c.typeLocked()) {
                s.numTypeLocked++;
                s.sumTypeLockedBytes += c.typeLockedBytes();
            }
            if (c.byteLocked()) s.numByteLocked++;
        }

        s.weightedBytesUtil = s.sumBytesTotal > 0 ? s.sumBytesUsed / s.sumBytesTotal : 0;
        s.weightedTypesUtil = s.sumTypesTotal > 0 ? s.sumTypesUsed / s.sumTypesTotal : 0;

        double[] bu = cells.stream().mapToDouble(CellRecord::bytesUtilPct).sorted().toArray();
        double[] tu = cells.stream().mapToDouble(CellRecord::typesUtilPct).sorted().toArray();

        s.bytesP10 = percentile(bu, 0.10);
        s.bytesP25 = percentile(bu, 0.25);
        s.bytesP50 = percentile(bu, 0.50);
        s.bytesP75 = percentile(bu, 0.75);
        s.bytesP90 = percentile(bu, 0.90);
        s.bytesP95 = percentile(bu, 0.95);
        s.bytesP99 = percentile(bu, 0.99);
        s.bytesMax = bu.length > 0 ? bu[bu.length - 1] : 0;

        s.typesP10 = percentile(tu, 0.10);
        s.typesP25 = percentile(tu, 0.25);
        s.typesP50 = percentile(tu, 0.50);
        s.typesP75 = percentile(tu, 0.75);
        s.typesP90 = percentile(tu, 0.90);
        s.typesP95 = percentile(tu, 0.95);
        s.typesP99 = percentile(tu, 0.99);
        s.typesMax = tu.length > 0 ? tu[tu.length - 1] : 0;

        return s;
    }

    public static Map<CellKey, List<CellRecord>> groupByType(List<CellRecord> cells) {
        return cells.stream().collect(
                Collectors.groupingBy(c -> new CellKey(c.cellItemId, c.cellMeta, c.cellType, c.cellDisplayName)));
    }

    public static List<CellRecord> filterByType(List<CellRecord> cells, ICellCacheRegistry.TYPE type) {
        return cells.stream().filter(c -> c.cellType == type).collect(Collectors.toList());
    }

    public static List<CellRecord> filterSingularityCells(List<CellRecord> cells) {
        return cells.stream().filter(CellRecord::isSingularityCell).collect(Collectors.toList());
    }

    public static List<CellRecord> filterNonSingularityCells(List<CellRecord> cells) {
        return cells.stream().filter(c -> !c.isSingularityCell()).collect(Collectors.toList());
    }

    public static Map<String, List<CellRecord>> findDuplicatePartitionedCells(List<CellRecord> cells) {
        Map<String, List<CellRecord>> duplicates = new java.util.HashMap<>();

        for (CellRecord cell : cells) {
            if (!cell.isPartitioned || cell.partitionedItems.isEmpty()) continue;

            List<String> sortedItems = new ArrayList<>(cell.partitionedItems);
            java.util.Collections.sort(sortedItems);
            String signature = String.join("|", sortedItems) + "|" + cell.cellType.name();

            duplicates.computeIfAbsent(signature, k -> new ArrayList<>()).add(cell);
        }

        Map<String, List<CellRecord>> result = new java.util.HashMap<>();
        for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
            if (entry.getValue().size() >= 2) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    public static List<CellRecord> getTopFragmented(List<CellRecord> cells, int limit) {
        return cells.stream().filter(CellRecord::typeLocked)
                .sorted((a, b) -> Double.compare(b.typeLockedBytes(), a.typeLockedBytes())).limit(limit)
                .collect(Collectors.toList());
    }

    public static List<CellRecord> getLowestUtilization(List<CellRecord> cells, int limit) {
        return cells.stream().filter(c -> !c.isEmpty()).sorted(Comparator.comparingDouble(CellRecord::bytesUtilPct))
                .limit(limit).collect(Collectors.toList());
    }

    public static List<String> generateReport(IGrid grid) {
        List<String> lines = new ArrayList<>();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        List<CellRecord> allCells = scanGrid(grid);
        Summary overall = summarize(allCells);

        List<CellRecord> regularCells = filterNonSingularityCells(allCells);
        List<CellRecord> singularityCells = filterSingularityCells(allCells);

        lines.add(GUI_HEADER + "═══════ " + GuiText.StorageScan.getLocal() + " ═══════");
        lines.add("");

        lines.add(GUI_HEADER + "── " + GuiText.StorageScanSummary.getLocal() + " ──");
        lines.add(
                GUI_LABEL + GuiText.StorageScanCells.getLocal()
                        + ": "
                        + GUI_VALUE
                        + nf.format(overall.numCells)
                        + GUI_SECONDARY
                        + " ("
                        + GUI_VALUE
                        + nf.format(overall.numEmpty)
                        + GUI_SECONDARY
                        + " "
                        + GuiText.StorageScanEmpty.getLocal()
                        + ")");
        lines.add("");

        List<CellRecord> itemCells = filterByType(allCells, ICellCacheRegistry.TYPE.ITEM);
        List<CellRecord> fluidCells = filterByType(allCells, ICellCacheRegistry.TYPE.FLUID);
        List<CellRecord> essentiaCells = filterByType(allCells, ICellCacheRegistry.TYPE.ESSENTIA);

        if (!itemCells.isEmpty()) {
            Summary itemSummary = summarize(itemCells);
            lines.add(
                    GUI_LABEL + GuiText.Items.getLocal()
                            + ": "
                            + GUI_VALUE
                            + nf.format(itemCells.size())
                            + GUI_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + GUI_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    itemSummary.weightedBytesUtil * 100));
        }

        if (!fluidCells.isEmpty()) {
            Summary fluidSummary = summarize(fluidCells);
            lines.add(
                    GUI_LABEL + GuiText.Fluids.getLocal()
                            + ": "
                            + GUI_VALUE
                            + nf.format(fluidCells.size())
                            + GUI_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + GUI_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    fluidSummary.weightedBytesUtil * 100));
        }

        if (!essentiaCells.isEmpty()) {
            Summary essentiaSummary = summarize(essentiaCells);
            lines.add(
                    GUI_LABEL + GuiText.Essentias.getLocal()
                            + ": "
                            + GUI_VALUE
                            + nf.format(essentiaCells.size())
                            + GUI_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + GUI_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    essentiaSummary.weightedBytesUtil * 100));
        }

        lines.add("");

        lines.add(GUI_HEADER + "── " + GuiText.StorageScanUtilization.getLocal() + " ──");

        Summary regularSummary = summarize(regularCells);
        Summary singularitySummary = summarize(singularityCells);

        lines.add(
                GUI_LABEL + GuiText.StorageScanBytesAll.getLocal()
                        + ": "
                        + GUI_VALUE
                        + formatBytes(overall.sumBytesUsed)
                        + GUI_SECONDARY
                        + " / "
                        + GUI_VALUE
                        + formatBytes(overall.sumBytesTotal));
        lines.add(
                GUI_SECONDARY + "  "
                        + GuiText.StorageScanUtil.getLocal()
                        + ": "
                        + GUI_WARN
                        + String.format("%.1f%%", overall.weightedBytesUtil * 100)
                        + GUI_SECONDARY
                        + " ("
                        + GuiText.StorageScanMedian.getLocal()
                        + ": "
                        + GUI_WARN
                        + String.format("%.1f%%)", overall.bytesP50 * 100));

        if (!regularCells.isEmpty()) {
            lines.add(
                    GUI_LABEL + GuiText.StorageScanBytesExclSing.getLocal()
                            + ": "
                            + GUI_VALUE
                            + formatBytes(regularSummary.sumBytesUsed)
                            + GUI_SECONDARY
                            + " / "
                            + GUI_VALUE
                            + formatBytes(regularSummary.sumBytesTotal));
            lines.add(
                    GUI_SECONDARY + "  "
                            + GuiText.StorageScanUtil.getLocal()
                            + ": "
                            + GUI_WARN
                            + String.format("%.1f%%", regularSummary.weightedBytesUtil * 100)
                            + GUI_SECONDARY
                            + " ("
                            + GuiText.StorageScanMedian.getLocal()
                            + ": "
                            + GUI_WARN
                            + String.format("%.1f%%)", regularSummary.bytesP50 * 100));
        }

        lines.add("");

        lines.add(
                GUI_LABEL + GuiText.Types.getLocal()
                        + ": "
                        + GUI_VALUE
                        + nf.format((long) overall.sumTypesUsed)
                        + GUI_SECONDARY
                        + " / "
                        + GUI_VALUE
                        + nf.format((long) overall.sumTypesTotal));
        lines.add(
                GUI_SECONDARY + "  "
                        + GuiText.StorageScanUtil.getLocal()
                        + ": "
                        + GUI_WARN
                        + String.format("%.1f%%", overall.weightedTypesUtil * 100)
                        + GUI_SECONDARY
                        + " ("
                        + GuiText.StorageScanMedian.getLocal()
                        + ": "
                        + GUI_WARN
                        + String.format("%.1f%%)", overall.typesP50 * 100));

        lines.add("");

        List<CellRecord> framedRegularCells = regularCells.stream().filter(CellRecord::typeLocked)
                .collect(Collectors.toList());
        if (!framedRegularCells.isEmpty()) {
            long totalWasted = framedRegularCells.stream().mapToLong(c -> (long) c.typeLockedBytes()).sum();
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanFragmentation.getLocal() + " ──");
            lines.add(
                    GUI_LABEL + GuiText.StorageScanLocked.getLocal()
                            + ": "
                            + GUI_ERROR
                            + nf.format(framedRegularCells.size())
                            + GUI_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal());
            lines.add(GUI_LABEL + GuiText.StorageScanWasted.getLocal() + ": " + GUI_ERROR + formatBytes(totalWasted));
            lines.add(GUI_SECONDARY + GuiText.StorageScanSingularityExcluded.getLocal());
            lines.add("");
        }

        Map<CellKey, List<CellRecord>> byType = groupByType(allCells);
        if (!byType.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanCellTypes.getLocal() + " ──");

            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();

                String cellName = key.displayName;

                String channelSuffix = "";
                if (key.cellType == ICellCacheRegistry.TYPE.FLUID) {
                    channelSuffix = GUI_CHANNEL_FLUID + " (F)";
                } else if (key.cellType == ICellCacheRegistry.TYPE.ESSENTIA) {
                    channelSuffix = GUI_CHANNEL_ESSENTIA + " (E)";
                } else {
                    channelSuffix = GUI_CHANNEL_ITEM + " (I)";
                }

                lines.add(GUI_LABEL + cellName + channelSuffix + ": " + GUI_VALUE + nf.format(cells.size()));
            }

            lines.add("");
        }

        if (!byType.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanCellTypesUtilization.getLocal() + " ──");

            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();
                Summary typeSummary = summarize(cells);

                String cellName = key.displayName;

                String channelSuffix = "";
                if (key.cellType == ICellCacheRegistry.TYPE.FLUID) {
                    channelSuffix = GUI_CHANNEL_FLUID + " (F)";
                } else if (key.cellType == ICellCacheRegistry.TYPE.ESSENTIA) {
                    channelSuffix = GUI_CHANNEL_ESSENTIA + " (E)";
                } else {
                    channelSuffix = GUI_CHANNEL_ITEM + " (I)";
                }

                lines.add(
                        GUI_LABEL + cellName
                                + channelSuffix
                                + ": "
                                + GUI_WARN
                                + String.format("%.1f%%", typeSummary.weightedBytesUtil * 100));
            }

            lines.add("");
        }

        Map<String, List<CellRecord>> duplicates = findDuplicatePartitionedCells(allCells);
        if (!duplicates.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanDuplicatePartitions.getLocal() + " ──");

            for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
                List<CellRecord> dupes = entry.getValue();

                String partitionList = String.join(", ", dupes.get(0).partitionedItems);
                if (partitionList.isEmpty()) partitionList = "Empty";

                lines.add(
                        GUI_ERROR + "• "
                                + GUI_VALUE
                                + dupes.size()
                                + " "
                                + GuiText.StorageScanCells.getLocal()
                                + " "
                                + GUI_SECONDARY
                                + GuiText.StorageScanLockedTo.getLocal()
                                + ":");
                lines.add(GUI_VALUE + "  " + partitionList);

                lines.add(GUI_HEADER + "  -- " + GuiText.StorageScanLocations.getLocal() + " --");
                for (CellRecord cell : dupes) {
                    String deviceType = cell.deviceType.equals("DRIVE") ? GuiText.StorageScanDrive.getLocal()
                            : GuiText.StorageScanChest.getLocal();
                    String coords = cell.deviceId.substring(cell.deviceId.indexOf('@') + 1);
                    lines.add(GUI_SECONDARY + "    " + deviceType + " " + coords + " #" + cell.slot);
                }
            }
            lines.add("");
        }

        List<CellRecord> fragmented = getTopFragmented(regularCells, 3);
        if (!fragmented.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanMostFragmented.getLocal() + " ──");
            for (CellRecord cell : fragmented) {
                String deviceType = cell.deviceType.equals("DRIVE") ? GuiText.StorageScanDrive.getLocal()
                        : GuiText.StorageScanChest.getLocal();
                String coords = cell.deviceId.substring(cell.deviceId.indexOf('@') + 1);
                lines.add(
                        GUI_SECONDARY + "• "
                                + GUI_VALUE
                                + deviceType
                                + " "
                                + coords
                                + GUI_SECONDARY
                                + " #"
                                + cell.slot
                                + ": "
                                + GUI_ERROR
                                + formatBytes(cell.typeLockedBytes()));
            }
            lines.add("");
        }

        lines.add(GUI_HEADER + "═════════════════════");

        return lines;
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        double t = idx - lo;
        return sorted[lo] * (1 - t) + sorted[hi] * t;
    }

    private static String formatBytes(double bytes) {
        if (bytes < 1024) return String.format("%.0f B", bytes);
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024 * 1024));
        return String.format("%.1f GB", bytes / (1024 * 1024 * 1024));
    }

    private static String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }

    public static List<String> generateTooltipReport(IGrid grid) {
        List<String> lines = new ArrayList<>();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        List<CellRecord> allCells = scanGrid(grid);
        Summary overall = summarize(allCells);

        List<CellRecord> regularCells = filterNonSingularityCells(allCells);
        List<CellRecord> singularityCells = filterSingularityCells(allCells);

        lines.add(TT_HEADER + "═══════════x " + GuiText.StorageScan.getLocal() + " ═══════════");
        lines.add("");

        lines.add(TT_DIVIDER + "───────────────────────────────");
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainLocked.getLocal());
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainWasted.getLocal());
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainFragmented.getLocal());
        lines.add(TT_DIVIDER + "───────────────────────────────");
        lines.add("");

        lines.add(TT_DIVIDER + "-- " + GuiText.StorageScanSummary.getLocal() + " --");
        lines.add(
                TT_LABEL + GuiText.StorageScanCells.getLocal()
                        + ": "
                        + TT_VALUE
                        + nf.format(overall.numCells)
                        + TT_SECONDARY
                        + " ("
                        + TT_VALUE
                        + nf.format(overall.numEmpty)
                        + TT_SECONDARY
                        + " "
                        + GuiText.StorageScanEmpty.getLocal()
                        + ")");
        lines.add("");

        List<CellRecord> itemCells = filterByType(allCells, ICellCacheRegistry.TYPE.ITEM);
        List<CellRecord> fluidCells = filterByType(allCells, ICellCacheRegistry.TYPE.FLUID);
        List<CellRecord> essentiaCells = filterByType(allCells, ICellCacheRegistry.TYPE.ESSENTIA);

        if (!itemCells.isEmpty()) {
            Summary itemSummary = summarize(itemCells);
            lines.add(
                    TT_LABEL + GuiText.Items.getLocal()
                            + ": "
                            + TT_VALUE
                            + nf.format(itemCells.size())
                            + TT_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + TT_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    itemSummary.weightedBytesUtil * 100));
        }

        if (!fluidCells.isEmpty()) {
            Summary fluidSummary = summarize(fluidCells);
            lines.add(
                    TT_LABEL + GuiText.Fluids.getLocal()
                            + ": "
                            + TT_VALUE
                            + nf.format(fluidCells.size())
                            + TT_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + TT_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    fluidSummary.weightedBytesUtil * 100));
        }

        if (!essentiaCells.isEmpty()) {
            Summary essentiaSummary = summarize(essentiaCells);
            lines.add(
                    TT_LABEL + GuiText.Essentias.getLocal()
                            + ": "
                            + TT_VALUE
                            + nf.format(essentiaCells.size())
                            + TT_SECONDARY
                            + " "
                            + GuiText.StorageScanCells.getLocal()
                            + " "
                            + TT_WARN
                            + String.format(
                                    "(%.1f%% " + GuiText.StorageScanUtil.getLocal() + ")",
                                    essentiaSummary.weightedBytesUtil * 100));
        }

        lines.add("");

        lines.add(TT_DIVIDER + "-- " + TT_LABEL + GuiText.StorageScanUtilization.getLocal() + TT_DIVIDER + " --");

        Summary regularSummary = summarize(regularCells);

        lines.add(
                TT_LABEL + GuiText.StorageScanBytesAll.getLocal()
                        + ": "
                        + TT_VALUE
                        + formatBytes(overall.sumBytesUsed)
                        + TT_SECONDARY
                        + " / "
                        + TT_VALUE
                        + formatBytes(overall.sumBytesTotal));
        lines.add(
                TT_SECONDARY + "  "
                        + TT_LABEL
                        + GuiText.StorageScanUtil.getLocal()
                        + ": "
                        + TT_WARN
                        + String.format("%.1f%%", overall.weightedBytesUtil * 100)
                        + TT_SECONDARY
                        + " ("
                        + TT_LABEL
                        + GuiText.StorageScanMedian.getLocal()
                        + ": "
                        + TT_WARN
                        + String.format("%.1f%%)", overall.bytesP50 * 100));

        if (!regularCells.isEmpty()) {
            lines.add(
                    TT_LABEL + GuiText.StorageScanBytesExclSing.getLocal()
                            + ": "
                            + TT_VALUE
                            + formatBytes(regularSummary.sumBytesUsed)
                            + TT_SECONDARY
                            + " / "
                            + TT_VALUE
                            + formatBytes(regularSummary.sumBytesTotal));
            lines.add(
                    TT_SECONDARY + "  "
                            + TT_LABEL
                            + GuiText.StorageScanUtil.getLocal()
                            + ": "
                            + TT_WARN
                            + String.format("%.1f%%", regularSummary.weightedBytesUtil * 100)
                            + TT_SECONDARY
                            + " ("
                            + TT_LABEL
                            + GuiText.StorageScanMedian.getLocal()
                            + ": "
                            + TT_WARN
                            + String.format("%.1f%%)", regularSummary.bytesP50 * 100));
        }

        lines.add("");
        lines.add(
                TT_LABEL + GuiText.Types.getLocal()
                        + ": "
                        + TT_VALUE
                        + nf.format((long) overall.sumTypesUsed)
                        + TT_SECONDARY
                        + " / "
                        + TT_VALUE
                        + nf.format((long) overall.sumTypesTotal));
        lines.add(
                TT_SECONDARY + "  "
                        + TT_LABEL
                        + GuiText.StorageScanUtil.getLocal()
                        + ": "
                        + TT_WARN
                        + String.format("%.1f%%", overall.weightedTypesUtil * 100)
                        + TT_SECONDARY
                        + " ("
                        + TT_LABEL
                        + GuiText.StorageScanMedian.getLocal()
                        + ": "
                        + TT_WARN
                        + String.format("%.1f%%)", overall.typesP50 * 100));

        lines.add("");

        List<CellRecord> framedRegularCells = regularCells.stream().filter(CellRecord::typeLocked)
                .collect(Collectors.toList());
        if (!framedRegularCells.isEmpty()) {
            long totalWasted = framedRegularCells.stream().mapToLong(c -> (long) c.typeLockedBytes()).sum();
            lines.add(TT_DIVIDER + "-- " + TT_LABEL + GuiText.StorageScanFragmentation.getLocal() + TT_DIVIDER + " --");
            lines.add(
                    TT_LABEL + GuiText.StorageScanLocked.getLocal()
                            + ": "
                            + TT_ERROR
                            + nf.format(framedRegularCells.size())
                            + TT_SECONDARY
                            + " "
                            + TT_VALUE
                            + GuiText.StorageScanCells.getLocal());
            lines.add(TT_LABEL + GuiText.StorageScanWasted.getLocal() + ": " + TT_ERROR + formatBytes(totalWasted));
            lines.add(TT_SECONDARY + GuiText.StorageScanSingularityExcluded.getLocal());
            lines.add("");
        }

        Map<CellKey, List<CellRecord>> byType = groupByType(allCells);
        if (!byType.isEmpty()) {
            lines.add(TT_DIVIDER + "-- " + TT_LABEL + GuiText.StorageScanCellTypes.getLocal() + TT_DIVIDER + " --");

            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();

                String cellName = stripFormatting(key.displayName);
                String channelSuffix = "";
                if (key.cellType == ICellCacheRegistry.TYPE.FLUID) {
                    channelSuffix = TT_CHANNEL_FLUID + " (F)" + TT_LABEL;
                } else if (key.cellType == ICellCacheRegistry.TYPE.ESSENTIA) {
                    channelSuffix = TT_CHANNEL_ESSENTIA + " (E)" + TT_LABEL;
                } else {
                    channelSuffix = TT_CHANNEL_ITEM + " (I)" + TT_LABEL;
                }

                lines.add(TT_LABEL + cellName + " " + channelSuffix + ": " + TT_VALUE + nf.format(cells.size()));
            }

            lines.add("");
        }

        if (!byType.isEmpty()) {
            lines.add(
                    TT_DIVIDER + "-- "
                            + TT_LABEL
                            + GuiText.StorageScanCellTypesUtilization.getLocal()
                            + TT_DIVIDER
                            + " --");

            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();
                Summary typeSummary = summarize(cells);

                String cellName = stripFormatting(key.displayName);
                String channelSuffix = "";
                if (key.cellType == ICellCacheRegistry.TYPE.FLUID) {
                    channelSuffix = TT_CHANNEL_FLUID + " (F)" + TT_LABEL;
                } else if (key.cellType == ICellCacheRegistry.TYPE.ESSENTIA) {
                    channelSuffix = TT_CHANNEL_ESSENTIA + " (E)" + TT_LABEL;
                } else {
                    channelSuffix = TT_CHANNEL_ITEM + " (I)" + TT_LABEL;
                }

                lines.add(
                        TT_LABEL + cellName
                                + " "
                                + channelSuffix
                                + ": "
                                + TT_WARN
                                + String.format("%.1f%%", typeSummary.weightedBytesUtil * 100));
            }

            lines.add("");
        }

        Map<String, List<CellRecord>> duplicates = findDuplicatePartitionedCells(allCells);
        if (!duplicates.isEmpty()) {
            lines.add(
                    TT_DIVIDER + "-- "
                            + TT_LABEL
                            + GuiText.StorageScanDuplicatePartitions.getLocal()
                            + TT_DIVIDER
                            + " --");

            for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
                List<CellRecord> dupes = entry.getValue();

                String partitionList = String.join(", ", dupes.get(0).partitionedItems);
                if (partitionList.isEmpty()) partitionList = TT_SECONDARY + GuiText.Empty.getLocal();

                lines.add(
                        TT_ERROR + "• "
                                + TT_VALUE
                                + dupes.size()
                                + " "
                                + TT_LABEL
                                + GuiText.StorageScanCells.getLocal()
                                + " "
                                + TT_SECONDARY
                                + GuiText.StorageScanLockedTo.getLocal()
                                + ":");
                lines.add(TT_VALUE + "  " + partitionList);

                lines.add(TT_HEADER + "  -- " + TT_LABEL + GuiText.StorageScanLocations.getLocal() + TT_HEADER + " --");
                for (CellRecord cell : dupes) {
                    String deviceType = cell.deviceType.equals("DRIVE") ? TT_VALUE + GuiText.StorageScanDrive.getLocal()
                            : TT_VALUE + GuiText.StorageScanChest.getLocal();
                    String fullLocation = cell.deviceId.substring(cell.deviceId.indexOf('@') + 1);
                    lines.add(
                            TT_SECONDARY + "    "
                                    + deviceType
                                    + " "
                                    + TT_VALUE
                                    + fullLocation
                                    + TT_SECONDARY
                                    + " #"
                                    + TT_VALUE
                                    + cell.slot);
                }
            }
            lines.add("");
        }

        List<CellRecord> fragmented = getTopFragmented(regularCells, 5);
        if (!fragmented.isEmpty()) {
            lines.add(
                    TT_DIVIDER + "-- " + TT_LABEL + GuiText.StorageScanMostFragmented.getLocal() + TT_DIVIDER + " --");
            for (CellRecord cell : fragmented) {
                String deviceType = cell.deviceType.equals("DRIVE") ? TT_VALUE + GuiText.StorageScanDrive.getLocal()
                        : TT_VALUE + GuiText.StorageScanChest.getLocal();
                String coords = cell.deviceId.substring(cell.deviceId.indexOf('@') + 1);
                lines.add(
                        TT_SECONDARY + "• "
                                + deviceType
                                + " "
                                + TT_VALUE
                                + coords
                                + TT_SECONDARY
                                + " #"
                                + TT_VALUE
                                + cell.slot
                                + TT_SECONDARY
                                + ": "
                                + TT_ERROR
                                + formatBytes(cell.typeLockedBytes()));
            }
            lines.add("");
        }

        lines.add(TT_DIVIDER + "════════════════════════");

        return lines;
    }
}
