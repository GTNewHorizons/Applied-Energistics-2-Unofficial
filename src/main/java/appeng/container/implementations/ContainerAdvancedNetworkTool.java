package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;

public class ContainerAdvancedNetworkTool extends AEBaseContainer {

    private final INetworkTool toolInv;

    @GuiSync(1)
    public boolean facadeMode;

    public ContainerAdvancedNetworkTool(final InventoryPlayer ip, final INetworkTool te) {
        super(ip, null, null);
        this.toolInv = te;

        this.lockPlayerInventorySlot(ip.currentItem);

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

    public void toggleFacadeMode() {
        final NBTTagCompound data = Platform.openNbtData(this.toolInv.getItemStack());
        data.setBoolean("hideFacades", !data.getBoolean("hideFacades"));
        this.detectAndSendChanges();
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

        if (this.isValidContainer()) {
            final NBTTagCompound data = Platform.openNbtData(currentItem);
            this.setFacadeMode(data.getBoolean("hideFacades"));
        }

        super.detectAndSendChanges();
    }

    public boolean isFacadeMode() {
        return this.facadeMode;
    }

    private void setFacadeMode(final boolean facadeMode) {
        this.facadeMode = facadeMode;
    }
}
