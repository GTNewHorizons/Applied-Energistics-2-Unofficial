package appeng.core.sync.packets;

import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEItemStack;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IPinsHandler;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPinsUpdate extends AppEngPacket {

    // input.
    @Nullable
    private final IAEItemStack[] list;

    public PacketPinsUpdate(final ByteBuf stream) throws IOException {
        IAEItemStack[] newList = new IAEItemStack[9];

        for (int i = 0; i < 9; i++) {
            if (stream.readBoolean()) {
                newList[i] = AEItemStack.loadItemStackFromPacket(stream);
            }
        }

        list = newList;
    }

    public PacketPinsUpdate(IAEItemStack[] arr) throws IOException {
        list = arr;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        for (IAEItemStack aeItemStack : arr) {
            if (aeItemStack != null) {
                data.writeBoolean(true);
                aeItemStack.writeToPacket(data);
            } else {
                data.writeBoolean(false);
            }
        }

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof IPinsHandler iph) {
            iph.setAEPins(list);
        }
    }
}
