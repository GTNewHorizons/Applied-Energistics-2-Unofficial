package appeng.container.sync;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

import appeng.api.storage.data.IAEStack;
import appeng.container.guisync.IGuiPacketWritable;
import io.netty.buffer.ByteBuf;

public final class SyncCodecs {

    private static final SyncCodec<IAEStack<?>> AE_STACK_CODEC = of(
            IAEStack.class.getName(),
            IAEStack::writeToPacketGeneric,
            IAEStack::fromPacketGeneric,
            value -> value == null ? null : value.copy(),
            Objects::equals);

    private SyncCodecs() {}

    public static <T> SyncCodec<T> of(final String typeKey, final SyncCodec.Writer<T> writer,
            final SyncCodec.Reader<T> reader, final UnaryOperator<T> copier, final BiPredicate<T, T> equality) {
        Objects.requireNonNull(typeKey, "typeKey");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(copier, "copier");
        Objects.requireNonNull(equality, "equality");

        return new SyncCodec<>() {

            @Override
            public void write(final ByteBuf buf, final T value) throws java.io.IOException {
                writer.write(buf, value);
            }

            @Override
            public T read(final ByteBuf buf) throws java.io.IOException {
                return reader.read(buf);
            }

            @Override
            public T copy(final T value) {
                return copier.apply(value);
            }

            @Override
            public boolean valuesEqual(final T a, final T b) {
                return equality.test(a, b);
            }

            @Override
            public String getTypeKey() {
                return typeKey;
            }
        };
    }

    public static <T extends IGuiPacketWritable> SyncCodec<T> packetWritable(final Class<T> type,
            final SyncCodec.Reader<T> reader, final UnaryOperator<T> copier, final BiPredicate<T, T> equality) {
        Objects.requireNonNull(type, "type");
        return of(type.getName(), (buf, value) -> value.writeToPacket(buf), reader, copier, equality);
    }

    public static <E extends Enum<E>> SyncCodec<E> enumValue(final Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass");

        final E[] values = enumClass.getEnumConstants();
        return of(enumClass.getName(), (buf, value) -> buf.writeInt(value.ordinal()), buf -> {
            final int ordinal = buf.readInt();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new java.io.IOException("Invalid enum ordinal " + ordinal + " for " + enumClass.getName());
            }

            return values[ordinal];
        }, UnaryOperator.identity(), Objects::equals);
    }

    public static SyncCodec<IAEStack<?>> aeStack() {
        return AE_STACK_CODEC;
    }
}
