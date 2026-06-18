package appeng.core.sync.packets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.me.cache.ItemFlowGridCache.FlowRate;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketFlowRates extends AppEngPacket {

    private final Map<IAEStack<?>, FlowRate> rates;

    public PacketFlowRates(final ByteBuf stream) throws IOException {
        final int size = stream.readInt();
        this.rates = new HashMap<>();
        for (int i = 0; i < size; i++) {
            final IAEStack<?> stack = Platform.readStackByte(stream);
            final long in = stream.readLong();
            final long out = stream.readLong();
            if (stack != null) {
                this.rates.put(stack, new FlowRate(in, out));
            }
        }
    }

    public PacketFlowRates(final Map<IAEStack<?>, FlowRate> rates) throws IOException {
        this.rates = rates;

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(this.getPacketID());
        buffer.writeInt(rates.size());
        for (final Map.Entry<IAEStack<?>, FlowRate> entry : rates.entrySet()) {
            Platform.writeStackByte(entry.getKey(), buffer);
            buffer.writeLong(entry.getValue().in());
            buffer.writeLong(entry.getValue().out());
        }

        this.configureWrite(buffer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiMEMonitorable monitorable) {
            monitorable.updateFlowRates(this.rates);
        }
    }
}
