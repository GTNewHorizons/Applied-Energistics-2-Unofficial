package appeng.container.interfaces;

import net.minecraft.item.ItemStack;

import appeng.client.gui.IGuiSub;
import appeng.container.PrimaryGui;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IContainerSubGui {

    void setPrimaryGui(PrimaryGui pg);

    @SideOnly(Side.CLIENT)
    ItemStack getPrimaryGuiIcon();

    @SideOnly(Side.CLIENT)
    void setGuiLink(IGuiSub gs);
}
