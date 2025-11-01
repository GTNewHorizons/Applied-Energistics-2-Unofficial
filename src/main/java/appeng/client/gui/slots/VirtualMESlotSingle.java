package appeng.client.gui.slots;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;

public class VirtualMESlotSingle extends VirtualMESlot {

    private IAEStack<?> stack;

    public VirtualMESlotSingle(int x, int y, int slotIndex, IAEStack<?> stack) {
        super(x, y, slotIndex);
        this.stack = stack;
        this.showAmount = false;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        return stack;
    }

    public void setAEStack(@Nullable final IAEStack<?> stack) {
        this.stack = stack;
    }
}
