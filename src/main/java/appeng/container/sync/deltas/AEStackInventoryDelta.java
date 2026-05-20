package appeng.container.sync.deltas;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.data.IAEStack;

public final class AEStackInventoryDelta {

    private final int slotIndex;
    private final @Nullable IAEStack<?> stack;

    public AEStackInventoryDelta(final int slotIndex, @Nullable final IAEStack<?> stack) {
        this.slotIndex = slotIndex;
        this.stack = stack == null ? null : stack.copy();
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public @Nullable IAEStack<?> getStack() {
        return this.stack == null ? null : this.stack.copy();
    }
}
