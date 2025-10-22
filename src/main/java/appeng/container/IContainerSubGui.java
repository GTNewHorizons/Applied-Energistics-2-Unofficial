package appeng.container;

import net.minecraft.item.ItemStack;

import appeng.client.gui.IGuiSub;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IContainerSubGui {

    void setPrimaryGui(PrimaryGui pg);

    @SideOnly(Side.CLIENT)
    ItemStack getPrimaryGuiIcon();

    @SideOnly(Side.CLIENT)
    void setGuiLink(IGuiSub gs);
}
