package appeng.container.sync;

import java.io.IOException;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import io.netty.buffer.ByteBuf;

public abstract class AbstractSyncHandler {

    protected final SyncManager manager;
    protected final String key;
    protected final String fullKey;
    protected final SyncDirection direction;
    protected boolean dirty;
    private int id = -1;
    private boolean fullResyncRequested;
    private boolean remoteFullResyncRequested;

    protected AbstractSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.key = Objects.requireNonNull(key, "key");
        this.fullKey = Objects.requireNonNull(fullKey, "fullKey");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public final String getKey() {
        return this.key;
    }

    public final String getFullKey() {
        return this.fullKey;
    }

    public final int getId() {
        if (this.id < 0) {
            throw new IllegalStateException("Sync handler ID has not been assigned yet: " + this.fullKey);
        }
        return this.id;
    }

    public final SyncDirection getDirection() {
        return this.direction;
    }

    public final boolean canSendFrom(final SyncEndpoint endpoint) {
        return this.direction.canSendFrom(endpoint);
    }

    public final boolean sendsInitialStateFrom(final SyncEndpoint endpoint) {
        return this.direction.sendsInitialStateFrom(endpoint);
    }

    public final void readIncoming(final SyncEndpoint remoteEndpoint, final SyncMode mode, final ByteBuf buf)
            throws IOException {
        if (mode == SyncMode.REQUEST_FULL) {
            this.requestFullResync();
            return;
        }

        if (!this.direction.canReceiveFrom(remoteEndpoint)) {
            throw new IOException("Handler " + this.fullKey + " does not accept updates from " + remoteEndpoint + ".");
        }

        this.readUpdate(remoteEndpoint, mode, buf);
    }

    protected final void markDirtyInternal() {
        this.dirty = true;
    }

    protected final void requestFullResync() {
        this.fullResyncRequested = true;
        this.dirty = true;
    }

    protected final void requestRemoteFullResync() {
        this.remoteFullResyncRequested = true;
    }

    protected final boolean shouldForceFullResync() {
        return this.fullResyncRequested;
    }

    protected final void clearFullResyncRequest() {
        this.fullResyncRequested = false;
    }

    final boolean shouldRequestRemoteFullResync() {
        return this.remoteFullResyncRequested;
    }

    final void clearRemoteFullResyncRequest() {
        this.remoteFullResyncRequested = false;
    }

    final void assignId(final int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Handler ID must be non-negative.");
        }

        if (this.id >= 0 && this.id != id) {
            throw new IllegalStateException(
                    "Sync handler " + this.fullKey + " was already assigned a different ID: " + this.id);
        }

        this.id = id;
    }

    @Nullable
    protected abstract SyncMode writeUpdate(SyncEndpoint localEndpoint, boolean initialSync, ByteBuf buf)
            throws IOException;

    protected abstract void readUpdate(SyncEndpoint remoteEndpoint, SyncMode mode, ByteBuf buf) throws IOException;

    protected abstract String getTypeKey();
}
