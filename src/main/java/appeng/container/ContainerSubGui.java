package appeng.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.client.gui.IGuiSub;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.slot.SlotInaccessible;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerSubGui extends AEBaseContainer implements IContainerSubGui {

    private final Slot primaryGuiButtonIcon;

    @SideOnly(Side.CLIENT)
    private IGuiSub guiLink;

    public ContainerSubGui(InventoryPlayer ip, Object anchor) {
        super(ip, anchor);
        this.primaryGuiButtonIcon = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 0, -9000);
        this.addSlotToContainer(this.primaryGuiButtonIcon);
    }

    @Override
    public void setPrimaryGui(PrimaryGui primaryGui) {
        super.setPrimaryGui(primaryGui);
        this.primaryGuiButtonIcon.putStack(primaryGui.getIcon());
    }

    @SideOnly(Side.CLIENT)
    public ItemStack getPrimaryGuiIcon() {
        return this.primaryGuiButtonIcon.getStack();
    }

    @Override
    public void onSlotChange(Slot s) {
        if (Platform.isClient() && this.primaryGuiButtonIcon == s && this.primaryGuiButtonIcon.getHasStack()) {
            this.guiLink.initPrimaryGuiButton();
        }
    }

    @SideOnly(Side.CLIENT)
    public void setGuiLink(IGuiSub gs) {
        this.guiLink = gs;
    }
}
