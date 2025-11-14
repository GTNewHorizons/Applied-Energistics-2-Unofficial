package appeng.client.gui.slots;

import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.tile.inventory.IAEStackInventory;

public class VirtualMEPatternSlot extends VirtualMEPhantomSlot {

    public VirtualMEPatternSlot(int x, int y, IAEStackInventory inventory, int slotIndex) {
        super(x, y, inventory, slotIndex);
        this.showAmount = true;
    }

    @Override
    public void handleMouseClicked(boolean acceptItem, boolean acceptFluid, boolean isExtraAction, int mouseButton) {
        if (mouseButton == 2) { // middle click
            if (this.getAEStack() != null) {
                if (isExtraAction) {
                    NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PATTERN_ITEM_RENAMER));
                } else {
                    NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PATTERN_VALUE_AMOUNT));
                }
            }
        }

        super.handleMouseClicked(acceptItem, acceptFluid, isExtraAction, mouseButton);
    }
}
