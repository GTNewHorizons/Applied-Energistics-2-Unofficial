package appeng.container.sync;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

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

    T copy(T value);

    boolean valuesEqual(T a, T b);

    default String getTypeKey() {
        return this.getClass().getName();
    }
}
