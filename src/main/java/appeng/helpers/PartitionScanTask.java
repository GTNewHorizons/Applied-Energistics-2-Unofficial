package appeng.helpers;

import java.util.ArrayList;
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
import appeng.api.util.DimensionalCoord;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.CellInventory;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class PartitionScanTask implements IGuiPacketWritable {

    private final Map<IAEStackType<?>, Map<IAEStack<?>, List<PartitionRecord>>> partitions = new IdentityHashMap<>();

    public PartitionScanTask() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.partitions.put(type, new HashMap<>());
        }
    }

    // For IGuiPacketWritable
    public PartitionScanTask(final ByteBuf buf) {
        final int sizePartitions = buf.readInt();
        for (int i = 0; i < sizePartitions; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            final Map<IAEStack<?>, List<PartitionRecord>> stackPartitionsMap = new HashMap<>();
            final int mapSize = buf.readInt();

            for (int j = 0; j < mapSize; j++) {
                final IAEStack<?> aes = Platform.readStackByte(buf);
                final List<PartitionRecord> partitionRecords = new ArrayList<>();
                final int recordSize = buf.readInt();

                for (int k = 0; k < recordSize; k++) partitionRecords.add(PartitionRecord.read(buf));

                stackPartitionsMap.put(aes, partitionRecords);
            }

            partitions.put(AEStackTypeRegistry.getType(typeId), stackPartitionsMap);
        }
    }

    @Override
    public void writeToPacket(ByteBuf buf) {
        buf.writeInt(partitions.size());
        for (Entry<IAEStackType<?>, Map<IAEStack<?>, List<PartitionRecord>>> entry : partitions.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey().getId());
            final Map<IAEStack<?>, List<PartitionRecord>> map = entry.getValue();
            buf.writeInt(map.size());

            for (Entry<IAEStack<?>, List<PartitionRecord>> stackEntry : map.entrySet()) {
                Platform.writeStackByte(stackEntry.getKey(), buf);
                buf.writeInt(stackEntry.getValue().size());
                stackEntry.getValue().forEach(record -> record.write(buf));
            }
        }
    }

    public static final class PartitionRecord {

        public final int slot;
        public final String displayName;
        public final int typesUsed;
        public final int x;
        public final int y;
        public final int z;
        public final int dim;

        public PartitionRecord(int slot, String cellDisplayName, int typesUsed, DimensionalCoord dc) {
            this.slot = slot;
            this.displayName = cellDisplayName;
            this.typesUsed = typesUsed;
            this.x = dc.x;
            this.y = dc.y;
            this.z = dc.z;
            this.dim = dc.getDimension();
        }

        public void write(ByteBuf buf) {
            buf.writeInt(slot);
            ByteBufUtils.writeUTF8String(buf, displayName);
            buf.writeInt(typesUsed);
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(dim);
        }

        public static PartitionRecord read(ByteBuf buf) {
            return new PartitionRecord(
                    buf.readInt(),
                    ByteBufUtils.readUTF8String(buf),
                    buf.readInt(),
                    new DimensionalCoord(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
        }
    }

    public void scanGrid(final IGrid grid) {
        final GridStorageCache gsc = grid.getCache(IStorageGrid.class);
        for (ICellProvider icp : gsc.getActiveCellProviders()) {
            if (icp instanceof IGridProxyable igp) {
                final DimensionalCoord dc = igp.getLocation();

                if (igp instanceof PartStorageBus sb) {
                    scanPartition(sb.getPrimaryGuiIcon(), -1, sb.getAEInventoryByName(StorageName.CONFIG), dc, -1);
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
                                        scanPartition(
                                                cell,
                                                i,
                                                ci.getConfigAEInventory(),
                                                dc,
                                                (int) ci.getStoredItemTypes());
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

    private void scanPartition(ItemStack is, int slot, IAEStackInventory configInv, DimensionalCoord dc,
            int storedTypes) {
        if (configInv != null) {
            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                final IAEStack<?> filterStack = configInv.getAEStackInSlot(i);
                if (filterStack == null) continue;
                final Map<IAEStack<?>, List<PartitionRecord>> map = this.partitions.get(filterStack.getStackType());
                final List<PartitionRecord> list = map.getOrDefault(filterStack, new ArrayList<>());
                list.add(new PartitionRecord(slot, Platform.getItemDisplayName(is), storedTypes, dc));
                map.put(filterStack, list);
            }
        }
    }

    public void trimRecords() {
        this.partitions.forEach(
                (key, value) -> value.entrySet().removeIf(stackListEntry -> stackListEntry.getValue().size() < 2));
        this.partitions.entrySet().removeIf(stackType -> stackType.getValue().isEmpty());
    }

    public Map<IAEStackType<?>, Map<IAEStack<?>, List<PartitionRecord>>> getPartitions() {
        return this.partitions;
    }
}
