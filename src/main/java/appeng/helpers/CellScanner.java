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

/**
 * Comprehensive cell scanner and statistical analyzer for ME Network storage. Provides detailed per-cell metrics and
 * aggregated statistics for storage optimization.
 */
public final class CellScanner {

    private CellScanner() {}

    // ========== COLOR CONSTANTS ==========

    // GUI Report Colors (optimized for light gray background - high contrast)
    private static final String GUI_HEADER = "§1"; // dark blue - section headers (high contrast)
    private static final String GUI_LABEL = "§1"; // dark blue - field labels (readable, not black)
    private static final String GUI_VALUE = "§8"; // dark gray - normal values (readable, not black)
    private static final String GUI_SECONDARY = "§8"; // dark gray - secondary/footnote
    private static final String GUI_WARN = "§6"; // gold - warnings/percentages
    private static final String GUI_ERROR = "§4"; // dark red - errors/critical
    private static final String GUI_GOOD = "§2"; // dark green - positive/good
    private static final String GUI_CHANNEL_ITEM = "§8"; // dark gray - item channel
    private static final String GUI_CHANNEL_FLUID = "§1"; // dark blue - fluid channel
    private static final String GUI_CHANNEL_ESSENTIA = "§5"; // purple - essentia channel

    // Tooltip Colors (optimized for dark purple background) - matching reshuffle tooltip colors
    private static final String TT_HEADER = "§b"; // bright aqua - section headers
    private static final String TT_LABEL = "§b"; // bright aqua - labels
    private static final String TT_VALUE = "§f"; // white - values (good contrast on dark background)
    private static final String TT_SECONDARY = "§7"; // light gray - secondary text
    private static final String TT_WARN = "§e"; // yellow - warnings
    private static final String TT_ERROR = "§c"; // bright red - errors
    private static final String TT_GOOD = "§a"; // bright green - positive
    private static final String TT_DIVIDER = "§7"; // light gray - dividers
    private static final String TT_CHANNEL_ITEM = "§f"; // white - item channel (matching reshuffle)
    private static final String TT_CHANNEL_FLUID = "§b"; // cyan - fluid channel
    private static final String TT_CHANNEL_ESSENTIA = "§d"; // pink - essentia channel

    /**
     * Unique identifier for a storage cell type
     */
    public static final class CellKey {

        public final String itemId;
        public final int meta;
        public final ICellCacheRegistry.TYPE cellType;
        public final String displayName; // Add display name for proper cell identification

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

    /**
     * Complete information about a single physical storage cell
     */
    public static final class CellRecord {

        public final String deviceType;
        public final String deviceId;
        public final int slot;
        public final String cellItemId;
        public final int cellMeta;
        public final String cellDisplayName; // Add display name
        public final ICellCacheRegistry.TYPE cellType;
        public final double bytesTotal;
        public final double bytesUsed;
        public final double typesTotal;
        public final double typesUsed;
        public final boolean isPartitioned; // Whether cell has partition/filter configured
        public final List<String> partitionedItems; // List of partitioned item names (if any)

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

        /**
         * Check if this is a singularity cell (cells that only hold 1 type)
         */
        public boolean isSingularityCell() {
            return typesTotal == 1;
        }
    }

    /**
     * Statistical summary of a population of cells
     */
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

