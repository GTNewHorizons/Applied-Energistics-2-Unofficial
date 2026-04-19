package appeng.core.sync.packets;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.commons.io.IOUtils;

import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.implementations.CraftingCpuEntry;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
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
        final ByteBuf decompressedEntries = Unpooled.buffer();
        try (final ByteBufInputStream compressedInput = new ByteBufInputStream(stream);
                final GZIPInputStream gzipInput = new GZIPInputStream(compressedInput);
                final ByteBufOutputStream decompressedOutput = new ByteBufOutputStream(decompressedEntries)) {
            IOUtils.copy(gzipInput, decompressedOutput);
        }

        for (int i = 0; i < count; i++) {
            this.entries[i] = new CraftingCpuEntry(decompressedEntries);
        }
    }

    public PacketCraftingCpuUpdate(final Collection<CraftingCpuEntry> entries, final boolean clearFirst,
            final int remainingOperations) throws IOException {
        this.clearFirst = clearFirst;
        this.remainingOperations = remainingOperations;
        this.entries = entries.toArray(new CraftingCpuEntry[0]);

        final ByteBuf data = Unpooled.buffer();
        final ByteBuf serializedEntries = Unpooled.buffer();
        try (final ByteBufOutputStream compressedOutput = new ByteBufOutputStream(data);
                final GZIPOutputStream gzipOutput = new GZIPOutputStream(compressedOutput)) {
            for (final CraftingCpuEntry entry : this.entries) {
                entry.writeToPacket(serializedEntries);
            }
            try (final ByteBufInputStream serializedInput = new ByteBufInputStream(serializedEntries)) {
                IOUtils.copy(serializedInput, gzipOutput);
            }
            gzipOutput.finish();
        }

        final ByteBuf packetData = Unpooled.buffer();
        packetData.writeInt(this.getPacketID());
        packetData.writeBoolean(this.clearFirst);
        packetData.writeInt(this.remainingOperations);
        packetData.writeInt(this.entries.length);
        packetData.writeBytes(data);
        this.configureWrite(packetData);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;
        if (currentScreen instanceof GuiCraftingCPU guiCraftingCPU) {
            guiCraftingCPU.postVisualEntryUpdate(this.entries, this.clearFirst, this.remainingOperations);
        }
    }
}
