package appeng.helpers;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public interface ICustomButtonDataObject {

    @SideOnly(Side.CLIENT)
    void initCustomButtons(int guiLeft, int guiTop, int xSize, int ySize, int xOffset, int yOffset,
            List<GuiButton> buttonList);

    @SideOnly(Side.CLIENT)
    boolean actionPerformedCustomButtons(final GuiButton btn);

    void readData(NBTTagCompound tag);

    void writeData(NBTTagCompound tag);

    void writeByte(ByteBuf buf);

    void readByte(ByteBuf buf);
}
