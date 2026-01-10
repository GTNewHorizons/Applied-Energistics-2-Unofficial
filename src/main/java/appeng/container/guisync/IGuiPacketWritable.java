package appeng.container.guisync;

import io.netty.buffer.ByteBuf;

/**
 * Implement on classes to signal they can be synchronized to the client using {@link GuiSync}. <br/>
 * For this to work fully, the class also needs to have a public constructor that takes a {@link ByteBuf} argument and
 * be implemented equals to detect changes.
 */
public interface IGuiPacketWritable {

    void writeToPacket(ByteBuf buf);
}
