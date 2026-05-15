package appeng.container.implementations;

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.SecurityPermissions;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerSuperMEReplenisher extends AEBaseContainer implements IVirtualSlotHolder {

    private final TileSuperMEReplenisher tile;
    private final IAEStack<?>[] configClientSlot = new IAEStack[3 * 9];

    public ContainerSuperMEReplenisher(final InventoryPlayer ip, final TileSuperMEReplenisher te) {
        super(ip, te);
        this.tile = te;

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
                            232,
                            8 + i * 18,
                            this.getInventoryPlayer()));
        }

        bindPlayerInventory(ip, 40, 174);
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (slotId == 0 && player.inventory.getItemStack() == null) {
            return null;
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            final IAEStackInventory config = this.tile.getAEInventoryByName(StorageName.CONFIG);
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }

        super.detectAndSendChanges();
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.tile.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
    }

    public IAEStackInventory getConfig() {
        return this.tile.getAEInventoryByName(StorageName.CONFIG);
    }
}
