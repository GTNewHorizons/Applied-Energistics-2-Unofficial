package appeng.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;
import appeng.util.item.IAEStackList;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class ReshuffleReport implements IGuiPacketWritable {

    public static class ItemChange {

        public final IAEStack<?> stack;
        public final long beforeCount;
        public final long afterCount;
        public final long difference;
        public final ChangeType changeType;

        public ItemChange(IAEStack<?> stack, long beforeCount, long afterCount) {
            this.stack = stack;
            this.beforeCount = beforeCount;
            this.afterCount = afterCount;
            this.difference = afterCount - beforeCount;

            if (difference > 0) {
                this.changeType = ChangeType.GAINED;
            } else if (difference < 0) {
                this.changeType = ChangeType.LOST;
            } else {
                this.changeType = ChangeType.UNCHANGED;
            }
        }

        public void write(ByteBuf out) {
            Platform.writeStackByte(this.stack, out);
            out.writeLong(this.beforeCount);
            out.writeLong(this.afterCount);
        }

        public static ItemChange read(ByteBuf in) {
            return new ItemChange(Platform.readStackByte(in), in.readLong(), in.readLong());
        }
    }

    public enum ChangeType {
        GAINED,
        LOST,
        UNCHANGED
    }

    public final AEStackTypeFilter types;
    public final boolean voidProtection;
    public final long startTime;
    public long endTime = 0;

    public int totalItemTypesBefore = 0;
    public int totalItemTypesAfter = 0;

    public long totalStacksBefore = 0;
    public long totalStacksAfter = 0;

    public int itemsProcessed = 0;
    public int itemsSkipped = 0;

    public final IItemList<IAEStack<?>> beforeSnapshot = new IAEStackList(), afterSnapshot = new IAEStackList(),
            stackLookup = new IAEStackList();

    public final IItemList<IAEStack<?>> skippedItemsList = new IAEStackList();

    public int itemsGained = 0;
    public int itemsLost = 0;
    public int itemsUnchanged = 0;
    public long totalGained = 0;
    public long totalLost = 0;

    public final List<ItemChange> lostItems = new ArrayList<>();
    public final List<ItemChange> gainedItems = new ArrayList<>();

    public ReshuffleReport(final AEStackTypeFilter types, final boolean voidProtection) {
        this.types = types;
        this.voidProtection = voidProtection;
        this.includeSubnets = includeSubnets;
        this.startTime = System.currentTimeMillis();
    }

    // For IGuiPacketWritable
    public ReshuffleReport(final ByteBuf buf) {
        this.types = new AEStackTypeFilter();
        final int sizeTypes = buf.readInt();
        for (int i = 0; i < sizeTypes; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            if (buf.readBoolean()) {
                this.types.toggle(AEStackTypeRegistry.getType(typeId));
            }
        }

        this.voidProtection = buf.readBoolean();
        this.startTime = buf.readLong();
        this.endTime = buf.readLong();

        this.totalItemTypesBefore = buf.readInt();
        this.totalItemTypesAfter = buf.readInt();

        this.totalStacksBefore = buf.readLong();
        this.totalStacksAfter = buf.readLong();

        this.itemsProcessed = buf.readInt();
        this.itemsSkipped = buf.readInt();

        final int sizeSkippedItemsList = buf.readInt();
        for (int i = 0; i < sizeSkippedItemsList; i++) {
            this.skippedItemsList.add(Platform.readStackByte(buf));
        }

        final int sizeLostItems = buf.readInt();
        for (int i = 0; i < sizeLostItems; i++) {
            this.lostItems.add(ItemChange.read(buf));
        }

        final int sizeGainedItems = buf.readInt();
        for (int i = 0; i < sizeGainedItems; i++) {
            this.gainedItems.add(ItemChange.read(buf));
        }
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        buf.writeInt(AEStackTypeRegistry.getAllTypes().size());
        for (Entry<IAEStackType<?>, Boolean> entry : this.types.getImmutableFilters().entrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey().getId());
            buf.writeBoolean(entry.getValue());
        }

        buf.writeBoolean(this.voidProtection);
        buf.writeLong(this.startTime);
        buf.writeLong(this.endTime);

        buf.writeInt(this.totalItemTypesBefore);
        buf.writeInt(this.totalItemTypesAfter);

        buf.writeLong(this.totalStacksBefore);
        buf.writeLong(this.totalStacksAfter);

        buf.writeInt(this.itemsProcessed);
        buf.writeInt(this.itemsSkipped);

        buf.writeInt(this.skippedItemsList.size());
        this.skippedItemsList.forEach(stack -> Platform.writeStackByte(stack, buf));

        buf.writeInt(this.lostItems.size());
        this.lostItems.forEach(stack -> stack.write(buf));

        buf.writeInt(this.gainedItems.size());
        this.gainedItems.forEach(stack -> stack.write(buf));
    }

    public void snapshotBefore(Map<IAEStackType<?>, IMEMonitor<?>> monitors) {
        beforeSnapshot.resetStatus();
        stackLookup.resetStatus();
        totalStacksBefore = 0;
        totalItemTypesBefore = 0;

        for (Entry<IAEStackType<?>, Boolean> entry : this.types.getImmutableFilters().entrySet()) {
            if (!entry.getValue()) continue;
            IMEMonitor<?> monitor = monitors.get(entry.getValue());
            if (monitor == null) continue;

            for (IAEStack<?> stack : monitor.getStorageList()) {
                if (stack != null && stack.getStackSize() > 0) {
                    beforeSnapshot.add(stack);
                    stackLookup.add(stack);
                    totalStacksBefore += stack.getStackSize();
                    totalItemTypesBefore++;
                }
            }
        }
    }

    public void generateReport(Map<IAEStackType<?>, IMEMonitor<?>> monitors, int processed, int skipped,
            List<IAEStack<?>> skippedStacks) {
        this.endTime = System.currentTimeMillis();
        this.itemsExtracted = extracted;
        this.itemsInjected = injected;
        this.itemsSkipped = skipped;
        this.skippedItemsList.resetStatus();
        if (skippedStacks != null) {
            for (IAEStack<?> s : skippedStacks) {
                this.skippedItemsList.add(s.copy());
            }
        }

        totalStacksAfter = 0;
        totalItemTypesAfter = 0;

        for (Entry<IAEStackType<?>, Boolean> entry : this.types.getImmutableFilters().entrySet()) {
            if (!entry.getValue()) continue;
            IMEMonitor<?> monitor = monitors.get(entry.getValue());
            if (monitor == null) continue;

            final IItemList<?> storageList = monitor.getStorageList();
            for (IAEStack<?> stack : storageList) {
                if (stack != null && stack.getStackSize() > 0) {
                    this.afterSnapshot.add(stack);
                    if (stackLookup.findPrecise(stack) == null) stackLookup.add(stack);
                    totalStacksAfter += stack.getStackSize();
                    totalItemTypesAfter++;
                }
            }
        }

        for (IAEStack<?> lookup : stackLookup) {
            final IAEStack<?> before = beforeSnapshot.findPrecise(lookup);
            final IAEStack<?> after = afterSnapshot.findPrecise(lookup);

            ItemChange change = new ItemChange(
                    lookup,
                    before == null ? 0 : before.getStackSize(),
                    after == null ? 0 : after.getStackSize());

            switch (change.changeType) {
                case GAINED:
                    itemsGained++;
                    totalGained += change.difference;
                    gainedItems.add(change);
                    break;
                case LOST:
                    itemsLost++;
                    totalLost += Math.abs(change.difference);
                    lostItems.add(change);
                    break;
                case UNCHANGED:
                    itemsUnchanged++;
                    break;
            }
        }

        lostItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
        gainedItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
    }
}
