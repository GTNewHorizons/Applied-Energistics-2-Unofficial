package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.implementations.GuiSuperWirelessKit;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSuperWirelessToolData extends AppEngPacket {

    private final NBTTagCompound data;

    // automatic.
    public PacketSuperWirelessToolData(final ByteBuf stream) throws IOException {
        this.data = ByteBufUtils.readTag(stream);
    }

    // api
    public PacketSuperWirelessToolData(final NBTTagCompound newData) throws IOException {

        this.data = newData;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        ByteBufUtils.writeTag(data, newData);

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiSuperWirelessKit gsw) {
            gsw.setData(this.data);
        }
    }
}
