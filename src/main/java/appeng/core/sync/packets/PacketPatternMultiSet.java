package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternMulti;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPatternMultiSet extends AppEngPacket {

    private final GuiBridge originGui;
    private final int multi;

    public PacketPatternMultiSet(final ByteBuf stream) {
        this.originGui = GuiBridge.values()[stream.readInt()];
        this.multi = stream.readInt();
    }

    public PacketPatternMultiSet(int originalGui, int multi) {
        this.originGui = GuiBridge.values()[originalGui];
        this.multi = multi;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(originalGui);
        data.writeInt(this.multi);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerPatternMulti cpv) {
            final Object target = cpv.getTarget();
            if (target instanceof IGridHost) {
                final ContainerOpenContext context = cpv.getOpenContext();
                if (context != null) {
                    final TileEntity te = context.getTile();
                    Platform.openGUI(player, te, cpv.getOpenContext().getSide(), originGui);
                    if (player.openContainer instanceof ContainerPatternTerm cpt) {
                        cpt.multiplyOrDivideStacks(multi);
                    } else if (player.openContainer instanceof ContainerPatternTermEx cpt) {
                        cpt.multiplyOrDivideStacks(multi);
                    }
                }
            }
        }
    }
}