    /**
     * Scans all storage cells in the grid and returns detailed per-cell records
     */
    public static List<CellRecord> scanGrid(IGrid grid) {
        List<CellRecord> records = new ArrayList<>();

        try {
            // Scan ME Drives - check all slots for all stack types
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

                    // Drives have 10 slots (INV_SIZE = 10)
                    // We need to check each slot for ANY stack type
                    for (int slot = 0; slot < 10; slot++) {
                        ItemStack cellStack = inv.getStackInSlot(slot);
                        if (cellStack == null) continue;

                        // Try to find a handler for this cell across all stack types
                        appeng.api.storage.ICellHandler cellHandler = appeng.api.AEApi.instance().registries().cell()
                                .getHandler(cellStack);
                        if (cellHandler == null) continue;

                        // Try each stack type until we find the right one
                        for (appeng.api.storage.data.IAEStackType<?> stackType : appeng.api.storage.data.AEStackTypeRegistry
                                .getAllTypes()) {
                            try {
                                appeng.api.storage.IMEInventoryHandler<?> cellInv = cellHandler
                                        .getCellInventory(cellStack, drive, stackType);
                                if (cellInv == null) continue;

                                // Check if this is a ICellCacheRegistry implementation
                                if (cellInv instanceof ICellCacheRegistry reg && reg.canGetInv()) {
                                    String displayName = cellStack.getDisplayName();

                                    // Check if cell is partitioned (has config filter)
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
                                    break; // Found the right stack type for this cell, move to next slot
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

            // Scan ME Chests
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

                    // Chest cell is in slot 1 (fixed slot)
                    ItemStack cellStack = inv.getStackInSlot(1);
                    if (cellStack == null) continue;

                    // Get cell handler
                    appeng.api.storage.ICellHandler cellHandler = appeng.api.AEApi.instance().registries().cell()
                            .getHandler(cellStack);
                    if (cellHandler == null) continue;

                    // Try all stack types to find the one this chest supports
                    for (appeng.api.storage.data.IAEStackType<?> stackType : appeng.api.storage.data.AEStackTypeRegistry
                            .getAllTypes()) {
                        try {
                            appeng.api.storage.IMEInventoryHandler<?> cellInv = cellHandler
                                    .getCellInventory(cellStack, chest, stackType);
                            if (cellInv == null) continue;

                            if (cellInv instanceof ICellCacheRegistry reg && reg.canGetInv()) {
                                String displayName = cellStack.getDisplayName();

                                // Check if cell is partitioned (has config filter)
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
                                                1, // Always slot 1 for chests
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
                                break; // Found this cell's type, no need to try other types
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

    /**
     * Computes comprehensive statistics for a list of cells
     */
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

    /**
     * Groups cells by their type (item ID + meta + cell type)
     */
    public static Map<CellKey, List<CellRecord>> groupByType(List<CellRecord> cells) {
        return cells.stream().collect(
                Collectors.groupingBy(c -> new CellKey(c.cellItemId, c.cellMeta, c.cellType, c.cellDisplayName)));
    }

    /**
     * Filters cells by channel type
     */
    public static List<CellRecord> filterByType(List<CellRecord> cells, ICellCacheRegistry.TYPE type) {
        return cells.stream().filter(c -> c.cellType == type).collect(Collectors.toList());
    }

    /**
     * Filters to get only singularity cells (1 type slot cells)
     */
    public static List<CellRecord> filterSingularityCells(List<CellRecord> cells) {
        return cells.stream().filter(CellRecord::isSingularityCell).collect(Collectors.toList());
    }

    /**
     * Filters to get only non-singularity cells (multi-type cells)
     */
    public static List<CellRecord> filterNonSingularityCells(List<CellRecord> cells) {
        return cells.stream().filter(c -> !c.isSingularityCell()).collect(Collectors.toList());
    }

    /**
     * Finds duplicate partitioned cells - cells that are locked to the same item types
     */
    public static Map<String, List<CellRecord>> findDuplicatePartitionedCells(List<CellRecord> cells) {
        // Group partitioned cells by their partition signature
        Map<String, List<CellRecord>> duplicates = new java.util.HashMap<>();

        for (CellRecord cell : cells) {
            if (!cell.isPartitioned || cell.partitionedItems.isEmpty()) continue;

            // Create signature from sorted partition items
            List<String> sortedItems = new ArrayList<>(cell.partitionedItems);
            java.util.Collections.sort(sortedItems);
            String signature = String.join("|", sortedItems) + "|" + cell.cellType.name();

            duplicates.computeIfAbsent(signature, k -> new ArrayList<>()).add(cell);
        }

        // Filter to only include groups with 2+ cells (actual duplicates)
        Map<String, List<CellRecord>> result = new java.util.HashMap<>();
        for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
            if (entry.getValue().size() >= 2) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Finds the most fragmented cells (type-locked with most wasted bytes)
     */
    public static List<CellRecord> getTopFragmented(List<CellRecord> cells, int limit) {
        return cells.stream().filter(CellRecord::typeLocked)
                .sorted((a, b) -> Double.compare(b.typeLockedBytes(), a.typeLockedBytes())).limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Finds cells with lowest utilization
     */
    public static List<CellRecord> getLowestUtilization(List<CellRecord> cells, int limit) {
        return cells.stream().filter(c -> !c.isEmpty()).sorted(Comparator.comparingDouble(CellRecord::bytesUtilPct))
                .limit(limit).collect(Collectors.toList());
    }

    /**
     * Generates a human-readable report for display in GUI or chat
     */
    public static List<String> generateReport(IGrid grid) {
        List<String> lines = new ArrayList<>();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        List<CellRecord> allCells = scanGrid(grid);
        Summary overall = summarize(allCells);

        // Separate singularity cells from regular cells for analysis
        List<CellRecord> regularCells = filterNonSingularityCells(allCells);
        List<CellRecord> singularityCells = filterSingularityCells(allCells);

        // Header
        lines.add(GUI_HEADER + "═══════ " + GuiText.StorageScan.getLocal() + " ═══════");
        lines.add("");

        // Global summary
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

        // Separate rows for Items/Fluids/Essentia (filter types)
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

        // ====== UTILIZATION SECTION ======
        lines.add(GUI_HEADER + "── " + GuiText.StorageScanUtilization.getLocal() + " ──");

        // Bytes utilization - WITH and WITHOUT singularities
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

        // Types utilization
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

        // Fragmentation - EXCLUDING singularity cells
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

        // Cell type breakdown - counts only (no utilization)
        Map<CellKey, List<CellRecord>> byType = groupByType(allCells);
        if (!byType.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanCellTypes.getLocal() + " ──");

            // Sort by count descending
            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();

                String cellName = key.displayName;

                // Channel type suffix
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

        // Cell Types Utilization - separate section
        if (!byType.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanCellTypesUtilization.getLocal() + " ──");

            // Sort by count descending
            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();
                Summary typeSummary = summarize(cells);

                String cellName = key.displayName;

                // Channel type suffix
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

        // Duplicate partitioned cells analysis - full details, no truncation
        Map<String, List<CellRecord>> duplicates = findDuplicatePartitionedCells(allCells);
        if (!duplicates.isEmpty()) {
            lines.add(GUI_HEADER + "── " + GuiText.StorageScanDuplicatePartitions.getLocal() + " ──");

            for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
                List<CellRecord> dupes = entry.getValue();

                // Build full partition list (no truncation)
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

                // Show locations
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

        // Top fragmented cells (excluding singularities)
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

        // Bottom line
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

    /**
     * Strips Minecraft formatting codes (§x) from a string
     */
    private static String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }

    /**
     * Generates a tooltip-optimized report with brighter colors for dark backgrounds
     */
    public static List<String> generateTooltipReport(IGrid grid) {
        List<String> lines = new ArrayList<>();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        List<CellRecord> allCells = scanGrid(grid);
        Summary overall = summarize(allCells);

        // Separate singularity cells from regular cells
        List<CellRecord> regularCells = filterNonSingularityCells(allCells);
        List<CellRecord> singularityCells = filterSingularityCells(allCells);

        // Header
        lines.add(TT_HEADER + "═══════════x " + GuiText.StorageScan.getLocal() + " ═══════════");
        lines.add("");

        // Add explanatory text for key terms
        lines.add(TT_DIVIDER + "───────────────────────────────");
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainLocked.getLocal());
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainWasted.getLocal());
        lines.add(TT_SECONDARY + GuiText.StorageScanExplainFragmented.getLocal());
        lines.add(TT_DIVIDER + "───────────────────────────────");
        lines.add("");

        // Global summary
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

        // Filter types
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

        // Utilization
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

        // Fragmentation (excluding singularities)
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

        // Cell type breakdown - counts only (no utilization)
        Map<CellKey, List<CellRecord>> byType = groupByType(allCells);
        if (!byType.isEmpty()) {
            lines.add(TT_DIVIDER + "-- " + TT_LABEL + GuiText.StorageScanCellTypes.getLocal() + TT_DIVIDER + " --");

            List<Map.Entry<CellKey, List<CellRecord>>> sortedTypes = new ArrayList<>(byType.entrySet());
            sortedTypes.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            for (Map.Entry<CellKey, List<CellRecord>> entry : sortedTypes) {
                CellKey key = entry.getKey();
                List<CellRecord> cells = entry.getValue();

                // Strip any formatting codes from cell name to prevent color conflicts
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

        // Cell Types Utilization - separate section
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

                // Strip any formatting codes from cell name to prevent color conflicts
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

        // Duplicate partitioned cells - full details, no truncation
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

                // Build full partition list (no truncation)
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

                // Show all duplicate locations with full coordinates (including dimension)
                lines.add(TT_HEADER + "  -- " + TT_LABEL + GuiText.StorageScanLocations.getLocal() + TT_HEADER + " --");
                for (CellRecord cell : dupes) {
                    String deviceType = cell.deviceType.equals("DRIVE") ? TT_VALUE + GuiText.StorageScanDrive.getLocal()
                            : TT_VALUE + GuiText.StorageScanChest.getLocal();
                    // deviceId already includes dimension in format "Drive@x,y,z (Dim N)" or "Chest@x,y,z (Dim N)"
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

        // Top fragmented (excluding singularities)
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
