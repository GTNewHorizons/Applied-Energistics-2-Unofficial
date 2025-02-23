package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.item.AEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketUpdateAESlot extends AppEngPacket {

    public final IAEItemStack slotItem;

    public final int slotId;

    // automatic.
    public PacketUpdateAESlot(final ByteBuf stream) throws IOException {

        this.slotId = stream.readInt();

        this.slotItem = this.readItem(stream);
    }

    // api
    public PacketUpdateAESlot(final int slotId, final IAEItemStack slotItem) throws IOException {

        this.slotItem = slotItem;

        this.slotId = slotId;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeInt(slotId);

        this.writeItem(slotItem, data);

        this.configureWrite(data);
    }

    private IAEItemStack readItem(final ByteBuf stream) throws IOException {
        final boolean hasItem = stream.readBoolean();

        if (hasItem) {
            return AEItemStack.loadItemStackFromPacket(stream);
        }

        return null;
    }

    private void writeItem(final IAEItemStack slotItem, final ByteBuf data) throws IOException {
        if (slotItem == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            slotItem.writeToPacket(data);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiPatternTerm gpt) {
            gpt.setSlotAE(this.slotId, this.slotItem);
        }
    }
}
