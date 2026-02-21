package appeng.client.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.tile.inventory.IAEStackInventory;

public class VirtualMEPatternSlot extends VirtualMEPhantomSlot {

    public VirtualMEPatternSlot(int x, int y, IAEStackInventory inventory, int slotIndex,
            TypeAcceptPredicate acceptType) {
        super(x, y, inventory, slotIndex, acceptType);
        this.showAmount = true;
    }

    @Override
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        if (mouseButton == 3) { // keyBindPickBlock
            if (this.getAEStack() != null) {
                if (isExtraAction) {
                    NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PATTERN_ITEM_RENAMER));
                } else {
                    NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PATTERN_VALUE_AMOUNT));
                }
            }
        }

        super.handleMouseClicked(itemStack, isExtraAction, mouseButton);
    }
}
