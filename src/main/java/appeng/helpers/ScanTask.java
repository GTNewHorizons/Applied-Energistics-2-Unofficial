package appeng.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.core.AELog;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.CellInventory;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.IAEStackList;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ScanTask implements IGuiPacketWritable {

    private final Map<IAEStackType<?>, Map<IAEStack<?>, List<ScanRecord>>> scanData = new IdentityHashMap<>();
    private final List<ScanRecord> scanCellsData = new ArrayList<>();

    public ScanTask() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.scanData.put(type, new HashMap<>());
        }
    }

    // For IGuiPacketWritable
    public ScanTask(final ByteBuf buf) {
        final int sizeScanData = buf.readInt();
        for (int i = 0; i < sizeScanData; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            final Map<IAEStack<?>, List<ScanRecord>> stackPartitionsMap = new HashMap<>();
            final int mapSize = buf.readInt();

            for (int j = 0; j < mapSize; j++) {
                final IAEStack<?> aes = Platform.readStackByte(buf);
                final List<ScanRecord> scanRecords = new ArrayList<>();
                final int recordSize = buf.readInt();

                for (int k = 0; k < recordSize; k++) scanRecords.add(ScanRecord.read(buf));

                stackPartitionsMap.put(aes, scanRecords);
            }

            this.scanData.put(AEStackTypeRegistry.getType(typeId), stackPartitionsMap);
        }

        final int sizeScanCellsData = buf.readInt();
        for (int i = 0; i < sizeScanCellsData; i++) {
            this.scanCellsData.add(ScanRecord.read(buf));
        }
    }

    @Override
    public void writeToPacket(ByteBuf buf) {
        buf.writeInt(scanData.size());
        for (Entry<IAEStackType<?>, Map<IAEStack<?>, List<ScanRecord>>> entry : scanData.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey().getId());
            final Map<IAEStack<?>, List<ScanRecord>> map = entry.getValue();
            buf.writeInt(map.size());

            for (Entry<IAEStack<?>, List<ScanRecord>> stackEntry : map.entrySet()) {
                Platform.writeStackByte(stackEntry.getKey(), buf);
                buf.writeInt(stackEntry.getValue().size());
                stackEntry.getValue().forEach(record -> record.write(buf));
            }
        }

        buf.writeInt(this.scanCellsData.size());
        this.scanCellsData.forEach(c -> c.write(buf));
    }

    public static class ScanRecord {

        public final ItemStack itemStack;
        public final int slot, x, y, z, dim;
        public final long typesUsed, typesTotal, bytesUsed, bytesTotal;
        public final List<IAEStack<?>> topStoredItems;

        public ScanRecord(int slot, ItemStack itemStack, long typesUsed, long typesTotal, long bytesUsed,
                long bytesTotal, DimensionalCoord dc, List<IAEStack<?>> topStoredItems) {
            this.slot = slot;
            this.itemStack = itemStack;
            this.typesUsed = typesUsed;
            this.typesTotal = typesTotal;
            this.bytesUsed = bytesUsed;
            this.bytesTotal = bytesTotal;
            this.x = dc.x;
            this.y = dc.y;
            this.z = dc.z;
            this.dim = dc.getDimension();
            this.topStoredItems = topStoredItems;
        }

        public void write(ByteBuf buf) {
            buf.writeInt(slot);
            Platform.writeStackByte(AEItemStack.create(itemStack), buf);
            buf.writeLong(typesUsed);
            buf.writeLong(typesTotal);
            buf.writeLong(bytesUsed);
            buf.writeLong(bytesTotal);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(dim);

            buf.writeInt(topStoredItems.size());
            topStoredItems.forEach(record -> Platform.writeStackByte(record, buf));
        }

        public static ScanRecord read(ByteBuf buf) {
            final int slot = buf.readInt();
            final ItemStack itemStack = Platform.readStackByte(buf).getItemStackForNEI();
            final long typesUsed = buf.readLong();
            final long typesTotal = buf.readLong();
            final long bytesUsed = buf.readLong();
            final long bytesTotal = buf.readLong();
            final int x = buf.readInt();
            final int y = buf.readInt();
            final int z = buf.readInt();
            final int dim = buf.readInt();

            final List<IAEStack<?>> topStoredItems = new ArrayList<>();
            final int recordSize = buf.readInt();
            for (int i = 0; i < recordSize; i++) {
                topStoredItems.add(Platform.readStackByte(buf));
            }

            return new ScanRecord(
                    slot,
                    itemStack,
                    typesUsed,
                    typesTotal,
                    bytesUsed,
                    bytesTotal,
                    new DimensionalCoord(x, y, z, dim),
                    topStoredItems);
        }
    }

    public void scanGrid(final IGrid grid) {
        final GridStorageCache gsc = grid.getCache(IStorageGrid.class);
        for (ICellProvider icp : gsc.getActiveCellProviders()) {
            if (icp instanceof IGridProxyable igp) {
                final DimensionalCoord dc = igp.getLocation();

                if (igp instanceof PartStorageBus sb) {
                    scan(
                            sb.getPrimaryGuiIcon(),
                            sb.getInternalHandler(),
                            -1,
                            sb.getAEInventoryByName(StorageName.CONFIG),
                            dc,
                            -1,
                            -1,
                            -1,
                            -1);
                    continue;
                }

                for (IAEStackType<?> stackType : AEStackTypeRegistry.getAllTypes()) {
                    inv: for (IMEInventoryHandler<?> inv : icp.getCellArray(stackType)) {
                        IMEInventory<?> target = inv;
                        while (target instanceof IMEInventoryHandler<?>next) {
                            target = next.getInternal();
                        }

                        if (target instanceof CellInventory<?>ci) {
                            final ItemStack cell = ci.getItemStack();

                            if (igp instanceof IInventory iinv) {
                                for (int i = 0; i < iinv.getSizeInventory(); i++) {
                                    if (iinv.getStackInSlot(i) == cell) {
                                        scan(
                                                cell,
                                                target,
                                                i,
                                                ci.getConfigAEInventory(),
                                                dc,
                                                ci.getStoredItemTypes(),
                                                ci.getTotalItemTypes(),
                                                ci.getUsedBytes(),
                                                ci.getTotalBytes());
                                        continue inv;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        this.trimRecords();
    }

    private void scan(ItemStack is, IMEInventory<?> inv, int slot, IAEStackInventory configInv, DimensionalCoord dc,
            long typesUsed, long typesTotal, long bytesUsed, long bytesTotal) {
        if (configInv != null) {
            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                IAEStack<?> filterStack = configInv.getAEStackInSlot(i);
                if (filterStack == null) continue;
                final Map<IAEStack<?>, List<ScanRecord>> map = this.scanData.get(inv.getStackType());
                final List<ScanRecord> list = map.getOrDefault(filterStack, new ArrayList<>());
                list.add(new ScanRecord(slot, is, typesUsed, typesTotal, bytesUsed, bytesTotal, dc, new ArrayList<>()));
                map.put(filterStack, list);
            }
        }

        if (inv instanceof CellInventory<?>) {
            this.scanCellsData.add(
                    new ScanRecord(
                            slot,
                            is,
                            typesUsed,
                            typesTotal,
                            bytesUsed,
                            bytesTotal,
                            dc,
                            collectTopStoredItems(inv, 5)));
        }
    }

    private static List<IAEStack<?>> collectTopStoredItems(IMEInventory<?> cellInv, int maxItems) {
        final List<IAEStack<?>> entries = new ArrayList<>();
        try {

            final List<IAEStack<?>> allStacks = new ArrayList<>();
            cellInv.getAvailableItems((IItemList) new IAEStackList(), IterationCounter.fetchNewId())
                    .forEach(stack -> allStacks.add((IAEStack<?>) stack));

            allStacks.sort(Comparator.comparingLong(s -> -s.getStackSize()));

            for (int i = 0; i < Math.min(maxItems, allStacks.size()); i++) {
                entries.add(allStacks.get(i));
            }
        } catch (final Exception e) {
            AELog.debug(e);
        }
        return entries;
    }

    public void trimRecords() {
        this.scanData.forEach(
                (key, value) -> value.entrySet().removeIf(stackListEntry -> stackListEntry.getValue().size() < 2));
        this.scanData.entrySet().removeIf(stackType -> stackType.getValue().isEmpty());
    }

    public Map<IAEStackType<?>, Map<IAEStack<?>, List<ScanRecord>>> getScanData() {
        return this.scanData;
    }

    public List<ScanRecord> getScanCellsData() {
        return this.scanCellsData;
    }
}
