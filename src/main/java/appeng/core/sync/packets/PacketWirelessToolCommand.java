package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.implementations.ContainerWirelessKit;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.WirelessKitCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketWirelessToolCommand extends AppEngPacket {

    private final WirelessKitCommand command;

    public PacketWirelessToolCommand(final ByteBuf stream) throws IOException {
        this.command = WirelessKitCommand.read(stream);
    }

    public PacketWirelessToolCommand(final WirelessKitCommand newCommand) throws IOException {
        this.command = newCommand;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        newCommand.write(data);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerWirelessKit swk) swk.processCommand(this.command);
    }
}
