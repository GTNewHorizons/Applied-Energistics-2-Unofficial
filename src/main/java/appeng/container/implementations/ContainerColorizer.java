package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.container.AEBaseContainer;
import appeng.items.contents.ColorizerObj;
import appeng.util.Platform;

public class ContainerColorizer extends AEBaseContainer {

    private final ColorizerObj toolInv;

    public ContainerColorizer(final InventoryPlayer ip, final ColorizerObj te) {
        super(ip, te);
        this.toolInv = te;

        this.lockPlayerInventorySlot(ip.currentItem);
        this.bindPlayerInventory(ip, 0, 237 - 82);
    }

    @Override
    public void detectAndSendChanges() {
        final ItemStack currentItem = this.getPlayerInv().getCurrentItem();

        if (currentItem != this.toolInv.getItemStack()) {
            if (currentItem != null) {
                if (Platform.isSameItem(this.toolInv.getItemStack(), currentItem)) {
                    this.getPlayerInv()
                            .setInventorySlotContents(this.getPlayerInv().currentItem, this.toolInv.getItemStack());
                } else {
                    this.setValidContainer(false);
                }
            } else {
                this.setValidContainer(false);
            }
        }

        super.detectAndSendChanges();
    }
}
