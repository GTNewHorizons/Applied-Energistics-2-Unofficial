package appeng.container.sync.handlers;

import java.io.IOException;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.sync.SyncCodec;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;

public final class ConfigEnumSyncHandler<E extends Enum<E>> extends ObjectSyncHandler<E> {

    private final Settings setting;
    private final IConfigManager configManager;
    private final Class<E> enumClass;

    public ConfigEnumSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction, final Settings setting, final Class<E> enumClass,
            final IConfigManager configManager, @Nullable final E initialValue) {
        super(manager, key, fullKey, direction, new ConfigEnumCodec<>(enumClass), initialValue);
        this.setting = Objects.requireNonNull(setting, "setting");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.enumClass = Objects.requireNonNull(enumClass, "enumClass");
    }

    public void syncFromConfig() {
        this.set(this.readCurrentValue());
    }

    public void rotate(final boolean backwards) {
        final E next = this.rotateValue(this.getCurrentOrConfigValue(), backwards);
        this.set(next);
    }

    @Override
    protected void readUpdate(final SyncEndpoint remoteEndpoint, final SyncMode mode, final ByteBuf buf)
            throws IOException {
        super.readUpdate(remoteEndpoint, mode, buf);

        if (remoteEndpoint == SyncEndpoint.CLIENT) {
            final E value = this.get();
            if (value != null) {
                this.configManager.putSetting(this.setting, value);
            }
        }
    }

    private E readCurrentValue() {
        return this.enumClass.cast(this.configManager.getSetting(this.setting));
    }

    private @Nullable E getCurrentOrConfigValue() {
        final E current = this.get();
        return current != null ? current : this.readCurrentValue();
    }

    private E rotateValue(final @Nullable E currentValue, final boolean backwards) {
        if (currentValue == null) {
            throw new IllegalStateException("Cannot rotate a null config enum value.");
        }

        return Platform.rotateEnum(currentValue, backwards, this.setting.getPossibleValues());
    }

    private static final class ConfigEnumCodec<E extends Enum<E>> implements SyncCodec<E> {

        private final E[] enumValues;

        private ConfigEnumCodec(final Class<E> enumClass) {
            this.enumValues = Objects.requireNonNull(enumClass, "enumClass").getEnumConstants();
        }

        @Override
        public void write(final ByteBuf buf, final E value) {
            buf.writeInt(value.ordinal());
        }

        @Override
        public E read(final ByteBuf buf) throws IOException {
            final int ordinal = buf.readInt();
            if (ordinal < 0 || ordinal >= this.enumValues.length) {
                throw new IOException("Invalid enum ordinal " + ordinal + " for config enum sync.");
            }

            return this.enumValues[ordinal];
        }

        @Override
        public E copy(final E value) {
            return value;
        }

        @Override
        public boolean valuesEqual(final E a, final E b) {
            return a == b;
        }

        @Override
        public String getTypeKey() {
            return this.enumValues.getClass().getComponentType().getName();
        }
    }
}
