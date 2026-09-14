package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;

import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketRequestResync extends AppEngPacket {

    private int x;
    private int y;
    private int z;

    // automatic.
    public PacketRequestResync(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
    }

    // api
    public PacketRequestResync(final int x, final int y, final int z) {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        player.worldObj.markBlockForUpdate(x, y, z);
    }
}
