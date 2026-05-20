package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.sync.SyncEndpoint;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketContainerSync extends AppEngPacket {

    private final int windowId;
    private final byte[] data;

    public PacketContainerSync(final ByteBuf buf) {
        this.windowId = buf.readInt();
        this.data = new byte[buf.readableBytes()];
        buf.readBytes(this.data);
    }

    public PacketContainerSync(final int windowId, final ByteBuf data) {
        this.windowId = windowId;
        this.data = null;

        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());
        buf.writeInt(windowId);
        buf.writeBytes(data, data.readerIndex(), data.readableBytes());
        this.configureWrite(buf);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof AEBaseContainer container && container.windowId == this.windowId) {
            container.receiveSyncData(SyncEndpoint.SERVER, Unpooled.wrappedBuffer(this.data));
        }
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof AEBaseContainer container && container.windowId == this.windowId) {
            container.receiveSyncData(SyncEndpoint.CLIENT, Unpooled.wrappedBuffer(this.data));
        }
    }
}
