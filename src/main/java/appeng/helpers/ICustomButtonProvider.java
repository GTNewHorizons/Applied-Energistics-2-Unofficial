package appeng.helpers;

import java.util.List;

import net.minecraft.client.gui.GuiButton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public interface ICustomButtonProvider {

    @SideOnly(Side.CLIENT)
    void initCustomButtons(int guiLeft, int guiTop, int xSize, int ySize, int xOffset, int yOffset,
            List<GuiButton> buttonList);

    @SideOnly(Side.CLIENT)
    boolean actionPerformedCustomButtons(final GuiButton btn);

    void writeCustomButtonData();

    void readCustomButtonData();

    ICustomButtonDataObject getDataObject();

    void setDataObject(ICustomButtonDataObject dataObject);
}
