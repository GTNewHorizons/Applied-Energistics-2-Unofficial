package appeng.container;

import net.minecraft.item.ItemStack;

import appeng.client.gui.implementations.GuiSub;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface IContainerSubGui {

    void setPrimaryGui(PrimaryGui pg);

    @SideOnly(Side.CLIENT)
    ItemStack getPrimaryGuiIcon();

    @SideOnly(Side.CLIENT)
    void setGuiLink(GuiSub gs);
}
