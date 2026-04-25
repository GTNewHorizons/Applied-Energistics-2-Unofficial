package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import appeng.container.implementations.ContainerSuperWirelessKit;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.SuperWirelessKitCommand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSuperWirelessToolCommand extends AppEngPacket {

    private final SuperWirelessKitCommand command;

    public PacketSuperWirelessToolCommand(final ByteBuf stream) throws IOException {
        this.command = SuperWirelessKitCommand.read(stream);
    }

    public PacketSuperWirelessToolCommand(final SuperWirelessKitCommand newCommand) throws IOException {
        this.command = newCommand;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        newCommand.write(data);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerSuperWirelessKit swk) swk.processCommand(this.command);
    }
}
