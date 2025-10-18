package appeng.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;

import appeng.core.sync.GuiBridge;
import appeng.util.Platform;

@Desugar
public record PrimaryGui(Object gui, ItemStack guiIcon, TileEntity te, ForgeDirection side) {

    public void open(EntityPlayer p) {
        if (gui instanceof GuiBridge gb) {
            Platform.openGUI(p, te, side, gb);
        }
    }

    public ItemStack getIcon() {
        return guiIcon;
    }
}
