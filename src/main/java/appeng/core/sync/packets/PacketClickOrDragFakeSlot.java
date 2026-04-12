package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.container.slot.SlotFake;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketClickOrDragFakeSlot extends AppEngPacket {

    private final ItemStack dragItem;
    private final int slotIndex;
    private final boolean overwrite;

    public PacketClickOrDragFakeSlot(ItemStack dragItem, int slotIndex, boolean overwrite) {
        this.dragItem = dragItem;
        this.slotIndex = slotIndex;
        this.overwrite = overwrite;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeBoolean(overwrite);

        if (this.dragItem != null) {
            data.writeBoolean(true);
            ByteBufUtils.writeItemStack(data, this.dragItem);
        } else {
            data.writeBoolean(false);
        }

        data.writeInt(this.slotIndex);
        this.configureWrite(data);
    }

    public PacketClickOrDragFakeSlot(final ByteBuf stream) {
        this.overwrite = stream.readBoolean();

        if (stream.readBoolean()) {
            this.dragItem = ByteBufUtils.readItemStack(stream);
        } else {
            this.dragItem = null;
        }
        this.slotIndex = stream.readInt();
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (slotIndex < 0 || slotIndex >= c.inventorySlots.size()) return;
        Slot slot = c.inventorySlots.get(slotIndex);
        if (!(slot instanceof SlotFake)) return;

        ItemStack stackInSlot = slot.getStack();
        if (!this.overwrite && stackInSlot != null) {
            if (this.dragItem != null && stackInSlot.isItemEqual(this.dragItem)
                    && ItemStack.areItemStackTagsEqual(slot.getStack(), this.dragItem)) {
                stackInSlot.stackSize = Math
                        .min(this.dragItem.stackSize + stackInSlot.stackSize, stackInSlot.getMaxStackSize());
                slot.putStack(stackInSlot);
                return;
            } else if (this.dragItem == null) {
                stackInSlot.stackSize -= 1;
                if (stackInSlot.stackSize <= 0) {
                    slot.putStack(null);
                } else {
                    slot.putStack(stackInSlot);
                }
                return;
            }
        }

        slot.putStack(dragItem);
    }
}
