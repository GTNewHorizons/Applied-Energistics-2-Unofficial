package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IInterfaceHost;
import appeng.tile.networking.TileCableBus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketToggleInterfaceVisibility extends AppEngPacket {

    private final int x, y, z, dim, side;

    public PacketToggleInterfaceVisibility(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
        this.dim = stream.readInt();
        this.side = stream.readInt();
    }

    public PacketToggleInterfaceVisibility(int x, int y, int z, int dim, int side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.side = side;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeInt(dim);
        data.writeInt(side);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!(player.openContainer instanceof AEBaseContainer)) return;

        var server = MinecraftServer.getServer();
        if (server == null) return;

        var world = server.worldServerForDimension(this.dim);
        if (world == null) return;

        final TileEntity te = world.getTileEntity(this.x, this.y, this.z);
        final IInterfaceHost host;
        if (te instanceof TileCableBus cableBus) {
            final IPart part = cableBus.getPart(ForgeDirection.getOrientation(this.side));
            if (!(part instanceof IInterfaceHost)) return;
            host = (IInterfaceHost) part;
        } else if (te instanceof IInterfaceHost) {
            host = (IInterfaceHost) te;
        } else {
            return;
        }

        final var cm = host.getInterfaceDuality().getConfigManager();
        final YesNo current = (YesNo) cm.getSetting(Settings.INTERFACE_TERMINAL);
        cm.putSetting(Settings.INTERFACE_TERMINAL, current == YesNo.YES ? YesNo.NO : YesNo.YES);
        host.saveChanges();

        if (player.openContainer instanceof ContainerInterfaceTerminal container) {
            container.scheduleUpdate();
        }
    }
}
