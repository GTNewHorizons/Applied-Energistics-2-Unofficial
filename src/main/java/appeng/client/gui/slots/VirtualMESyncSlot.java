package appeng.client.gui.slots;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;

public class VirtualMESyncSlot extends VirtualMEPhantomSlot {

    @FunctionalInterface
    public interface StackGetter {

        @Nullable
        IAEStack<?> get();
    }

    @FunctionalInterface
    public interface StackSetter {

        void set(@Nullable IAEStack<?> stack);
    }

    private final StackGetter stackGetter;
    private final StackSetter stackSetter;

    public VirtualMESyncSlot(int x, int y, IAEStackInventory inventory, int slotIndex, TypeAcceptPredicate acceptType,
            StackGetter stackGetter, StackSetter stackSetter) {
        super(x, y, inventory, slotIndex, acceptType);
        this.stackGetter = stackGetter;
        this.stackSetter = stackSetter;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        return this.stackGetter.get();
    }

    @Override
    protected void setAEStack(@Nullable final IAEStack<?> stack) {
        this.stackSetter.set(stack);
    }
}
