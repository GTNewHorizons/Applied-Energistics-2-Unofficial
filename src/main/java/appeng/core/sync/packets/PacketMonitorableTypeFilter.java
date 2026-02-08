package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class PacketMonitorableTypeFilter extends AppEngPacket {

    public final Reference2BooleanMap<IAEStackType<?>> map;

    public PacketMonitorableTypeFilter(final ByteBuf buf) throws IOException {
        map = new Reference2BooleanOpenHashMap<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.map.put(type, true);
        }

        final int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            final boolean value = buf.readBoolean();
            final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
            if (type != null) {
                this.map.put(type, value);
            }
        }
    }

    public PacketMonitorableTypeFilter(Reference2BooleanMap<IAEStackType<?>> map) throws IOException {
        this.map = null;

        final ByteBuf buf = Unpooled.buffer();

        buf.writeInt(this.getPacketID());
        buf.writeInt(map.size());
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : map.reference2BooleanEntrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey().getId());
            buf.writeBoolean(entry.getBooleanValue());
        }

        this.configureWrite(buf);
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiMEMonitorable monitorable) {
            monitorable.updateTypeFilters(this.map);
        }
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerMEMonitorable container) {
            container.updateTypeFilters(this.map);
        }
    }
}
