package appeng.core.sync.packets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternItemRenamer;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.IContainerCraftingPacket;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPatternItemRenamer extends AppEngPacket {

    private final GuiBridge originGui;
    private String name = "";
    private final int valueIndex;

    public PacketPatternItemRenamer(final ByteBuf stream) {
        originGui = GuiBridge.values()[stream.readInt()];
        valueIndex = stream.readInt();

        final DataInputStream dis = new DataInputStream(
                new ByteArrayInputStream(stream.array(), stream.readerIndex(), stream.readableBytes()));
        try {
            name = dis.readUTF();
        } catch (IOException ignored) {}
    }

    public PacketPatternItemRenamer(int originalGui, String newName, int newValueIndex) {
        originGui = GuiBridge.values()[originalGui];
        name = newName;
        valueIndex = newValueIndex;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(getPacketID());
        data.writeInt(originalGui);
        data.writeInt(valueIndex);

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);

        try {
            dos.writeUTF(name);
        } catch (IOException ignored) {}

        data.writeBytes(bos.toByteArray());

        configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerPatternItemRenamer cpv) {
            final Object target = cpv.getTarget();
            if (target instanceof IGridHost) {
                final ContainerOpenContext context = cpv.getOpenContext();
                if (context != null) {
                    final TileEntity te = context.getTile();
                    Platform.openGUI(player, te, cpv.getOpenContext().getSide(), originGui);
                    if (player.openContainer instanceof IContainerCraftingPacket) {
                        Slot slot = player.openContainer.getSlot(valueIndex);
                        if (slot != null && slot.getHasStack()) {
                            ItemStack nextStack = slot.getStack().copy();
                            nextStack.setRepairCost(2);
                            nextStack.setStackDisplayName(name);
                            slot.putStack(nextStack);
                        }
                    }
                }
            }
        }
    }
}
