package appeng.items.contents;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.IGridHost;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

@Deprecated
public class AdvancedNetworkToolViewer implements INetworkTool {

    private final AppEngInternalInventory inv;
    private final ItemStack is;
    private final IGridHost gh;
    private final int size;

    public AdvancedNetworkToolViewer(final ItemStack is, final IGridHost gHost) {
        this.is = is;
        this.gh = gHost;
        this.inv = new AppEngInternalInventory(null, 25);
        this.size = 5;
        if (is.hasTagCompound()) // prevent crash when opening network status screen.
        {
            this.inv.readFromNBT(Platform.openNbtData(is), "inv");
        }
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getSizeInventory() {
        return this.inv.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(final int i) {
        return this.inv.getStackInSlot(i);
    }

    @Override
    public ItemStack decrStackSize(final int i, final int j) {
        return this.inv.decrStackSize(i, j);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(final int i) {
        return this.inv.getStackInSlotOnClosing(i);
    }

    @Override
    public void setInventorySlotContents(final int i, final ItemStack itemstack) {
        this.inv.setInventorySlotContents(i, itemstack);
    }

    @Override
    public String getInventoryName() {
        return this.inv.getInventoryName();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return this.inv.hasCustomInventoryName();
    }

    @Override
    public int getInventoryStackLimit() {
        return this.inv.getInventoryStackLimit();
    }

    @Override
    public void markDirty() {
        this.inv.markDirty();
        this.inv.writeToNBT(Platform.openNbtData(this.is), "inv");
    }

    @Override
    public boolean isUseableByPlayer(final EntityPlayer entityplayer) {
        return this.inv.isUseableByPlayer(entityplayer);
    }

    @Override
    public void openInventory() {
        this.inv.openInventory();
    }

    @Override
    public void closeInventory() {
        this.inv.closeInventory();
    }

    @Override
    public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
        return this.inv.isItemValidForSlot(i, itemstack) && itemstack.getItem() instanceof IUpgradeModule
                && ((IUpgradeModule) itemstack.getItem()).getType(itemstack) != null;
    }

    @Override
    public ItemStack getItemStack() {
        return this.is;
    }

    @Override
    public IGridHost getGridHost() {
        return this.gh;
    }
}
