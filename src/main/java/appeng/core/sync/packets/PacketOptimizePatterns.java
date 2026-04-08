package appeng.core.sync.packets;

import java.util.HashMap;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerOptimizePatterns;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

public class PacketOptimizePatterns extends AppEngPacket {
	static public final int MULTIPLIER_BITS = 6;// multiplier <= (0b111110), take 6 bits
	static public final long MULTIPLIER_BIT_MASK=(1L << MULTIPLIER_BITS) - 1;// low 6 bits = 1
	static public final long HASH_SIGN_BIT_MASK=1L << MULTIPLIER_BITS;// 7th bit = 1
	
	
    HashMap<Integer, Integer> hashCodeToMultiplier = new HashMap<>();

    // automatic
    public PacketOptimizePatterns(final ByteBuf data) {
        int size = data.readInt();

        for (int i = 0; i < size; i++) {
            hashCodeToMultiplier.put(data.readInt(), data.readInt());
        }
    }

    // api
    public PacketOptimizePatterns(Object2IntMap<IAEStack<?>> multipliersMap) {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(multipliersMap.size());
        for (var entry : multipliersMap.object2IntEntrySet()) {
            long encoded = entry.getKey().getStackSize();
            data.writeInt((int) (encoded >> (MULTIPLIER_BITS+1)) * ((encoded & HASH_SIGN_BIT_MASK) == 0 ? 1 : -1));
            data.writeInt(entry.getIntValue());
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
