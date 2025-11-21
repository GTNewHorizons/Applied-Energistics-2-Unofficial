package appeng.container.implementations;

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.parts.automation.PartSharedItemBus;
import appeng.tile.inventory.IAEStackInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerBusIO extends ContainerUpgradeable implements IVirtualSlotHolder {

    public final IAEStack<?>[] virtualSlotsClient = new IAEStack<?>[9];
    private final PartSharedItemBus<?> bus;

    public ContainerBusIO(final InventoryPlayer ip, final PartSharedItemBus<?> te) {
        super(ip, te);
        this.bus = te;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.updateVirtualSlots(StorageName.NONE, this.bus.getAEInventoryByName(StorageName.NONE), virtualSlotsClient);
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory storage = this.bus.getAEInventoryByName(StorageName.NONE);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            storage.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
        if (isServer()) {
            this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, storage, virtualSlotsClient);
        }
    }
}
