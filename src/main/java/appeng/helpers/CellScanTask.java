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
        public final List<ItemStack> partitionedItemStacks;
        public final int x;
        public final int y;
        public final int z;
        public final int dim;

        public CellRecord(String deviceType, String deviceId, int slot, String cellItemId, int cellMeta,
                String cellDisplayName, ICellCacheRegistry.TYPE cellType, double bytesTotal, double bytesUsed,
                double typesTotal, double typesUsed, boolean isPartitioned, List<String> partitionedItems,
                List<ItemStack> partitionedItemStacks, int x, int y, int z, int dim) {
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
            this.partitionedItemStacks = partitionedItemStacks != null ? partitionedItemStacks : new ArrayList<>();
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
        }
    }

    public static List<CellRecord> scanGrid(IGrid grid) {
        List<CellRecord> records = new ArrayList<>();

        try {
            for (IGridNode obj : grid.getMachines(TileDrive.class)) {
                try {
                    TileDrive drive = (obj != null) ? (TileDrive) obj.getMachine() : null;
                    assert drive != null;
                    int dimension = drive.getWorldObj() != null ? drive.getWorldObj().provider.dimensionId : 0;
                    String deviceId = String
                            .format("Drive@%d,%d,%d (Dim %d)", drive.xCoord, drive.yCoord, drive.zCoord, dimension);
                    IInventory inv = drive.getInternalInventory();

                    for (int slot = 0; slot < 10; slot++) {
                        ItemStack cellStack = inv.getStackInSlot(slot);
                        if (cellStack == null) continue;
                        scanCell(
                                records,
                                cellStack,
                                "DRIVE",
                                deviceId,
                                slot,
                                drive,
                                drive.xCoord,
                                drive.yCoord,
                                drive.zCoord,
                                dimension);
                    }
                } catch (Exception ignored) {}
            }

            for (IGridNode obj : grid.getMachines(TileChest.class)) {
                try {
                    TileChest chest = (obj != null) ? (TileChest) obj.getMachine() : null;
                    assert chest != null;
                    int dimension = chest.getWorldObj() != null ? chest.getWorldObj().provider.dimensionId : 0;
                    String deviceId = String
                            .format("Chest@%d,%d,%d (Dim %d)", chest.xCoord, chest.yCoord, chest.zCoord, dimension);
                    ItemStack cellStack = chest.getInternalInventory().getStackInSlot(1);
                    if (cellStack != null) scanCell(
                            records,
                            cellStack,
                            "CHEST",
                            deviceId,
                            1,
                            chest,
                            chest.xCoord,
                            chest.yCoord,
                            chest.zCoord,
                            dimension);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            AELog.error(e, "CellScanTask: Grid scan failed");
        }

        return records;
    }

    private static void scanCell(List<CellRecord> records, ItemStack cellStack, String deviceType, String deviceId,
            int slot, ISaveProvider saveProvider, int x, int y, int z, int dim) {
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
            List<ItemStack> partitionedItemStacks = new ArrayList<>();
            if (cellStack.getItem() instanceof ICellWorkbenchItem cwi) {
                final appeng.tile.inventory.IAEStackInventory configInv = cwi.getConfigAEInventory(cellStack);
                if (configInv != null) {
                    for (int i = 0; i < configInv.getSizeInventory(); i++) {
                        final appeng.api.storage.data.IAEStack<?> filterStack = configInv.getAEStackInSlot(i);
                        if (filterStack instanceof appeng.api.storage.data.IAEItemStack ais) {
                            final ItemStack is = ais.getItemStack();
                            if (is != null) {
                                isPartitioned = true;
                                partitionedItems.add(is.getDisplayName());
                                partitionedItemStacks.add(is);
                            }
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
                            partitionedItems,
                            partitionedItemStacks,
                            x,
                            y,
                            z,
                            dim));
            break;
        }
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
}
