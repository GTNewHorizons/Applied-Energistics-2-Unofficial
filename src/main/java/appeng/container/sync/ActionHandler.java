package appeng.container.sync;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Sends stateless container-scoped actions immediately instead of batching them into tick-based state sync.
 */
public class ActionHandler<T> extends AbstractSyncHandler {

    private final StreamCodec<T> codec;
    private @Nullable Consumer<T> clientActionListener;
    private @Nullable Consumer<T> serverActionListener;

    public ActionHandler(final SyncManager manager, final String key, final String fullKey,
            final SyncDirection direction, final StreamCodec<T> codec) {
        super(manager, key, fullKey, direction);
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public void send(final T payload) {
        final SyncEndpoint localEndpoint = this.getLocalEndpoint();
        if (!this.canSendFrom(localEndpoint)) {
            throw new IllegalStateException(
                    "Handler " + this.fullKey + " cannot send actions from " + localEndpoint + ".");
        }

        final ByteBuf buf = Unpooled.buffer();
        try {
            this.codec.write(buf, payload);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to serialize action handler " + this.fullKey + ".", e);
        }

        this.manager.sendAction(this, localEndpoint, buf);
    }

    public void send() {
        if (!Objects.equals(this.codec, StreamCodecs.empty())) {
            throw new IllegalStateException("Handler " + this.fullKey + " cannot send an action without a payload.");
        }

        this.send(null);
    }

    public @NotNull ActionHandler<T> onClientAction(final @NotNull Consumer<T> listener) {
        if (!this.direction.canReceiveFrom(SyncEndpoint.SERVER)) {
            throw new IllegalStateException("Handler " + this.fullKey + " does not receive actions on the client.");
        }

        this.clientActionListener = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public @NotNull ActionHandler<T> onClientAction(final @NotNull Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        return this.onClientAction(ignored -> listener.run());
    }

    public @NotNull ActionHandler<T> onServerAction(final @NotNull Consumer<T> listener) {
        if (!this.direction.canReceiveFrom(SyncEndpoint.CLIENT)) {
            throw new IllegalStateException("Handler " + this.fullKey + " does not receive actions on the server.");
        }

        this.serverActionListener = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public @NotNull ActionHandler<T> onServerAction(final @NotNull Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        return this.onServerAction(ignored -> listener.run());
    }

    @Override
    protected SyncMode writeUpdate(final SyncEndpoint localEndpoint, final boolean initialSync, final ByteBuf buf) {
        this.dirty = false;
        this.clearFullResyncRequest();
        return null;
    }

    @Override
    protected void readUpdate(final SyncEndpoint remoteEndpoint, final SyncMode mode, final ByteBuf buf)
            throws IOException {
        if (mode != SyncMode.FULL) {
            throw new IOException("ActionHandler for " + this.fullKey + " only accepts full action payloads.");
        }

        final T payload = this.codec.read(buf);
        if (remoteEndpoint == SyncEndpoint.SERVER) {
            if (this.clientActionListener != null) {
                this.clientActionListener.accept(payload);
            }
        } else if (this.serverActionListener != null) {
            this.serverActionListener.accept(payload);
        }
    }

    @Override
    protected String getTypeKey() {
        return "action:" + this.codec.getTypeKey();
    }

    private SyncEndpoint getLocalEndpoint() {
        if (Platform.isServer()) {
            return SyncEndpoint.SERVER;
        }
        if (Platform.isClient()) {
            return SyncEndpoint.CLIENT;
        }
        throw new IllegalStateException("Cannot send action outside the client or server side.");
    }
}
