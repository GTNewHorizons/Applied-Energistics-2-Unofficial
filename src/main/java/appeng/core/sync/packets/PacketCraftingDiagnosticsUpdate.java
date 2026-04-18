package appeng.core.sync.packets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingDiagnosticsUpdate extends AppEngPacket {

    private final NBTTagCompound tag;
    private final ByteBuf data;

    public PacketCraftingDiagnosticsUpdate(final ByteBuf stream) throws IOException {
        this.data = null;

        final GZIPInputStream gzReader = new GZIPInputStream(new InputStream() {

            @Override
            public int read() throws IOException {
                if (stream.readableBytes() <= 0) {
                    return -1;
                }

                return stream.readByte() & 0xff;
            }
        });

        final DataInputStream inStream = new DataInputStream(gzReader);
        this.tag = CompressedStreamTools.read(inStream);
        inStream.close();
    }

    public PacketCraftingDiagnosticsUpdate(final NBTTagCompound tag) throws IOException {
        this.tag = tag;
        this.data = Unpooled.buffer(2048);
        this.data.writeInt(this.getPacketID());

        final GZIPOutputStream compressFrame = new GZIPOutputStream(new OutputStream() {

            @Override
            public void write(final int value) throws IOException {
                PacketCraftingDiagnosticsUpdate.this.data.writeByte(value);
            }
        });

        CompressedStreamTools.write(tag, new DataOutputStream(compressFrame));
        compressFrame.close();
        this.configureWrite(this.data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiCraftingDiagnosticTerminal gui) {
            gui.postUpdate(this.tag);
        }
    }
}
