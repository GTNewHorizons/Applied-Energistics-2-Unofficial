package appeng.client.gui.slots;

import static appeng.client.gui.implementations.GuiMEMonitorable.keyBindPickBlockAction;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.core.sync.packets.PacketSwitchGuis;

public class VirtualMEPhantomSlotPrecise extends VirtualMEPhantomSlot {

    public VirtualMEPhantomSlotPrecise(int x, int y, AEStackInventorySyncHandler syncHandler, int slotIndex,
            TypeAcceptPredicate acceptType) {
        super(x, y, syncHandler, slotIndex, acceptType);
        this.showAmount = true;
    }

    @Override
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        if (mouseButton == keyBindPickBlockAction) {
            if (this.getAEStack() != null) {
                if (!isExtraAction) {
                    NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PATTERN_VALUE_AMOUNT));
                    NetworkHandler.instance.sendToServer(
                            new PacketPatternValueSet(
                                    this.getAEStack(),
                                    this.getStorageName(),
                                    this.getSlotIndex() + 1_000_000));
                }
            }
        }

        super.handleMouseClicked(itemStack, isExtraAction, mouseButton);
    }
}
