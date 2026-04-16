package appeng.helpers;

import java.util.ArrayList;
import java.util.List;

import appeng.api.config.ReshufflePhase;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;

public class ReshuffleReport implements IGuiPacketWritable {

    public final AEStackTypeFilter typeFilters;
    public final boolean includeSubnets;

    public final ReshufflePhase phase;

    public final int extractedTypes, injectedTypes, beforeTypes, afterTypes;
    public final long startTime, endTime;
    public final double extractedItems, injectedItems, beforeItems, afterItems;

    public final IItemList<IAEStack<?>> cantExtract, cantInject, beforeSnapshot, afterSnapshot, stackLookup;

    public final List<ItemChange> lostItems = new ArrayList<>();
    public final List<ItemChange> gainedItems = new ArrayList<>();

    public static class ItemChange {

        public final IAEStack<?> stack;
        public final long beforeCount;
        public final long afterCount;
        public final long difference;
        public final byte changeType;

        public ItemChange(IAEStack<?> stack, long beforeCount, long afterCount) {
            this.stack = stack;
            this.beforeCount = beforeCount;
            this.afterCount = afterCount;
            this.difference = afterCount - beforeCount;

            if (difference > 0) {
                this.changeType = 1;
            } else if (difference < 0) {
                this.changeType = -1;
            } else {
                this.changeType = 0;
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

    public ReshuffleReport(final AEStackTypeFilter typeFilters, final boolean includeSubnets,
            final ReshufflePhase phase, final int extractedTypes, final int injectedTypes, final long startTime,
            final long endTime, final double extractedItems, final double injectedItems,
            final IItemList<IAEStack<?>> cantExtract, final IItemList<IAEStack<?>> cantInject,
            final IItemList<IAEStack<?>> beforeSnapshot, IItemList<IAEStack<?>> afterSnapshot,
            IItemList<IAEStack<?>> stackLockup, final double beforeItems, final double afterItems) {
        this.typeFilters = typeFilters;
        this.includeSubnets = includeSubnets;
        this.phase = phase;
        this.extractedTypes = extractedTypes;
        this.injectedTypes = injectedTypes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.extractedItems = extractedItems;
        this.injectedItems = injectedItems;
        this.cantExtract = cantExtract;
        this.cantInject = cantInject;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.stackLookup = stackLockup;
        this.beforeItems = beforeItems;
        this.afterItems = afterItems;
        this.beforeTypes = this.afterTypes = 0;
        if (this.phase == ReshufflePhase.DONE) generateReport();
    }

    // For IGuiPacketWritable
    public ReshuffleReport(final ByteBuf buf) {
        this.stackLookup = this.beforeSnapshot = this.afterSnapshot = null;

        this.typeFilters = new AEStackTypeFilter(buf);
        this.includeSubnets = buf.readBoolean();

        this.phase = ReshufflePhase.values()[buf.readInt()];
        this.extractedTypes = buf.readInt();
        this.injectedTypes = buf.readInt();

        this.startTime = buf.readLong();
        this.endTime = buf.readLong();

        this.extractedItems = buf.readDouble();
        this.injectedItems = buf.readDouble();

        this.cantExtract = new IAEStackList();
        final int sizeCantExtract = buf.readInt();
        for (int i = 0; i < sizeCantExtract; i++) {
            this.cantExtract.add(Platform.readStackByte(buf));
        }

        this.cantInject = new IAEStackList();
        final int sizeCantInject = buf.readInt();
        for (int i = 0; i < sizeCantInject; i++) {
            this.cantInject.add(Platform.readStackByte(buf));
        }

        if (this.phase == ReshufflePhase.DONE) {
            this.beforeTypes = buf.readInt();
            this.afterTypes = buf.readInt();
            this.beforeItems = buf.readDouble();
            this.afterItems = buf.readDouble();

            final int sizeGainedItems = buf.readInt();
            for (int i = 0; i < sizeGainedItems; i++) {
                this.gainedItems.add(ItemChange.read(buf));
            }

            final int sizeLostItems = buf.readInt();
            for (int i = 0; i < sizeLostItems; i++) {
                this.lostItems.add(ItemChange.read(buf));
            }
        } else {
            this.beforeTypes = this.afterTypes = 0;
            this.beforeItems = this.afterItems = 0;
        }
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        this.typeFilters.writeToPacket(buf);
        buf.writeBoolean(this.includeSubnets);

        buf.writeInt(this.phase.ordinal());
        buf.writeInt(this.extractedTypes);
        buf.writeInt(this.injectedTypes);

        buf.writeLong(this.startTime);
        buf.writeLong(this.endTime);

        buf.writeDouble(this.extractedItems);
        buf.writeDouble(this.injectedItems);

        buf.writeInt(this.cantExtract.size());
        this.cantExtract.forEach(stack -> Platform.writeStackByte(stack, buf));

        buf.writeInt(this.cantInject.size());
        this.cantInject.forEach(stack -> Platform.writeStackByte(stack, buf));

        if (this.phase == ReshufflePhase.DONE) {
            buf.writeInt(this.beforeSnapshot.size());
            buf.writeInt(this.afterSnapshot.size());
            buf.writeDouble(this.beforeItems);
            buf.writeDouble(this.afterItems);

            buf.writeInt(this.gainedItems.size());
            this.gainedItems.forEach(s -> s.write(buf));
            buf.writeInt(this.lostItems.size());
            this.lostItems.forEach(s -> s.write(buf));
        }
    }

    public void generateReport() {
        for (IAEStack<?> lookup : this.stackLookup) {
            final IAEStack<?> before = this.beforeSnapshot.findPrecise(lookup);
            final IAEStack<?> after = this.afterSnapshot.findPrecise(lookup);

            ItemChange change = new ItemChange(
                    lookup,
                    before == null ? 0 : before.getStackSize(),
                    after == null ? 0 : after.getStackSize());

            switch (change.changeType) {
                case 1 -> this.gainedItems.add(change);
                case -1 -> this.lostItems.add(change);
            }
        }

        this.lostItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
        this.gainedItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
    }

    public int getProgressPercent() {
        return switch (this.phase) {
            case EXTRACTION -> 20;
            case INJECTION -> 20 + (80 * (this.injectedTypes / this.extractedTypes));
            case DONE -> 100;
            default -> 0;
        };
    }
}
