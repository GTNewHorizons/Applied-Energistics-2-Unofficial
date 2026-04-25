package appeng.core.sync.packets;

import java.io.IOException;
import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.implementations.GuiSuperWirelessKit;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.SuperWirelessToolDataObject;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketSuperWirelessToolData extends AppEngPacket {

    private final NBTTagCompound nData;
    private final ArrayList<SuperWirelessToolDataObject> wData;

    // automatic.
    public PacketSuperWirelessToolData(final ByteBuf stream) throws IOException {
        this.nData = ByteBufUtils.readTag(stream);
        this.wData = SuperWirelessToolDataObject.readAsList(stream);
    }

    // api
    public PacketSuperWirelessToolData(final NBTTagCompound nData, final ArrayList<SuperWirelessToolDataObject> wData)
            throws IOException {
        this.nData = nData;
        this.wData = wData;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        ByteBufUtils.writeTag(data, nData);

        SuperWirelessToolDataObject.writeAsList(wData, data);

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        if (gs instanceof GuiSuperWirelessKit gsw) gsw.setData(this.nData, this.wData);
    }
}
