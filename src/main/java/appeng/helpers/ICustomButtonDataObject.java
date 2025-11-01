package appeng.helpers;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;

import io.netty.buffer.ByteBuf;

public interface ICustomButtonDataObject {

    void initCustomButtons(int guiLeft, int guiTop, int xSize, int ySize, int xOffset, int yOffset,
            List<GuiButton> buttonList);

    boolean actionPerformedCustomButtons(final GuiButton btn);

    void readData(NBTTagCompound tag);

    void writeData(NBTTagCompound tag);

    void writeByte(ByteBuf buf);

    void readByte(ByteBuf buf);
}
