package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.util.AEColor;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.items.tools.powered.ToolColorApplicator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketColorSelect extends AppEngPacket {

    // Constructor for sending (Client -> Server)
    private final AEColor color;

    // Receiving Constructor (Server Side)
    public PacketColorSelect(final ByteBuf stream) {
        int ord = stream.readInt();
        if (ord >= 0 && ord < AEColor.values().length) {
            this.color = AEColor.values()[ord];
        } else {
            this.color = AEColor.Transparent;
        }
    }

    // Sending Constructor (Client Side) - "API" style
    public PacketColorSelect(final AEColor color) {
        this.color = color;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(color.ordinal());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ToolColorApplicator tool) {
            tool.setColor(held, this.color);
        }
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        // Not used (Client -> Server only)
    }
}
