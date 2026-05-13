package appeng.container.sync;

import java.io.IOException;
import java.util.Objects;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class StreamCodecs {

    private static final StreamCodec<Void> EMPTY_CODEC = of(Void.class.getName(), (buf, value) -> {}, buf -> null);

    private static final StreamCodec<Boolean> BOOLEAN_CODEC = of(
            Boolean.TYPE.getName(),
            ByteBuf::writeBoolean,
            ByteBuf::readBoolean);

    private static final StreamCodec<Byte> BYTE_CODEC = of(
            Byte.TYPE.getName(),
            (buf, value) -> buf.writeByte(value),
            ByteBuf::readByte);

    private static final StreamCodec<Short> SHORT_CODEC = of(
            Short.TYPE.getName(),
            (buf, value) -> buf.writeShort(value),
            ByteBuf::readShort);

    private static final StreamCodec<Character> CHAR_CODEC = of(
            Character.TYPE.getName(),
            (buf, value) -> buf.writeChar(value),
            ByteBuf::readChar);

    private static final StreamCodec<Integer> INT_CODEC = of(
            Integer.TYPE.getName(),
            ByteBuf::writeInt,
            ByteBuf::readInt);

    private static final StreamCodec<Long> LONG_CODEC = of(Long.TYPE.getName(), ByteBuf::writeLong, ByteBuf::readLong);

    private static final StreamCodec<Float> FLOAT_CODEC = of(
            Float.TYPE.getName(),
            ByteBuf::writeFloat,
            ByteBuf::readFloat);

    private static final StreamCodec<Double> DOUBLE_CODEC = of(
            Double.TYPE.getName(),
            ByteBuf::writeDouble,
            ByteBuf::readDouble);

    private static final StreamCodec<String> STRING_CODEC = of(
            String.class.getName(),
            ByteBufUtils::writeUTF8String,
            ByteBufUtils::readUTF8String);

    private StreamCodecs() {}

    public static <T> StreamCodec<T> of(final String typeKey, final StreamCodec.Writer<T> writer,
            final StreamCodec.Reader<T> reader) {
        Objects.requireNonNull(typeKey, "typeKey");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(reader, "reader");

        return new StreamCodec<>() {

            @Override
            public void write(final ByteBuf buf, final T value) throws IOException {
                writer.write(buf, value);
            }

            @Override
            public T read(final ByteBuf buf) throws IOException {
                return reader.read(buf);
            }

            @Override
            public String getTypeKey() {
                return typeKey;
            }
        };
    }

    public static StreamCodec<Void> empty() {
        return EMPTY_CODEC;
    }

    public static StreamCodec<Boolean> booleanValue() {
        return BOOLEAN_CODEC;
    }

    public static StreamCodec<Byte> byteValue() {
        return BYTE_CODEC;
    }

    public static StreamCodec<Short> shortValue() {
        return SHORT_CODEC;
    }

    public static StreamCodec<Character> charValue() {
        return CHAR_CODEC;
    }

    public static StreamCodec<Integer> intValue() {
        return INT_CODEC;
    }

    public static StreamCodec<Long> longValue() {
        return LONG_CODEC;
    }

    public static StreamCodec<Float> floatValue() {
        return FLOAT_CODEC;
    }

    public static StreamCodec<Double> doubleValue() {
        return DOUBLE_CODEC;
    }

    public static StreamCodec<String> string() {
        return STRING_CODEC;
    }

    public static <E extends Enum<E>> StreamCodec<E> enumValue(final Class<E> enumClass) {
        Objects.requireNonNull(enumClass, "enumClass");

        final E[] values = enumClass.getEnumConstants();
        return of(enumClass.getName(), (buf, value) -> buf.writeInt(value.ordinal()), buf -> {
            final int ordinal = buf.readInt();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IOException("Invalid enum ordinal " + ordinal + " for " + enumClass.getName());
            }

            return values[ordinal];
        });
    }
}
