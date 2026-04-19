package appeng.container.implementations;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.IAEStack;
import appeng.util.ScheduledReason;
import io.netty.buffer.ByteBuf;

public class CraftingCpuEntry {

    private final IAEStack<?> stack;
    private final long storedAmount;
    private final long activeAmount;
    private final long pendingAmount;
    private final ScheduledReason scheduledReason;

    public CraftingCpuEntry(@NotNull final IAEStack<?> stack, final long storedAmount, final long activeAmount,
            final long pendingAmount, final ScheduledReason scheduledReason) {
        this.stack = stack;
        this.storedAmount = storedAmount;
        this.activeAmount = activeAmount;
        this.pendingAmount = pendingAmount;
        this.scheduledReason = scheduledReason == null ? ScheduledReason.UNDEFINED : scheduledReason;
    }

    public CraftingCpuEntry(final ByteBuf buffer) throws IOException {
        this.stack = IAEStack.fromPacketGeneric(buffer);
        this.storedAmount = buffer.readLong();
        this.activeAmount = buffer.readLong();
        this.pendingAmount = buffer.readLong();
        final int reasonOrdinal = buffer.readInt();
        this.scheduledReason = reasonOrdinal >= 0 && reasonOrdinal < ScheduledReason.VALUES.length
                ? ScheduledReason.VALUES[reasonOrdinal]
                : ScheduledReason.UNDEFINED;
    }

    public void writeToPacket(final ByteBuf buffer) throws IOException {
        IAEStack.writeToPacketGeneric(buffer, this.stack);
        buffer.writeLong(this.storedAmount);
        buffer.writeLong(this.activeAmount);
        buffer.writeLong(this.pendingAmount);
        buffer.writeInt(this.scheduledReason.ordinal());
    }

    public IAEStack<?> getStack() {
        return this.stack;
    }

    public IAEStack<?> getVisualStack() {
        final IAEStack<?> visualStack = this.stack.copy();
        visualStack.setStackSize(this.getTotalAmount());
        return visualStack;
    }

    public long getStoredAmount() {
        return this.storedAmount;
    }

    public long getActiveAmount() {
        return this.activeAmount;
    }

    public long getPendingAmount() {
        return this.pendingAmount;
    }

    public ScheduledReason getScheduledReason() {
        return this.scheduledReason;
    }

    public boolean hasStoredAmount() {
        return this.storedAmount > 0;
    }

    public boolean hasActiveAmount() {
        return this.activeAmount > 0;
    }

    public boolean hasPendingAmount() {
        return this.pendingAmount > 0;
    }

    public boolean isVisibleWhenHideStoredEnabled() {
        return this.hasActiveAmount() || this.hasPendingAmount();
    }

    public boolean matchesSearch(final String needle) {
        return needle == null || needle.isEmpty() || this.stack.getDisplayName().toLowerCase().contains(needle);
    }

    public long getTotalAmount() {
        return this.storedAmount + this.activeAmount + this.pendingAmount;
    }

    public static IAEStack<?> normalizeStack(final IAEStack<?> stack) {
        final IAEStack<?> normalizedStack = stack.copy();
        normalizedStack.setStackSize(1);
        return normalizedStack;
    }
}
