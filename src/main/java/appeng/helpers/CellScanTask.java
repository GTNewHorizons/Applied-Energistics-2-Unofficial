package appeng.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.DimensionalCoord;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.CellInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;

public class CellScanTask {

    private final Map<IAEStackType<?>, Map<IAEStack<?>, List<CellRecord>>> partitions = new IdentityHashMap<>();
    private final IGrid grid;

    public CellScanTask(final IGrid grid) {
        this.grid = grid;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.partitions.put(type, new HashMap<>());
        }
    }

    public static final class CellRecord {

        public final int slot;
        public final String cellDisplayName;
        public final int typesUsed;
        public final int x;
        public final int y;
        public final int z;
        public final int dim;

        public CellRecord(int slot, String cellDisplayName, int typesUsed, DimensionalCoord dc) {
            this.slot = slot;
            this.cellDisplayName = cellDisplayName;
            this.typesUsed = typesUsed;
            this.x = dc.x;
            this.y = dc.y;
            this.z = dc.z;
            this.dim = dc.getDimension();
        }
    }

    public void scanGrid() {
        final GridStorageCache gsc = grid.getCache(IStorageGrid.class);
        for (ICellProvider icp : gsc.getActiveCellProviders()) {
            if (icp instanceof IGridProxyable igp) {
                final DimensionalCoord dc = igp.getLocation();
                for (IAEStackType<?> stackType : AEStackTypeRegistry.getAllTypes()) {
                    for (IMEInventoryHandler<?> inv : icp.getCellArray(stackType)) {
                        if (inv.getInternal() instanceof IMEInventoryHandler meih2) {
                            if (meih2.getInternal() instanceof IMEInventoryHandler meih3) {
                                final IMEInventory internal = meih3.getInternal();

                                if (internal instanceof CellInventory<?>ci) {
                                    final ItemStack cell = ci.getItemStack();

                                    if (igp instanceof IInventory iinv) {
                                        for (int i = 0; i < iinv.getSizeInventory(); i++) {
                                            if (iinv.getStackInSlot(i) == cell) {
                                                scanCell(cell, i, ci, dc);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    private void scanCell(ItemStack cellStack, int slot, CellInventory<?> cellInv, DimensionalCoord dc) {
        final IAEStackInventory configInv = cellInv.getConfigAEInventory();
        if (configInv != null) {
            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                final IAEStack<?> filterStack = configInv.getAEStackInSlot(i);
                final Map<IAEStack<?>, List<CellRecord>> map = this.partitions.get(filterStack.getStackType());
                final List<CellRecord> list = map.getOrDefault(filterStack, new ArrayList<>());
                list.add(
                        new CellRecord(
                                slot,
                                Platform.getItemDisplayName(cellStack),
                                (int) cellInv.getStoredItemTypes(),
                                dc));
                map.put(filterStack, list);
            }
        }
    }

    public void findDuplicatePartitionedCells() {
        final Iterator<Entry<IAEStackType<?>, Map<IAEStack<?>, List<CellRecord>>>> firstIter = partitions.entrySet()
                .iterator();
        while (firstIter.hasNext()) {
            final Entry<IAEStackType<?>, Map<IAEStack<?>, List<CellRecord>>> firstEntry = firstIter.next();
            firstEntry.getValue().entrySet().removeIf(secondEntry -> secondEntry.getValue().size() < 2);
            if (firstEntry.getValue().isEmpty()) firstIter.remove();
        }
    }

    public Map<IAEStackType<?>, Map<IAEStack<?>, List<CellRecord>>> getPartitions() {
        return partitions;
    }
}
