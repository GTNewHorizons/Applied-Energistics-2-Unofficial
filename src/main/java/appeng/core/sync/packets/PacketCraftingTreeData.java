package appeng.core.sync.packets;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.commons.io.IOUtils;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.crafting.v2.CraftingJobV2;
import appeng.util.Platform;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

public class PacketCraftingTreeData extends AppEngPacket {

    private static final int CHUNK_SIZE = 1024 * 1024; // Send data in 1MiB chunks

    // Store data for later deserialization when we get access to the World object
    private ByteBuf receivedData = null;

    @SuppressWarnings("unused")
    public PacketCraftingTreeData(final ByteBuf stream) throws IOException {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            throw new UnsupportedOperationException("A client tried to use a client-only packet on the server.");
        }
        receivedData = stream.slice();
    }

    private PacketCraftingTreeData(final ByteBuf chunkData, int chunkId, int totalChunks) {
        final ByteBuf output = Unpooled.buffer(12 + chunkData.readableBytes());
        output.writeInt(this.getPacketID());
        output.writeInt(chunkId);
        output.writeInt(totalChunks);
        output.writeBytes(chunkData);
        this.configureWrite(output);
    }

    public static List<PacketCraftingTreeData> createChunks(final CraftingJobV2 job) {
        final ByteBuf jobData = job.serialize();
        // Compress with GZIP
        final ByteBuf output = Unpooled.buffer(jobData.readableBytes() + 4);
        try (final ByteBufOutputStream bbos = new ByteBufOutputStream(output);
                final GZIPOutputStream gzos = new GZIPOutputStream(bbos);
                final ByteBufInputStream bbis = new ByteBufInputStream(jobData)) {
            IOUtils.copy(bbis, gzos);
            gzos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Split into chunks
        final int chunkCount = (int) Platform.ceilDiv(output.readableBytes(), CHUNK_SIZE);
        final ArrayList<PacketCraftingTreeData> chunks = new ArrayList<>(chunkCount);
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            final int start = CHUNK_SIZE * chunk;
            final int end = Math.min(start + CHUNK_SIZE, output.readableBytes());
            final int len = end - start;
            chunks.add(new PacketCraftingTreeData(output.slice(start, len), chunk, chunkCount));
        }
        if (AEConfig.instance.isFeatureEnabled(AEFeature.DebugLogging)) {
            AELog.info(
                    "Crafting tree packet raw size %d, compressed %d, chunk count %d",
                    jobData.writerIndex(),
                    output.readableBytes(),
                    chunks.size());
        }
        return chunks;
    }

    // Store partially received packets client-side
    private static final WeakHashMap<EntityPlayer, ByteBuf[]> chunkStorage = new WeakHashMap<>();

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (receivedData == null) {
            return;
        }
        final int chunkId = receivedData.readInt();
        final int totalChunks = receivedData.readInt();
        if (totalChunks <= 0 || chunkId < 0 || chunkId >= totalChunks) {
            AELog.warn("Invalid chunked crafting tree packet received from server: Chunk %d/%d", chunkId, totalChunks);
            return;
        }
        if (totalChunks == 1) {
            onFullClientData(receivedData.slice().order(ByteOrder.LITTLE_ENDIAN), player);
        } else {
            boolean packetComplete = false;
            ByteBuf[] storage;
            synchronized (chunkStorage) {
                storage = chunkStorage.get(player);
                if (storage == null || storage.length != totalChunks) {
                    storage = new ByteBuf[totalChunks];
                    chunkStorage.put(player, storage);
                }
                storage[chunkId] = receivedData.slice().order(ByteOrder.LITTLE_ENDIAN);
                if (Arrays.stream(storage).noneMatch(Objects::isNull)) {
                    chunkStorage.remove(player);
                    packetComplete = true;
                }
            }
            if (packetComplete) {
                ByteBuf combined = Unpooled.wrappedBuffer(storage).order(ByteOrder.LITTLE_ENDIAN);
                onFullClientData(combined, player);
            }
        }
    }

    private static void onFullClientData(ByteBuf data, EntityPlayer player) {
        final ByteBuf decompressedData = Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN);
        try (final ByteBufOutputStream bbos = new ByteBufOutputStream(decompressedData);
                final ByteBufInputStream bbis = new ByteBufInputStream(data);
                final GZIPInputStream gzis = new GZIPInputStream(bbis)) {
            IOUtils.copy(gzis, bbos);
            bbos.flush();
        } catch (IOException e) {
            AELog.error(e, "Could not decompress the serialized crafting tree.");
            return;
        }
        final CraftingJobV2 deserialized;
        try {
            deserialized = CraftingJobV2.deserialize(player.worldObj, decompressedData);
        } catch (Exception e) {
            AELog.error(e, "Could not deserialize crafting tree sent by the server.");
            return;
        }
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        if (gs instanceof GuiCraftConfirm) {
            ((GuiCraftConfirm) gs).setJobTree(deserialized);
        }
    }
}
