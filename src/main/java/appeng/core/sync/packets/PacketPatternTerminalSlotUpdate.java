package appeng.core.sync.packets;

import static appeng.util.Platform.readStackByte;
import static appeng.util.Platform.writeStackByte;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPatternTerminalSlotUpdate extends AppEngPacket {

    public final IAEStack<?> slotItem;

    public final int slotId;

    public final String invName;

    // automatic.
    public PacketPatternTerminalSlotUpdate(final ByteBuf stream) throws IOException {
        this.invName = ByteBufUtils.readUTF8String(stream);

        this.slotId = stream.readInt();

        this.slotItem = this.readItem(stream);
    }

    // api
    public PacketPatternTerminalSlotUpdate(final String invName, final int slotId, final IAEStack<?> slotItem)
            throws IOException {
        this.invName = invName;

        this.slotItem = slotItem;

        this.slotId = slotId;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        ByteBufUtils.writeUTF8String(data, invName);

        data.writeInt(slotId);

        this.writeItem(slotItem, data);

        this.configureWrite(data);
    }

    private IAEStack<?> readItem(final ByteBuf stream) throws IOException {
        final boolean hasItem = stream.readBoolean();

        if (hasItem) {
            return readStackByte(stream);
        }
        return null;
    }

    private void writeItem(final IAEStack<?> slotItem, final ByteBuf data) throws IOException {
        if (slotItem == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            writeStackByte(slotItem, data);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiPatternTerm gpt) {
            gpt.setPatternSlot(this.invName, this.slotId, this.slotItem);
        }
    }
}
