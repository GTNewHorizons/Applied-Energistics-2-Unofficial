package appeng.container.sync;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

/**
 * Encodes and decodes one value in a packet stream.
 * <p>
 * Stream codecs define the packet format only. State synchronization handlers use {@link SyncCodec} when they also need
 * copy and equality semantics for change detection.
 */
public interface StreamCodec<T> {

    @FunctionalInterface
    interface Reader<T> {

        T read(ByteBuf buf) throws IOException;
    }

    @FunctionalInterface
    interface Writer<T> {

        void write(ByteBuf buf, T value) throws IOException;
    }

    void write(ByteBuf buf, T value) throws IOException;

    T read(ByteBuf buf) throws IOException;

    /**
     * Returns the stable type identifier used when validating that both sides registered compatible handlers.
     */
    default String getTypeKey() {
        return this.getClass().getName();
    }
}
