package appeng.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.core.sync.GuiBridge;
import appeng.util.Platform;

public class PrimaryGui {

    final protected Object gui;
    final protected ItemStack guiIcon;
    final protected TileEntity te;
    final protected ForgeDirection side;
    protected int slotIndex = Integer.MIN_VALUE;

    public PrimaryGui(Object gui, ItemStack guiIcon, TileEntity te, ForgeDirection side) {
        this.gui = gui;
        this.guiIcon = guiIcon;
        this.te = te;
        this.side = side;
    }

    public void open(EntityPlayer p) {
        if (gui instanceof GuiBridge gb) {
            Platform.openGUI(p, te, side, gb, slotIndex);
        }
    }

    public ItemStack getIcon() {
        return guiIcon;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }
}
