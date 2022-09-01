package appeng.core.sync.packets;

import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class PacketNEIDragClick extends AppEngPacket {

    private final ItemStack dragItem;
    private final int slotIndex;

    public PacketNEIDragClick(ItemStack dragItem, int slotIndex) {
        this.dragItem = dragItem;
        this.slotIndex = slotIndex;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        if (this.dragItem != null) {
            data.writeBoolean(true);
            ByteBufUtils.writeItemStack(data, this.dragItem);
        } else {
            data.writeBoolean(false);
        }

        data.writeInt(this.slotIndex);
        this.configureWrite(data);
    }

    public PacketNEIDragClick(final ByteBuf stream) {
        if (stream.readBoolean()) {
            this.dragItem = ByteBufUtils.readItemStack(stream);
        } else {
            this.dragItem = null;
        }
        this.slotIndex = stream.readInt();
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;
        Slot slot = c.getSlot(slotIndex);
        slot.putStack(dragItem);
    }
}
