package appeng.container.implementations;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.ExtractionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.prioitylist.PrecisePriorityList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Iterator;

import static appeng.util.Platform.isServer;

public class ContainerSuperMEReplenisher extends AEBaseContainer implements IVirtualSlotHolder {

    private final TileSuperMEReplenisher tile;
    private final IAEStack<?>[] configClientSlot = new IAEStack[11 * 9];

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
                            8+ i * 18,
                            this.getInventoryPlayer()));
            this.addSlotToContainer(
                    new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.WORKBENCH_CELL,
                            cells,
                            3 + i,
                            234,
                            8+ i * 18,
                            this.getInventoryPlayer()));
        }

        bindPlayerInventory(ip, 40, 174);
    }
    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            final IAEStackInventory config = this.tile.getAEInventoryByName(StorageName.CONFIG);
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.tile.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) {
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }
    }

    public IAEStackInventory getConfig() {
        return this.tile.getAEInventoryByName(StorageName.CONFIG);
    }
}
