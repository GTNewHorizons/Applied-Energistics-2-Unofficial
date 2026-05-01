package appeng.container.sync;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

/**
 * Encodes and decodes values synchronized by {@link SyncRegistrar} object handlers.
 * <p>
 * Implementations define both the packet format and the value semantics used by the synchronization system to detect
 * changes. Use {@link SyncCodecs} for common codecs and factory methods.
 */
public interface SyncCodec<T> {

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
     * Returns an isolated copy that can be retained as the last synchronized value.
     */
    T copy(T value);

    /**
     * Returns whether two values should be treated as equal for change detection.
     */
    boolean valuesEqual(T a, T b);

    /**
     * Returns the stable type identifier used when validating that both sides registered compatible handlers.
     */
    default String getTypeKey() {
        return this.getClass().getName();
    }
}
