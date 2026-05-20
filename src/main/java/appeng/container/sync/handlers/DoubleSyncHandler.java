package appeng.container.sync.handlers;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import appeng.container.sync.AbstractSyncHandler;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import io.netty.buffer.ByteBuf;

public class DoubleSyncHandler extends AbstractSyncHandler {

    @FunctionalInterface
    public interface DoubleChangeListener {

        void onChange(double oldValue, double newValue);
    }

    private double value;
    private double lastSentValue;
    private boolean hasLastSentValue;
    private @Nullable DoubleChangeListener clientChangeListener;
    private @Nullable DoubleChangeListener serverChangeListener;

    public DoubleSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction) {
        super(manager, key, fullKey, direction);
    }

    public double get() {
        return this.value;
    }

    /**
     * Seeds the local value without marking it dirty or scheduling it to be sent.
     */
    public void setLocalValue(final double value) {
        this.value = value;
        this.lastSentValue = value;
        this.hasLastSentValue = true;
        this.dirty = false;
        this.clearFullResyncRequest();
    }

    public void set(final double value) {
        this.value = value;
        this.markDirty();
    }

    public void markDirty() {
        this.markDirtyInternal();
    }

    public DoubleSyncHandler onClientChange(final DoubleChangeListener listener) {
        this.clientChangeListener = listener;
        return this;
    }

    public DoubleSyncHandler onServerChange(final DoubleChangeListener listener) {
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

        if (!initialSync && !forceFullResync
                && this.hasLastSentValue
                && Double.doubleToLongBits(this.lastSentValue) == Double.doubleToLongBits(this.value)) {
            this.dirty = false;
            return null;
        }

        buf.writeDouble(this.value);
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
            throw new IOException("DoubleSyncHandler only supports full updates.");
        }

        final double oldValue = this.value;
        final double newValue = buf.readDouble();
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
        return "double";
    }
}
