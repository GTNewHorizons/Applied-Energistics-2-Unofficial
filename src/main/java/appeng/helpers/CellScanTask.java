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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AELog;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

public final class CellScanTask {

    private CellScanTask() {}

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

        public double bytesP50;
        public double typesP50;
    }

    public static List<CellRecord> scanGrid(IGrid grid) {
        List<CellRecord> records = new ArrayList<>();

        try {
            for (Object obj : grid.getMachines(TileDrive.class)) {
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
                        scanCell(records, cellStack, "DRIVE", deviceId, slot, drive);
                    }
                } catch (Exception ignored) {}
            }

            for (Object obj : grid.getMachines(TileChest.class)) {
                try {
                    TileChest chest = (obj instanceof IGridNode) ? (TileChest) ((IGridNode) obj).getMachine()
                            : (TileChest) obj;
                    int dimension = chest.getWorldObj() != null ? chest.getWorldObj().provider.dimensionId : 0;
                    String deviceId = String
                            .format("Chest@%d,%d,%d (Dim %d)", chest.xCoord, chest.yCoord, chest.zCoord, dimension);
                    ItemStack cellStack = chest.getInternalInventory().getStackInSlot(1);
                    if (cellStack != null) scanCell(records, cellStack, "CHEST", deviceId, 1, chest);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AELog.error(e, "CellScanTask: Grid scan failed");
        }

        return records;
    }

    private static void scanCell(List<CellRecord> records, ItemStack cellStack, String deviceType, String deviceId,
            int slot, ISaveProvider saveProvider) {
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cellStack);
        if (cellHandler == null) return;

        for (IAEStackType<?> stackType : AEStackTypeRegistry.getAllTypes()) {
            IMEInventoryHandler<?> cellInv;
            try {
                cellInv = cellHandler.getCellInventory(cellStack, saveProvider, stackType);
            } catch (ClassCastException | IllegalArgumentException e) {
                continue;
            }
            if (!(cellInv instanceof ICellCacheRegistry reg) || !reg.canGetInv()) continue;

            boolean isPartitioned = false;
            List<String> partitionedItems = new ArrayList<>();
            if (cellStack.getItem() instanceof ICellWorkbenchItem cwi) {
                IInventory configInv = cwi.getConfigInventory(cellStack);
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
                            deviceType,
                            deviceId,
                            slot,
                            Item.itemRegistry.getNameForObject(cellStack.getItem()),
                            cellStack.getItemDamage(),
                            cellStack.getDisplayName(),
                            reg.getCellType(),
                            reg.getTotalBytes(),
                            reg.getUsedBytes(),
                            reg.getTotalTypes(),
                            reg.getUsedTypes(),
                            isPartitioned,
                            partitionedItems));
            break;
        }
    }

    public static Summary summarize(List<CellRecord> cells) {
        Summary s = new Summary();
        if (cells.isEmpty()) return s;

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
        s.bytesP50 = percentile(bu, 0.50);
        s.typesP50 = percentile(tu, 0.50);

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
        Map<String, List<CellRecord>> duplicates = new HashMap<>();
        for (CellRecord cell : cells) {
            if (!cell.isPartitioned || cell.partitionedItems.isEmpty()) continue;
            List<String> sorted = new ArrayList<>(cell.partitionedItems);
            Collections.sort(sorted);
            String signature = String.join("|", sorted) + "|" + cell.cellType.name();
            duplicates.computeIfAbsent(signature, k -> new ArrayList<>()).add(cell);
        }
        Map<String, List<CellRecord>> result = new HashMap<>();
        for (Map.Entry<String, List<CellRecord>> entry : duplicates.entrySet()) {
            if (entry.getValue().size() >= 2) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static List<CellRecord> getTopFragmented(List<CellRecord> cells, int limit) {
        return cells.stream().filter(CellRecord::typeLocked)
                .sorted((a, b) -> Double.compare(b.typeLockedBytes(), a.typeLockedBytes())).limit(limit)
                .collect(Collectors.toList());
    }

    public static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        return sorted[lo] * (1 - (idx - lo)) + sorted[hi] * (idx - lo);
    }

    public static String formatBytes(double bytes) {
        if (bytes < 1024) return String.format("%.0f B", bytes);
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024 * 1024));
        return String.format("%.1f GB", bytes / (1024 * 1024 * 1024));
    }

    public static String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }
}
