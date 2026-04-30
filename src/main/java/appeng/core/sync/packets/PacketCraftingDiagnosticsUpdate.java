package appeng.core.sync.packets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingDiagnosticsUpdate extends AppEngPacket {

    private final List<CraftingGridCache.DiagnosticRowView> rows;

    public PacketCraftingDiagnosticsUpdate(final ByteBuf stream) {
        final int rowCount = stream.readInt();
        this.rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            final IAEStack<?> stack = Platform.readStackByte(stream);
            final long totalProduced = stream.readLong();
            final long elapsedTimeMillis = stream.readLong();
            final long sampleCount = stream.readLong();
            this.rows
                    .add(new CraftingGridCache.DiagnosticRowView(stack, totalProduced, elapsedTimeMillis, sampleCount));
        }
    }

    public PacketCraftingDiagnosticsUpdate(final List<CraftingGridCache.DiagnosticRowView> rows) {
        this.rows = rows;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(rows.size());
        for (final CraftingGridCache.DiagnosticRowView row : rows) {
            Platform.writeStackByte(row.stack, data);
            data.writeLong(row.totalProduced);
            data.writeLong(row.elapsedTimeMillis);
            data.writeLong(row.sampleCount);
        }
        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiCraftingDiagnosticTerminal gui) {
            gui.postUpdate(this.rows);
        }
    }
}
