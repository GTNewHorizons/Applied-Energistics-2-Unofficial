package appeng.client.gui.slots;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.client.StorageName;
import appeng.tile.inventory.IAEStackInventory;

public class VirtualMEPatternSlot extends VirtualMESlot {

    private final IAEStackInventory inventory;

    public VirtualMEPatternSlot(int x, int y, IAEStackInventory inventory, int slotIndex) {
        super(x, y, slotIndex);
        this.inventory = inventory;
    }

    @Override
    public @Nullable IAEStack<?> getAEStack() {
        return this.inventory.getAEStackInSlot(this.getSlotIndex());
    }

    public StorageName getStorageName() {
        return this.inventory.getStorageName();
    }
}
