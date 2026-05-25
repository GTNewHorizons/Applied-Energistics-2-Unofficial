package appeng.core.sync.packets;

import static appeng.util.Platform.readStackByte;
import static appeng.util.Platform.writeStackByte;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPatternValueSet extends AppEngPacket {

    private final static int UPDATE_ONLY_OFFSET = 1_000_000;

    private final IAEStack<?> aes;
    private final StorageName invName;
    private int slotIndex;

    public PacketPatternValueSet(final ByteBuf stream) {
        this.aes = readStackByte(stream);
        this.invName = StorageName.values()[stream.readInt()];
        this.slotIndex = stream.readInt();
    }

    public PacketPatternValueSet(IAEStack<?> aes, StorageName invName, int slotIndex) {
        this(aes, invName, slotIndex, false);
    }

    public PacketPatternValueSet(IAEStack<?> aes, StorageName invName, int slotIndex, boolean onlyUpdate) {
        this.aes = aes;
        this.invName = invName;
        this.slotIndex = onlyUpdate ? slotIndex + UPDATE_ONLY_OFFSET : slotIndex;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        writeStackByte(aes, data);
        data.writeInt(invName.ordinal());
        data.writeInt(slotIndex);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof AEBaseContainer bc) {
            if (this.slotIndex >= UPDATE_ONLY_OFFSET) {
                this.slotIndex -= UPDATE_ONLY_OFFSET;
            } else {
                PrimaryGui pGui = bc.getPrimaryGui();
                assert pGui != null;
                pGui.open(player);
            }

            if (player.openContainer instanceof IVirtualSlotSource vss) {
                vss.updateVirtualSlot(invName, slotIndex, aes);
            }
        }
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof IVirtualSlotSource vss)
            vss.updateVirtualSlot(this.invName, this.slotIndex, this.aes);
    }
}
