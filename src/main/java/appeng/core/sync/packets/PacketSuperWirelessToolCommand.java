package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import appeng.container.implementations.ContainerSuperWirelessKit;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSuperWirelessToolCommand extends AppEngPacket {
    ;

    private final NBTTagCompound command;

    public PacketSuperWirelessToolCommand(final ByteBuf stream) throws IOException {
        this.command = ByteBufUtils.readTag(stream);
    }

    public PacketSuperWirelessToolCommand(final NBTTagCompound newCommand) throws IOException {
        this.command = newCommand;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        ByteBufUtils.writeTag(data, newCommand);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerSuperWirelessKit swk) {
            swk.processCommand(this.command);
        }
    }
}
