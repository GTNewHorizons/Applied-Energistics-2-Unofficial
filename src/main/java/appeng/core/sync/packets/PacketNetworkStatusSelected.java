package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.implementations.ContainerNetworkStatus;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * @author MCTBL
 */
public class PacketNetworkStatusSelected extends AppEngPacket {

    private boolean isConsume;

    public PacketNetworkStatusSelected(final ByteBuf stream) {
        this.isConsume = stream.readBoolean();
    }

    public PacketNetworkStatusSelected(final boolean isConsume) throws IOException {
        this.isConsume = isConsume;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeBoolean(this.isConsume);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerNetworkStatus cns) {
            cns.setConsume(isConsume);
        }
    }

}
