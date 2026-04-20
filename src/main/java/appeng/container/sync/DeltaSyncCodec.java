package appeng.container.sync;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import io.netty.buffer.ByteBuf;

/**
 * Extends {@link SyncCodec} with an optional delta format for mutable values.
 * <p>
 * Delta updates are an optimization only. Callers must still be able to fall back to a full sync whenever a delta
 * cannot be produced or cannot be applied.
 */
public interface DeltaSyncCodec<T, D> extends SyncCodec<T> {

    /**
     * Returns a delta from {@code previous} to {@code current}, or {@code null} when this change should be sent as a
     * full update instead.
     */
    @Nullable
    D diff(T previous, T current);

    void writeDelta(ByteBuf buf, D delta) throws IOException;

    /**
     * Applies one delta payload to {@code base} and returns the updated value.
     */
    T applyDelta(@Nullable T base, ByteBuf buf) throws IOException;

    default T applyDeltaLocally(@Nullable final T base, final D delta) throws IOException {
        final ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        this.writeDelta(buf, delta);
        return this.applyDelta(base, buf);
    }
}
