package appeng.container.slot;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.VirtualMESlot;
import appeng.tile.inventory.IAEStackInventory;

public class VirtualMESlotPattern extends VirtualMESlot {

    private final IAEStackInventory inventory;

    public VirtualMESlotPattern(int x, int y, IAEStackInventory inventory, int slotIndex) {
        super(x, y, null, slotIndex);
        this.inventory = inventory;
    }

    @Nullable
    public IAEStack<?> getAEStack() {
        return this.inventory.getAEStackInSlot(this.getSlotIndex());
    }

    public void setAEStack(IAEStack<?> aes) {
        this.inventory.putAEStackInSlot(this.getSlotIndex(), aes);
    }
}
