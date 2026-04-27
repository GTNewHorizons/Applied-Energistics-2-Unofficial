package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerCraftingDiagnosticTerminal;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingDiagnosticReset extends AppEngPacket {

    private final IAEStack<?> stack;

    public PacketCraftingDiagnosticReset(final ByteBuf stream) {
        this.stack = Platform.readStackByte(stream);
    }

    public PacketCraftingDiagnosticReset(final IAEStack<?> stack) {
        this.stack = stack == null ? null : stack.copy();

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        Platform.writeStackByte(this.stack, data);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftingDiagnosticTerminal container) {
            container.clearDiagnostics(this.stack);
        }
    }
}
