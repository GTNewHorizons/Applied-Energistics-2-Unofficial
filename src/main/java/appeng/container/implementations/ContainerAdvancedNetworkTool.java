package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.container.slot.SlotRestrictedInput;

public class ContainerAdvancedNetworkTool extends ContainerNetworkTool {

    public ContainerAdvancedNetworkTool(final InventoryPlayer ip, final INetworkTool te) {
        super(ip, te);
    }

    @Override
    protected void setupSlotContainer(InventoryPlayer ip, INetworkTool te) {
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                this.addSlotToContainer(
                        (new SlotRestrictedInput(
                                SlotRestrictedInput.PlacableItemType.UPGRADES,
                                te,
                                y * 5 + x,
                                62 - 18 + x * 18,
                                37 - 18 + y * 18,
                                this.getInventoryPlayer())));
            }
        }
        this.bindPlayerInventory(ip, 0, 120);
    }
}
