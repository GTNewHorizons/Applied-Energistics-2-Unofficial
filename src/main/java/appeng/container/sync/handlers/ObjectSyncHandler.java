package appeng.container.sync.handlers;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.container.sync.AbstractSyncHandler;
import appeng.container.sync.DeltaSyncCodec;
import appeng.container.sync.SyncCodec;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ObjectSyncHandler<T> extends AbstractSyncHandler {

    @FunctionalInterface
    public interface ObjectChangeListener<T> {

        void onChange(T oldValue, T newValue);
    }

    private final SyncCodec<T> codec;
    private T value;
    private T lastSentSnapshot;
    private boolean hasLastSentSnapshot;
    private @Nullable ObjectChangeListener<T> clientChangeListener;
    private @Nullable ObjectChangeListener<T> serverChangeListener;

    public ObjectSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction, final SyncCodec<T> codec, final T initialValue) {
        super(manager, key, fullKey, direction);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.value = initialValue;
        this.lastSentSnapshot = this.copyValue(initialValue);
        this.hasLastSentSnapshot = true;
        this.dirty = false;
    }

    public T get() {
        return this.value;
    }

    public void set(final T value) {
        this.value = value;
        this.clearQueuedDeltas();
        this.markDirty();
    }

    public void markDirty() {
        this.markDirtyInternal();
    }

    /**
     * Applies an in-place change to the current value and marks the handler dirty.
     */
    public void mutate(final Consumer<T> mutator) {
        if (this.value == null) {
            throw new IllegalStateException("Cannot mutate a null sync value: " + this.fullKey);
        }

        mutator.accept(this.value);
        this.clearQueuedDeltas();
        this.markDirty();
    }

    public @NotNull ObjectSyncHandler<T> onClientChange(final ObjectChangeListener<T> listener) {
        this.clientChangeListener = listener;
        return this;
    }

    public @NotNull ObjectSyncHandler<T> onServerChange(final ObjectChangeListener<T> listener) {
        this.serverChangeListener = listener;
        return this;
    }

    @Override
    protected SyncMode writeUpdate(final SyncEndpoint localEndpoint, final boolean initialSync, final ByteBuf buf)
            throws IOException {
        final boolean forceFullResync = this.shouldForceFullResync();
        if (!this.canSendFrom(localEndpoint)) {
            return null;
        }
        if (initialSync) {
            if (!this.sendsInitialStateFrom(localEndpoint)) {
                return null;
            }
        } else if (!this.dirty && !forceFullResync) {
            return null;
        }

        if (!initialSync && !forceFullResync
                && this.hasLastSentSnapshot
                && this.valuesEqual(this.value, this.lastSentSnapshot)) {
            this.clearQueuedDeltas();
            this.dirty = false;
            return null;
        }

        SyncMode mode = SyncMode.FULL;
        ByteBuf payload = buf;

        if (!initialSync && !forceFullResync
                && this.codec instanceof DeltaSyncCodec<?, ?>
                && this.hasLastSentSnapshot) {
            final ByteBuf fullPayload = Unpooled.buffer();
            this.writeFull(fullPayload, this.value);

            final ByteBuf deltaPayload = this.writeDeltaPayload();
            if (deltaPayload != null && deltaPayload.readableBytes() < fullPayload.readableBytes()) {
                mode = SyncMode.DELTA;
                payload = deltaPayload;
            } else {
                payload = fullPayload;
            }
        } else {
            this.writeFull(payload, this.value);
        }

        if (payload != buf) {
            buf.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        }

        this.lastSentSnapshot = this.copyValue(this.value);
        this.hasLastSentSnapshot = true;
        this.clearQueuedDeltas();
        this.dirty = false;
        this.clearFullResyncRequest();
        return mode;
    }

    @Override
    protected void readUpdate(final SyncEndpoint remoteEndpoint, final SyncMode mode, final ByteBuf buf)
            throws IOException {
        final T oldValue = this.value;
        final T newValue;

        this.clearQueuedDeltas();

        if (mode == SyncMode.FULL) {
            newValue = this.readFull(buf);
        } else if (this.codec instanceof DeltaSyncCodec<?, ?>) {
            try {
                newValue = this.applyDeltaPayload(buf);
            } catch (final IOException e) {
                // CLIENT_TO_SERVER cannot push a full authoritative state from the server,
                // so ask the client to resend its current full value instead.
                if (this.getDirection() == SyncDirection.CLIENT_TO_SERVER || remoteEndpoint == SyncEndpoint.SERVER) {
                    this.requestRemoteFullResync();
                } else {
                    // SERVER_TO_CLIENT and BIDIRECTIONAL can recover by sending the server state.
                    this.requestFullResync();
                }
                throw e;
            }
        } else {
            throw new IOException("ObjectSyncHandler for " + this.fullKey + " does not support delta updates.");
        }

        this.value = newValue;
        this.lastSentSnapshot = this.copyValue(newValue);
        this.hasLastSentSnapshot = true;
        this.dirty = false;

        if (remoteEndpoint == SyncEndpoint.SERVER) {
            if (this.clientChangeListener != null) {
                this.clientChangeListener.onChange(oldValue, newValue);
            }
        } else if (this.serverChangeListener != null) {
            this.serverChangeListener.onChange(oldValue, newValue);
        }
    }

    @Override
    protected String getTypeKey() {
        return this.codec.getTypeKey();
    }

    protected final @Nullable T getCurrentValue() {
        return this.value;
    }

    protected final void setCurrentValue(@Nullable final T value) {
        this.value = value;
    }

    private void writeFull(final ByteBuf buf, @Nullable final T value) throws IOException {
        buf.writeBoolean(value != null);
        if (value != null) {
            this.codec.write(buf, value);
        }
    }

    @Nullable
    private T readFull(final ByteBuf buf) throws IOException {
        if (!buf.readBoolean()) {
            return null;
        }
        return this.codec.read(buf);
    }

    protected final @Nullable T copyValue(@Nullable final T value) {
        return value == null ? null : this.codec.copy(value);
    }

    private boolean valuesEqual(@Nullable final T a, @Nullable final T b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return this.codec.valuesEqual(a, b);
    }

    @Nullable
    protected ByteBuf writeDeltaPayload() throws IOException {
        final ByteBuf queuedDeltaPayload = this.getQueuedDeltaPayload();
        if (queuedDeltaPayload != null) {
            return queuedDeltaPayload;
        }

        final DeltaSyncCodec<T, Object> deltaCodec = (DeltaSyncCodec<T, Object>) this.codec;
        final T previous = this.lastSentSnapshot;
        final T current = this.value;
        if (previous == null || current == null) {
            return null;
        }

        final Object delta = deltaCodec.diff(previous, current);
        if (delta == null) {
            return null;
        }

        final ByteBuf deltaPayload = Unpooled.buffer();
        deltaPayload.writeInt(1);
        deltaCodec.writeDelta(deltaPayload, delta);
        return deltaPayload;
    }

    protected T applyDeltaPayload(final ByteBuf buf) throws IOException {
        final DeltaSyncCodec<T, Object> deltaCodec = (DeltaSyncCodec<T, Object>) this.codec;
        T result = this.copyValue(this.value);
        final int deltaCount = buf.readInt();
        if (deltaCount <= 0) {
            throw new IOException("Invalid delta count " + deltaCount + " for " + this.fullKey + ".");
        }

        for (int i = 0; i < deltaCount; i++) {
            result = deltaCodec.applyDelta(result, buf);
        }

        return result;
    }

    protected void clearQueuedDeltas() {}

    protected @Nullable ByteBuf getQueuedDeltaPayload() throws IOException {
        return null;
    }
}
