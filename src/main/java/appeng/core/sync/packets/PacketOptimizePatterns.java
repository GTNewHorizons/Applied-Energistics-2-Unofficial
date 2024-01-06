package appeng.core.sync.packets;

import java.util.HashMap;
import java.util.Map.Entry;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerOptimizePatterns;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketOptimizePatterns extends AppEngPacket {

    HashMap<Integer, Integer> hashCodeToMultiplier = new HashMap<>();

    // automatic
    public PacketOptimizePatterns(final ByteBuf data) {
        int size = data.readInt();

        for (int i = 0; i < size; i++) {
            hashCodeToMultiplier.put(data.readInt(), data.readInt());
        }
    }

    // api
    public PacketOptimizePatterns(HashMap<IAEItemStack, Integer> multipliersMap) {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(multipliersMap.size());
        for (Entry<IAEItemStack, Integer> entry : multipliersMap.entrySet()) {
            data.writeInt(entry.getKey().hashCode());
            data.writeInt(entry.getValue());
        }

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerOptimizePatterns cop) {
            cop.optimizePatterns(hashCodeToMultiplier);
        }
    }

}
