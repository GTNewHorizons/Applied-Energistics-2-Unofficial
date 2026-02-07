package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotRestrictedInput;
import appeng.tile.spatial.TileSpatialLinkChamber;

public class ContainerSpatialLinkChamber extends AEBaseContainer {

    public ContainerSpatialLinkChamber(final InventoryPlayer ip, final TileSpatialLinkChamber te) {
        super(ip, te);

        this.addSlotToContainer(
                (new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.SPATIAL_STORAGE_CELLS,
                        te,
                        0,
                        80,
                        37,
                        this.getInventoryPlayer())).setStackLimit(1));

        this.bindPlayerInventory(ip, 0, 166 - /* height of player inventory */ 82);
    }
}
