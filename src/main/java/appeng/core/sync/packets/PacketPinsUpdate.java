package appeng.core.sync.packets;

import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IPinsHandler;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPinsUpdate extends AppEngPacket {

    // input.
    @Nullable
    private final IAEStack<?>[] list;
    @Nullable
    private final PinsState state;

    public PacketPinsUpdate(final ByteBuf stream) throws IOException {
        int arrLength = stream.readInt();

        int stateOrdinal = stream.readInt();
        if (stateOrdinal >= 0) state = PinsState.values()[stateOrdinal];
        else state = null;

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

    public PacketPinsUpdate(IAEStack<?>[] arr, PinsState state) throws IOException {
        list = arr;
        this.state = state;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeInt(arr.length);
        data.writeInt(state.ordinal());

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

    public PacketPinsUpdate(PinsState state) throws IOException {
        this.state = state;
        this.list = null;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeInt(-1); // No item array provided
        data.writeInt(state.ordinal());

        this.configureWrite(data);
    }

    public PacketPinsUpdate(IAEItemStack[] arr) throws IOException {
        list = arr;
        this.state = null;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        data.writeInt(arr.length);
        data.writeInt(-1); // No state provided

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
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        if (gs instanceof IPinsHandler iph) {
            if (list != null) iph.setAEPins(list);

            if (state != null) iph.setPinsState(state);
        }
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;
        if (sender.openContainer instanceof IPinsHandler container && state != null) {
            container.setPinsState(state);
        }
    }
}
