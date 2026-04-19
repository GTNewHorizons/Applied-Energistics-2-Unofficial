package appeng.container.sync.handlers;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import appeng.container.sync.AbstractSyncHandler;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import io.netty.buffer.ByteBuf;

public class BooleanSyncHandler extends AbstractSyncHandler {

    @FunctionalInterface
    public interface BooleanChangeListener {

        void onChange(boolean oldValue, boolean newValue);
    }

    private boolean value;
    private boolean lastSentValue;
    private boolean hasLastSentValue;
    private @Nullable BooleanChangeListener clientChangeListener;
    private @Nullable BooleanChangeListener serverChangeListener;

    public BooleanSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction) {
        super(manager, key, fullKey, direction);
    }

    public boolean get() {
        return this.value;
    }

    /**
     * Seeds the local value without marking it dirty or scheduling it to be sent.
     */
    public void setLocalValue(final boolean value) {
        this.value = value;
        this.lastSentValue = value;
        this.hasLastSentValue = true;
        this.dirty = false;
        this.clearFullResyncRequest();
    }

    public void set(final boolean value) {
        this.value = value;
        this.markDirty();
    }

    public void markDirty() {
        this.markDirtyInternal();
    }

    public BooleanSyncHandler onClientChange(final BooleanChangeListener listener) {
        this.clientChangeListener = listener;
        return this;
    }

    public BooleanSyncHandler onServerChange(final BooleanChangeListener listener) {
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

        buf.writeBoolean(this.value);
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
            throw new IOException("BooleanSyncHandler only supports full updates.");
        }

        final boolean oldValue = this.value;
        final boolean newValue = buf.readBoolean();
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
        return "boolean";
    }
}
