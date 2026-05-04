package appeng.container.sync.handlers;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.container.sync.AbstractSyncHandler;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import io.netty.buffer.ByteBuf;

public class LongSyncHandler extends AbstractSyncHandler {

    @FunctionalInterface
    public interface LongChangeListener {

        void onChange(long oldValue, long newValue);
    }

    private long value;
    private long lastSentValue;
    private boolean hasLastSentValue;
    private @Nullable LongChangeListener clientChangeListener;
    private @Nullable LongChangeListener serverChangeListener;

    public LongSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction) {
        super(manager, key, fullKey, direction);
    }

    public long get() {
        return this.value;
    }

    /**
     * Seeds the local value without marking it dirty or scheduling it to be sent.
     */
    public void setLocalValue(final long value) {
        this.value = value;
        this.lastSentValue = value;
        this.hasLastSentValue = true;
        this.dirty = false;
        this.clearFullResyncRequest();
    }

    public void set(final long value) {
        this.value = value;
        this.markDirty();
    }

    public void markDirty() {
        this.markDirtyInternal();
    }

    @NotNull
    public LongSyncHandler onClientChange(final LongChangeListener listener) {
        this.clientChangeListener = listener;
        return this;
    }

    @NotNull
    public LongSyncHandler onServerChange(final LongChangeListener listener) {
        this.serverChangeListener = listener;
        return this;
    }

    @Override
    protected SyncMode writeUpdate(final SyncEndpoint localEndpoint, final boolean initialSync, final ByteBuf buf) {
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

        if (!initialSync && !forceFullResync && this.hasLastSentValue && this.lastSentValue == this.value) {
            this.dirty = false;
            return null;
        }

        buf.writeLong(this.value);
        this.lastSentValue = this.value;
        this.hasLastSentValue = true;
        this.dirty = false;
        this.clearFullResyncRequest();
        return SyncMode.FULL;
    }

    @Override
    protected void readUpdate(final SyncEndpoint remoteEndpoint, final SyncMode mode, final ByteBuf buf)
            throws IOException {
        if (mode != SyncMode.FULL) {
            throw new IOException("LongSyncHandler only supports full updates.");
        }

        final long oldValue = this.value;
        final long newValue = buf.readLong();
        this.value = newValue;
        this.lastSentValue = newValue;
        this.hasLastSentValue = true;
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
        return "long";
    }
}
