package appeng.client.gui.slots;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;

public class VirtualMEMonitorableSlot extends VirtualMESlot {

    protected final IDisplayRepo repo;

    public VirtualMEMonitorableSlot(int x, int y, IDisplayRepo repo, int slotIndex) {
        super(x, y, slotIndex);
        this.repo = repo;
        this.showAmountAlways = true;
        this.showCraftableText = true;
        this.showCraftableIcon = true;
    }

    @Override
    public @Nullable IAEStack<?> getAEStack() {
        return this.repo.getReferenceStack(this.slotIndex);
    }

    @Override
    protected void drawNEIOverlay() {}
}
