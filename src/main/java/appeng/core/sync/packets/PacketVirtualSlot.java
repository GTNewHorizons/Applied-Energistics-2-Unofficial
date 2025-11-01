package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class PacketVirtualSlot extends AppEngPacket {

    final StorageName invName;
    final Int2ObjectMap<IAEStack<?>> slotStacks;

    public PacketVirtualSlot(final ByteBuf buf) throws IOException {
        this.invName = StorageName.values()[buf.readInt()];
        this.slotStacks = new Int2ObjectOpenHashMap<>();

        final int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            final int slot = buf.readInt();
            if (buf.readBoolean()) {
                this.slotStacks.put(slot, Platform.readStackByte(buf));
            } else {
                this.slotStacks.put(slot, null);
            }
        }
    }

    public PacketVirtualSlot(final StorageName invName, final Int2ObjectMap<IAEStack<?>> slotStacks) {
        this.invName = invName;
        this.slotStacks = null;

        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());

        buf.writeInt(invName.ordinal());
        buf.writeInt(slotStacks.size());
        for (Int2ObjectMap.Entry<IAEStack<?>> entry : slotStacks.int2ObjectEntrySet()) {
            buf.writeInt(entry.getIntKey());
            IAEStack<?> stack = entry.getValue();
            buf.writeBoolean(stack != null);
            if (stack != null) {
                Platform.writeStackByte(stack, buf);
            }
        }

        this.configureWrite(buf);
    }

    public PacketVirtualSlot(final StorageName invName, final int slotIndex, final IAEStack<?> stack) {
        this.invName = invName;
        this.slotStacks = null;

        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());

        buf.writeInt(invName.ordinal());
        buf.writeInt(1);
        buf.writeInt(slotIndex);
        buf.writeBoolean(stack != null);
        if (stack != null) {
            Platform.writeStackByte(stack, buf);
        }

        this.configureWrite(buf);
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof IVirtualSlotHolder container) {
            container.receiveSlotStacks(this.invName, this.slotStacks);
            return;
        }
        if (Minecraft.getMinecraft().currentScreen instanceof IVirtualSlotHolder container) {
            container.receiveSlotStacks(this.invName, this.slotStacks);
        }
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof IVirtualSlotHolder container) {
            container.receiveSlotStacks(this.invName, this.slotStacks);
        }
    }
}
