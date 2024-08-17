package appeng.core.sync.packets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketNEIBookmark extends AppEngPacket {

    private ItemStack bookmarkItem;

    // automatic
    public PacketNEIBookmark(final ByteBuf stream) throws IOException {
        final ByteArrayInputStream bytes = new ByteArrayInputStream(stream.array());
        bytes.skip(stream.readerIndex());
        final NBTTagCompound comp = CompressedStreamTools.readCompressed(bytes);
        if (comp != null) {
            this.bookmarkItem = ItemStack.loadItemStackFromNBT(comp);
        }
    }

    // api
    public PacketNEIBookmark(final NBTTagCompound bookmarkItemComp) throws IOException {
        final ByteBuf data = Unpooled.buffer();

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream outputStream = new DataOutputStream(bytes);

        data.writeInt(this.getPacketID());

        CompressedStreamTools.writeCompressed(bookmarkItemComp, outputStream);
        data.writeBytes(bytes.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        bookmarkItem.stackSize = fitStack(player, bookmarkItem);
        final EntityPlayerMP pmp = (EntityPlayerMP) player;
        final Container con = pmp.openContainer;

        if (con instanceof ContainerMEMonitorable monitorable) {
            final IMEMonitor<IAEItemStack> monitor = monitorable.getMonitor();
            if (monitor != null) {
                final IEnergySource energy = monitorable.getPowerSource();
                final BaseActionSource actionSource = monitorable.getActionSource();

                final AEItemStack request = AEItemStack.create(bookmarkItem);
                final IAEItemStack out = Platform.poweredExtraction(energy, monitor, request, actionSource);
                if (out != null) {
                    final InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    ItemStack outItem = out.getItemStack();
                    adp.addItems(outItem);
                }
            }
        }
    }

    private int fitStack(final EntityPlayer player, ItemStack itemStack) {
        // Check if the bookmark item fits fully or partially into the inventory
        // itemStack will always be <= maxStackSize because of how the packets are received
        ItemStack[] inv = player.inventory.mainInventory;
        int remainingStackSize = itemStack.stackSize; // We want to fit this
        for (ItemStack slotStack : inv) {
            if (slotStack == null) { // Empty slot, stack fits completely
                return itemStack.stackSize;
            } else if (slotStack.isItemEqual(itemStack)) {
                remainingStackSize -= itemStack.getMaxStackSize() - slotStack.stackSize;
                if (remainingStackSize < 0) {
                    return itemStack.stackSize;
                }
            }
        }
        if (remainingStackSize == itemStack.getMaxStackSize()) {
            return 0; // Stack didn't fit at all
        }
        return itemStack.stackSize - remainingStackSize;
    }
}
