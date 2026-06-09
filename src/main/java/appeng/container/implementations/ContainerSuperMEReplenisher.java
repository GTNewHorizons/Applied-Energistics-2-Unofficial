package appeng.container.implementations;

import static appeng.util.Platform.isServer;

import java.util.Objects;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.SecurityPermissions;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.SyncCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.container.sync.handlers.LongSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.items.AEBaseCell;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.Platform;
import appeng.util.item.IAEStackList;
import io.netty.buffer.ByteBuf;

public class ContainerSuperMEReplenisher extends AEBaseContainer implements IVirtualSlotSource {

    public static class Stored implements IGuiPacketWritable {

        public final IAEStackList list;

        public Stored(final IAEStackList list) {
            this.list = list;
        }

        public Stored(ByteBuf buf) {
            this.list = new IAEStackList(true);
            final int itemListSize = buf.readInt();
            for (int i = 0; i < itemListSize; i++) this.list.add(Platform.readStackByte(buf));
        }

        public static Stored copy(final Stored s) {
            return new Stored(s.list);
        }

        @Override
        public void writeToPacket(ByteBuf buf) {
            buf.writeInt(this.list.size());
            this.list.forEach(o -> Platform.writeStackByte(o, buf));
        }
    }

    private final TileSuperMEReplenisher tile;

    private final LongSyncHandler totalBytes;
    private final LongSyncHandler usedBytes;

    public final IntSyncHandler tickRate;
    public final IntSyncHandler threshold;

    public final AEStackInventorySyncHandler config;
    public final ObjectSyncHandler<Stored> storedData;

    public boolean needUpdate = false;

    public ContainerSuperMEReplenisher(final InventoryPlayer ip, final TileSuperMEReplenisher te) {
        super(ip, te);
        this.tile = te;

        final SyncRegistrar sync = this.syncRegistrar();
        this.totalBytes = sync.longS2C("totalBytes");
        this.usedBytes = sync.longS2C("usedBytes");

        this.tickRate = sync.intSync("tickRate").onClientChange((oldValue, newValue) -> this.needUpdate = true)
                .onServerChange((oldValue, newValue) -> this.tile.setTickRate(newValue));

        this.threshold = sync.intSync("threshold").onClientChange((oldValue, newValue) -> this.needUpdate = true)
                .onServerChange((oldValue, newValue) -> this.tile.setThreshold((double) newValue / 100));

        this.config = sync.aeStackInventory("config", this.getConfig()).onServerChange(o -> this.extraSync());

        this.storedData = sync.object(
                "storedData",
                SyncCodecs.packetWritable(Stored.class, Stored::new, Stored::copy, Objects::equals),
                this.tile.getStorage());

        final AppEngInternalInventory cells = this.tile.getCellInventory();
        for (int i = 0; i < 3; i++) {
            this.addSlotToContainer(
                    new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.WORKBENCH_CELL,
                            cells,
                            i,
                            8,
                            8 + i * 18,
                            this.getInventoryPlayer()));
            this.addSlotToContainer(
                    new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.WORKBENCH_CELL,
                            cells,
                            3 + i,
                            196,
                            8 + i * 18,
                            this.getInventoryPlayer()));
        }

        bindPlayerInventory(ip, 22, 158);
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < 6) {
            final ItemStack current = this.tile.getCellInventory().getStackInSlot(slotId);
            if (current != null) {
                if (current.getItem() instanceof AEBaseCell currentCell) {
                    final long currentBytes = currentCell.getBytesLong(current);
                    if (!(this.totalBytes.get() - this.usedBytes.get() >= currentBytes)) {
                        final ItemStack hand = player.inventory.getItemStack();
                        if (hand != null && hand.getItem() instanceof AEBaseCell handCell) {
                            if (!((this.totalBytes.get() + handCell.getBytesLong(hand) - this.usedBytes.get()
                                    >= currentBytes))) {
                                return null;
                            }
                        } else return null;
                    }
                }
            }
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (isServer()) {
            this.totalBytes.set(this.tile.getTotalBytes());
            this.usedBytes.set(this.tile.getUsedBytes());
            this.tickRate.set(this.tile.getTickRate());
            this.threshold.set((int) (this.tile.getThreshold() * 100));
            this.storedData.set(this.tile.getStorage());
        }

        super.detectAndSendChanges();
    }

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        final IAEStackInventory config = this.tile.getAEInventoryByName(invName);
        config.putAEStackInSlot(slotId, aes);
    }

    public IAEStackInventory getConfig() {
        return this.tile.getAEInventoryByName(StorageName.CONFIG);
    }

    public long getTotalBytes() {
        return this.totalBytes.get();
    }

    public long getUsedBytes() {
        return this.usedBytes.get();
    }

    // need because inventory can not always accept result, maybe I will use zero void module instead
    public void extraSync() {
        this.config.requestFullResync();
    }
}
