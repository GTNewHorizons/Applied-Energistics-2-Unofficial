package appeng.container.guisync;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class DataSynchronization {

    private final Map<Integer, SynchronizedField<?>> fields = new HashMap<>();

    public DataSynchronization(Object host) {

        collectFields(host, host.getClass(), 0, null);
    }

    private void collectFields(final Object host, final Class<?> clazz, final int offset,
            final StringBuilder hostNameBuilder) {
        for (Field field : clazz.getDeclaredFields()) {
            StringBuilder nameBuilder = hostNameBuilder != null ? hostNameBuilder : new StringBuilder();

            if (field.isAnnotationPresent(GuiSync.Recurse.class)) {
                final GuiSync.Recurse annotation = field.getAnnotation(GuiSync.Recurse.class);
                try {
                    nameBuilder.append(clazz.getName());
                    nameBuilder.append('.');
                    collectFields(field.get(host), field.getType(), annotation.value() + offset, nameBuilder);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to get instance for field " + field + ".");
                }
            }
            if (field.isAnnotationPresent(GuiSync.class)) {
                GuiSync annotation = field.getAnnotation(GuiSync.class);
                int key = annotation.value() + offset;
                if (this.fields.containsKey(key)) {
                    throw new IllegalStateException(
                            "Class " + host.getClass() + " declares the same sync id twice: " + key);
                }
                nameBuilder.append(field.getName());
                this.fields.put(key, SynchronizedField.create(host, field, nameBuilder.toString()));
            }
        }

        // Recurse upwards through the class hierarchy
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != AEBaseContainer.class && superclass != Object.class) {
            collectFields(host, superclass, offset, null);
        }
    }

    public boolean hasChanges() {
        for (SynchronizedField<?> value : fields.values()) {
            if (value.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Write the data for all fields to the given buffer, and marks all fields as unchanged.
     */
    public void writeAll(ByteBuf buf) {
        writeFields(buf, true);
    }

    /**
     * Write the data for changed fields to the given buffer, and marks all fields as unchanged.
     */
    public void writeChanges(ByteBuf buf) {
        writeFields(buf, false);
    }

    private void writeFields(ByteBuf buf, boolean includeUnchanged) {
        for (Map.Entry<Integer, SynchronizedField<?>> entry : fields.entrySet()) {
            if (includeUnchanged || entry.getValue().hasChanges()) {
                buf.writeInt(entry.getKey());
                entry.getValue().write(buf);
            }
        }

        // Terminator
        buf.writeInt(-1);
    }

    public void readUpdate(ByteBuf buf, Object2ObjectMap<String, Pair<Object, Object>> updatedFields) {
        for (int key = buf.readInt(); key != -1; key = buf.readInt()) {
            SynchronizedField<?> field = fields.get(key);
            if (field == null) {
                AELog.warn("Server sent update for GUI field %d, which we don't know.", key);
                continue;
            }

            Object prev = field.getCurrentValue();
            field.read(buf);
            updatedFields.put(field.getName(), new ObjectObjectImmutablePair<>(prev, field.getCurrentValue()));
        }
    }

    /**
     * @return True if any synchronized fields exist.
     */
    public boolean hasFields() {
        return !fields.isEmpty();
    }
}
