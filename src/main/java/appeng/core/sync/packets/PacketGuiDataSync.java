package appeng.core.sync.packets;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.AEBaseContainer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketGuiDataSync extends AppEngPacket {

    private byte[] data;

    public PacketGuiDataSync(final ByteBuf buf) {
        this.data = new byte[buf.readableBytes()];
        buf.readBytes(this.data);
    }

    public PacketGuiDataSync(Consumer<ByteBuf> dataSync) {
        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());

        dataSync.accept(buf);

        this.configureWrite(buf);
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof AEBaseContainer container) {
            container.receiveSyncData(Unpooled.wrappedBuffer(this.data));
        }
    }
}
