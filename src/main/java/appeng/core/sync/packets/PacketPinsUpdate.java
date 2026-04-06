package appeng.core.sync.packets;

import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IPinsHandler;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPinsUpdate extends AppEngPacket {

    @Nullable
    private final IAEStack<?>[] list;
    private final int craftingRowsOrdinal;
    private final int playerRowsOrdinal;

    public PacketPinsUpdate(final ByteBuf stream) throws IOException {
        int arrLength = stream.readInt();
        craftingRowsOrdinal = stream.readInt();
        playerRowsOrdinal = stream.readInt();

        if (arrLength < 0) {
            list = null;
            return;
        }

        list = new IAEStack<?>[arrLength];
        for (int i = 0; i < list.length; i++) {
            if (stream.readBoolean()) {
                list[i] = Platform.readStackByte(stream);
            }
        }
    }

    public PacketPinsUpdate(IAEStack<?>[] arr, PinsRows craftingRows, PinsRows playerRows) throws IOException {
        list = arr;
        this.craftingRowsOrdinal = craftingRows.ordinal();
        this.playerRowsOrdinal = playerRows.ordinal();

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(arr.length);
        data.writeInt(craftingRowsOrdinal);
        data.writeInt(playerRowsOrdinal);

        for (IAEStack<?> aeItemStack : arr) {
            if (aeItemStack != null) {
                data.writeBoolean(true);
                Platform.writeStackByte(aeItemStack, data);
            } else {
                data.writeBoolean(false);
            }
        }
        this.configureWrite(data);
    }

    /** State-only update (e.g. button click). */
    public PacketPinsUpdate(PinsRows craftingRows, PinsRows playerRows) throws IOException {
        list = null;
        this.craftingRowsOrdinal = craftingRows.ordinal();
        this.playerRowsOrdinal = playerRows.ordinal();

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(-1);
        data.writeInt(craftingRowsOrdinal);
        data.writeInt(playerRowsOrdinal);
        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        if (gs instanceof IPinsHandler iph) {
            int craftOrd = Math.max(0, Math.min(craftingRowsOrdinal, PinsRows.values().length - 1));
            int playerOrd = Math.max(0, Math.min(playerRowsOrdinal, PinsRows.values().length - 1));
            iph.setPinsRows(PinsRows.values()[craftOrd], PinsRows.values()[playerOrd]);
            if (list != null) iph.setAEPins(list);
        }
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;
        if (sender.openContainer instanceof IPinsHandler container) {
            int craftOrd = Math.max(0, Math.min(craftingRowsOrdinal, PinsRows.values().length - 1));
            int playerOrd = Math.max(0, Math.min(playerRowsOrdinal, PinsRows.values().length - 1));
            container.setPinsRows(PinsRows.values()[craftOrd], PinsRows.values()[playerOrd]);
        }
    }
}
