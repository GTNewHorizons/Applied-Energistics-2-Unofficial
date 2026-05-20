package appeng.container.sync.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.container.sync.DeltaSyncCodec;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Object sync handler that can batch and transmit one or more delta updates before falling back to a normal full sync.
 */
public class DeltaObjectSyncHandler<T, D> extends ObjectSyncHandler<T> {

    private final DeltaSyncCodec<T, D> deltaCodec;
    private final List<D> pendingDeltas = new ArrayList<>();

    public DeltaObjectSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction, final DeltaSyncCodec<T, D> codec, final T initialValue) {
        super(manager, key, fullKey, direction, codec, initialValue);
        this.deltaCodec = codec;
    }

    /**
     * Applies a known local delta immediately and queues it for transmission on the next flush. If delta sync later
     * fails, normal resync recovery still falls back to a full update/request as needed.
     */
    public void applyAndQueueDelta(final D delta) {
        if (this.getCurrentValue() == null) {
            throw new IllegalStateException("Cannot apply a delta to a null sync value: " + this.fullKey);
        }

        try {
            this.setCurrentValue(this.deltaCodec.applyDeltaLocally(this.copyValue(this.getCurrentValue()), delta));
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to apply local delta for " + this.fullKey + ".", e);
        }

        this.pendingDeltas.add(delta);
        this.markDirty();
    }

    @Override
    public @NotNull DeltaObjectSyncHandler<T, D> onClientChange(final ObjectChangeListener<T> listener) {
        super.onClientChange(listener);
        return this;
    }

    @Override
    public @NotNull DeltaObjectSyncHandler<T, D> onServerChange(final ObjectChangeListener<T> listener) {
        super.onServerChange(listener);
        return this;
    }

    @Override
    protected void clearQueuedDeltas() {
        this.pendingDeltas.clear();
    }

    @Override
    protected @Nullable ByteBuf getQueuedDeltaPayload() throws IOException {
        if (this.pendingDeltas.isEmpty()) {
            return null;
        }

        final ByteBuf deltaPayload = Unpooled.buffer();
        deltaPayload.writeInt(this.pendingDeltas.size());
        for (final D delta : this.pendingDeltas) {
            this.deltaCodec.writeDelta(deltaPayload, delta);
        }
        return deltaPayload;
    }

    @Override
    protected T applyDeltaPayload(final ByteBuf buf) throws IOException {
        T result = this.copyValue(this.getCurrentValue());
        final int deltaCount = buf.readInt();
        if (deltaCount <= 0) {
            throw new IOException("Invalid delta count " + deltaCount + " for " + this.fullKey + ".");
        }

        for (int i = 0; i < deltaCount; i++) {
            result = this.deltaCodec.applyDelta(result, buf);
        }

        return result;
    }
}
