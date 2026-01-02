package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerPatternMulti;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPatternMultiSet extends AppEngPacket {

    private final int multi;

    public PacketPatternMultiSet(final ByteBuf stream) {
        this.multi = stream.readInt();
    }

    public PacketPatternMultiSet(int multi) {
        this.multi = multi;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(this.multi);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerPatternMulti cpv) {
            PrimaryGui pGui = cpv.getPrimaryGui();
            assert pGui != null;
            pGui.open(player);
            if (player.openContainer instanceof ContainerPatternTerm cpt) {
                cpt.multiplyOrDivideStacks(multi);
            }
        }
    }
}
