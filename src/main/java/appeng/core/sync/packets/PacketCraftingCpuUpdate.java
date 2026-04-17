package appeng.core.sync.packets;

import java.io.IOException;
import java.util.Collection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingCpuUpdate extends AppEngPacket {

    private final boolean clearFirst;
    private final int remainingOperations;
    private final CraftingCpuEntry[] entries;

    public PacketCraftingCpuUpdate(final ByteBuf stream) throws IOException {
        this.clearFirst = stream.readBoolean();
        this.remainingOperations = stream.readInt();
        final int count = stream.readInt();
        this.entries = new CraftingCpuEntry[count];
        for (int i = 0; i < count; i++) {
            this.entries[i] = new CraftingCpuEntry(stream);
        }
    }

    public PacketCraftingCpuUpdate(final Collection<CraftingCpuEntry> entries, final boolean clearFirst,
            final int remainingOperations) throws IOException {
        this.clearFirst = clearFirst;
        this.remainingOperations = remainingOperations;
        this.entries = entries.toArray(new CraftingCpuEntry[0]);

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeBoolean(this.clearFirst);
        data.writeInt(this.remainingOperations);
        data.writeInt(this.entries.length);
        for (final CraftingCpuEntry entry : this.entries) {
            entry.writeToPacket(data);
        }
        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        if (currentScreen instanceof GuiCraftingCPU guiCraftingCPU) {
            guiCraftingCPU.postVisualEntryUpdate(this.entries, this.clearFirst, this.remainingOperations);
        }
    }
}
