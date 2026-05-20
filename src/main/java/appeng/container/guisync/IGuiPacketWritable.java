package appeng.container.guisync;

import io.netty.buffer.ByteBuf;

/**
 * Implement on classes to signal they can be synchronized to the client using {@link GuiSync}. <br/>
 * For this to work fully, the class also needs to have a public constructor that takes a {@link ByteBuf} argument and
 * be implemented equals to detect changes.
 *
 * @deprecated Use {@link appeng.container.sync.SyncCodec} and {@link appeng.container.sync.SyncCodecs}, and register
 *             them via {@link appeng.container.sync.SyncRegistrar}. See
 *             {@link appeng.container.sync.codecs.AEStackTypeFilterSyncCodec} and
 *             {@link appeng.container.implementations.ContainerLevelEmitter} for examples.
 */
@Deprecated
public interface IGuiPacketWritable {

    void writeToPacket(ByteBuf buf);
}
