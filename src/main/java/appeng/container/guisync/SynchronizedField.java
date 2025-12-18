package appeng.container.guisync;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.base.Preconditions;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public abstract class SynchronizedField<T> {

    private final Object source;
    private final String name;
    protected final MethodHandle getter;
    protected final MethodHandle setter;
    protected T clientVersion;

    private SynchronizedField(Object source, Field field, String name) {
        this.clientVersion = null;
        this.source = source;
        this.name = name;
        field.setAccessible(true);
        try {
            this.getter = MethodHandles.publicLookup().unreflectGetter(field);
            this.setter = MethodHandles.publicLookup().unreflectSetter(field);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to get accessor for field " + field + ". Did you forget to make it public?");
        }
    }

    @SuppressWarnings("unchecked")
    public T getCurrentValue() {
        try {
            return (T) this.getter.invoke(source);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public String getName() {
        return this.name;
    }

    public boolean hasChanges() {
        return !Objects.equals(getCurrentValue(), this.clientVersion);
    }

    public final void write(ByteBuf buf) {
        T currentValue = getCurrentValue();
        this.clientVersion = currentValue;
        this.writeValue(buf, currentValue);
    }

    public final void read(ByteBuf buf) {
        T value = readValue(buf);
        try {
            setter.invoke(source, value);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    protected abstract void writeValue(ByteBuf buf, T value);

    protected abstract T readValue(ByteBuf buf);

    public static SynchronizedField<?> create(Object source, Field field, String name) {
        Class<?> fieldType = field.getType();

        if (fieldType == int.class || fieldType == Integer.class) {
            return new IntegerField(source, field, name);
        } else if (fieldType == long.class || fieldType == Long.class) {
            return new LongField(source, field, name);
        } else if (fieldType == double.class) {
            return new DoubleField(source, field, name);
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return new BooleanField(source, field, name);
        } else if (fieldType == String.class) {
            return new StringField(source, field, name);
        } else if (fieldType.isEnum()) {
            return createEnumField(source, field, name, fieldType.asSubclass(Enum.class));
        } else if (IGuiPacketWritable.class.isAssignableFrom(fieldType)) {
            return new CustomField(source, field, name);
        } else {
            throw new IllegalArgumentException("Cannot synchronize field " + field);
        }
    }

    private static <T extends Enum<T>> EnumField<T> createEnumField(Object source, Field field, String name,
            Class<T> fieldType) {
        return new EnumField<>(source, field, name, fieldType.getEnumConstants());
    }

    private static class StringField extends SynchronizedField<String> {

        private StringField(Object source, Field field, String name) {
            super(source, field, name);
        }

        @Override
        protected void writeValue(ByteBuf buf, String value) {
            ByteBufUtils.writeUTF8String(buf, value);
        }

        @Override
        protected String readValue(ByteBuf buf) {
            return ByteBufUtils.readUTF8String(buf);
        }
    }

    private static class IntegerField extends SynchronizedField<Integer> {

        private IntegerField(Object source, Field field, String name) {
            super(source, field, name);
        }

        @Override
        protected void writeValue(ByteBuf buf, Integer value) {
            buf.writeInt(value);
        }

        @Override
        protected Integer readValue(ByteBuf buf) {
            return buf.readInt();
        }
    }

    private static class LongField extends SynchronizedField<Long> {

        private LongField(Object source, Field field, String name) {
            super(source, field, name);
        }

        @Override
        protected void writeValue(ByteBuf buf, Long value) {
            buf.writeLong(value);
        }

        @Override
        protected Long readValue(ByteBuf buf) {
            return buf.readLong();
        }
    }

    private static class DoubleField extends SynchronizedField<Double> {

        private DoubleField(Object source, Field field, String name) {
            super(source, field, name);
        }

        @Override
        protected void writeValue(ByteBuf buf, Double value) {
            buf.writeDouble(value);
        }

        @Override
        protected Double readValue(ByteBuf buf) {
            return buf.readDouble();
        }
    }

    private static class BooleanField extends SynchronizedField<Boolean> {

        private BooleanField(Object source, Field field, String name) {
            super(source, field, name);
        }

        @Override
        protected void writeValue(ByteBuf buf, Boolean value) {
            buf.writeBoolean(value);
        }

        @Override
        protected Boolean readValue(ByteBuf buf) {
            return buf.readBoolean();
        }
    }

    private static class EnumField<T extends Enum<T>> extends SynchronizedField<T> {

        private final T[] values;

        private EnumField(Object source, Field field, String name, T[] values) {
            super(source, field, name);
            this.values = values;
        }

        @Override
        protected void writeValue(ByteBuf buf, T value) {
            if (value == null) {
                buf.writeShort(-1);
            } else {
                buf.writeShort((short) value.ordinal());
            }
        }

        @Override
        protected T readValue(ByteBuf buf) {
            int ordinal = buf.readShort();
            if (ordinal == -1) {
                return null;
            } else {
                return values[ordinal];
            }
        }
    }

    private static class CustomField extends SynchronizedField<Object> {

        private static final Map<Class<?>, Function<ByteBuf, Object>> factories = new HashMap<>();
        private final Class<?> fieldType;

        private CustomField(Object source, Field field, String name) {
            super(source, field, name);
            this.fieldType = field.getType();
            Preconditions.checkArgument(IGuiPacketWritable.class.isAssignableFrom(field.getType()));
        }

        @Override
        protected void writeValue(ByteBuf buf, Object value) {
            ((IGuiPacketWritable) value).writeToPacket(buf);
        }

        @Override
        protected Object readValue(ByteBuf data) {
            var factory = factories.computeIfAbsent(fieldType, CustomField::getFactory);
            return factory.apply(data);
        }

        private static Function<ByteBuf, Object> getFactory(Class<?> clazz) {
            try {
                var constructor = clazz.getConstructor(ByteBuf.class);
                return buffer -> {
                    try {
                        return constructor.newInstance(buffer);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Failed to deserialize " + clazz, e);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No constructor taking ByteBuf on " + clazz);
            }
        }
    }
}
