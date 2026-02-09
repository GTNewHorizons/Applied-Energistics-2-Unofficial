package appeng.core.sync.packets;

import appeng.container.implementations.ContainerSpatialLinkChamber;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;

public class PacketSpatialAction extends AppEngPacket {

    // automatic
    public PacketSpatialAction(final ByteBuf data) {

    }

    // api
    public PacketSpatialAction() {
        final ByteBuf data = Unpooled.buffer();
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerSpatialLinkChamber g){
            g.teleport();
        }
    }
}
