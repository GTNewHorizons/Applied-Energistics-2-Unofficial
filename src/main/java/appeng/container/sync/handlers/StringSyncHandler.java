package appeng.container.sync.handlers;

import java.io.IOException;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.container.sync.AbstractSyncHandler;
import appeng.container.sync.SyncDirection;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncManager;
import appeng.container.sync.SyncMode;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class StringSyncHandler extends AbstractSyncHandler {

    @FunctionalInterface
    public interface StringChangeListener {

        void onChange(String oldValue, String newValue);
    }

    private String value;
    private String lastSentValue;
    private boolean hasLastSentValue;
    private @Nullable StringChangeListener clientChangeListener;
    private @Nullable StringChangeListener serverChangeListener;

    public StringSyncHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction) {
        super(manager, key, fullKey, direction);
    }

    public String get() {
        return this.value;
    }

    /**
     * Seeds the local value without marking it dirty or scheduling it to be sent.
     */
    public void setLocalValue(final String value) {
        this.value = value;
        this.lastSentValue = value;
        this.hasLastSentValue = true;
        this.dirty = false;
        this.clearFullResyncRequest();
    }

    public void set(final String value) {
        this.value = value;
        this.markDirty();
    }

    public void markDirty() {
        this.markDirtyInternal();
    }

    @NotNull
    public StringSyncHandler onClientChange(final StringChangeListener listener) {
        this.clientChangeListener = listener;
        return this;
    }

    @NotNull
    public StringSyncHandler onServerChange(final StringChangeListener listener) {
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
                && Objects.equals(this.lastSentValue, this.value)) {
            this.dirty = false;
            return null;
        }

        buf.writeBoolean(this.value != null);
        if (this.value != null) ByteBufUtils.writeUTF8String(buf, this.value);
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

        final String oldValue = this.value;
        final String newValue;
        if (buf.readBoolean()) newValue = ByteBufUtils.readUTF8String(buf);
        else newValue = null;

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
        return "string";
    }
}
