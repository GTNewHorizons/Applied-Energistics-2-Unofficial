package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import baubles.api.BaublesApi;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketOpenPortableCellWorkbench extends AppEngPacket {

    public PacketOpenPortableCellWorkbench(final ByteBuf stream) {}

    public PacketOpenPortableCellWorkbench() {
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (Platform.isBaublesLoaded) {
            final IInventory baubles = BaublesApi.getBaubles(player);
            if (baubles != null) {
                for (int slot = 0; slot < baubles.getSizeInventory(); slot++) {
                    if (this.isPortableCellWorkbench(baubles.getStackInSlot(slot))) {
                        Platform.openGUI(
                                player,
                                null,
                                null,
                                GuiBridge.GUI_CELL_WORKBENCH,
                                Platform.baublesSlotsOffset + slot);
                        return;
                    }
                }
            }
        }

        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (this.isPortableCellWorkbench(player.inventory.getStackInSlot(slot))) {
                Platform.openGUI(player, null, null, GuiBridge.GUI_CELL_WORKBENCH, slot);
                return;
            }
        }
    }

    public boolean isPortableCellWorkbench(final ItemStack stack) {
        return stack != null && AEApi.instance().definitions().items().portableCellWorkbench().isSameAs(stack);
    }
}
