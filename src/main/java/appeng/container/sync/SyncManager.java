package appeng.container.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.AEBaseContainer;
import appeng.container.sync.handlers.AEStackInventorySyncHandler;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.ConfigEnumSyncHandler;
import appeng.container.sync.handlers.DeltaObjectSyncHandler;
import appeng.container.sync.handlers.DoubleSyncHandler;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.container.sync.handlers.LongSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketContainerSync;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public final class SyncManager {

    private final AEBaseContainer owner;
    private final List<AbstractSyncHandler> handlers = new ArrayList<>();
    private final Object2ObjectMap<String, AbstractSyncHandler> handlersByFullKey = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectMap<AbstractSyncHandler> handlersById = new Int2ObjectOpenHashMap<>();
    private final SyncRegistrar rootRegistrar = new ScopedSyncRegistrar("");
    private boolean initialServerSyncSent;
    private boolean frozen;
    private int layoutHash;

    public SyncManager(final AEBaseContainer owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public SyncRegistrar root() {
        return this.rootRegistrar;
    }

    public void freezeLayout() {
        if (this.frozen) {
            return;
        }

        final List<AbstractSyncHandler> sortedHandlers = new ArrayList<>(this.handlers);
        sortedHandlers.sort(Comparator.comparing(AbstractSyncHandler::getFullKey));

        this.handlersById.clear();

        int nextId = 0;
        int hash = 1;
        for (final AbstractSyncHandler handler : sortedHandlers) {
            handler.assignId(nextId);
            this.handlersById.put(nextId, handler);
            nextId++;

            hash = 31 * hash + handler.getFullKey().hashCode();
            hash = 31 * hash + handler.getDirection().ordinal();
            hash = 31 * hash + handler.getTypeKey().hashCode();
        }

        this.layoutHash = hash != 0 ? hash : 1;
        this.frozen = true;
    }

    public void tickServer() {
        if (!Platform.isServer()) {
            return;
        }

        this.freezeLayout();

        if (!this.initialServerSyncSent) {
            this.sendUpdates(SyncEndpoint.SERVER, true);
            this.initialServerSyncSent = true;
        }

        this.sendUpdates(SyncEndpoint.SERVER, false);
    }

    public void tickClient() {
        if (!Platform.isClient()) {
            return;
        }

        this.freezeLayout();
        this.sendUpdates(SyncEndpoint.CLIENT, false);
    }

    public void readIncoming(final SyncEndpoint remoteEndpoint, final ByteBuf buf) {
        this.freezeLayout();

        try {
            final int remoteLayoutHash = buf.readInt();
            final boolean initialSync = buf.readBoolean();

            if (remoteLayoutHash != this.layoutHash) {
                AELog.warn(
                        "Rejected container sync for %s due to layout mismatch. local=%d remote=%d side=%s initial=%s",
                        this.owner.getClass().getName(),
                        this.layoutHash,
                        remoteLayoutHash,
                        remoteEndpoint,
                        initialSync);
                return;
            }

            while (true) {
                final int handlerId = buf.readInt();
                if (handlerId == -1) {
                    return;
                }

                final AbstractSyncHandler handler = this.handlersById.get(handlerId);
                if (handler == null) {
                    AELog.warn(
                            "Rejected container sync for %s due to unknown handler id %d.",
                            this.owner.getClass().getName(),
                            handlerId);
                    return;
                }

                final SyncMode mode = SyncMode.fromOrdinal(buf.readByte());
                handler.readIncoming(remoteEndpoint, mode, buf);
            }
        } catch (final IOException e) {
            AELog.warn(e, String.format("Failed to read container sync for %s.", this.owner.getClass().getName()));
        }
    }

    private void sendUpdates(final SyncEndpoint localEndpoint, final boolean initialSync) {
        if (this.handlers.isEmpty()) {
            return;
        }

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.layoutHash);
        data.writeBoolean(initialSync);

        int updates = 0;
        for (final AbstractSyncHandler handler : this.handlers) {
            if (handler.shouldRequestRemoteFullResync()) {
                updates++;
                data.writeInt(handler.getId());
                data.writeByte(SyncMode.REQUEST_FULL.ordinal());
                handler.clearRemoteFullResyncRequest();
            }

            final ByteBuf payload = Unpooled.buffer();

            try {
                final SyncMode mode = handler.writeUpdate(localEndpoint, initialSync, payload);
                if (mode == null) {
                    continue;
                }

                updates++;
                data.writeInt(handler.getId());
                data.writeByte(mode.ordinal());
                data.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
            } catch (final IOException e) {
                AELog.warn(
                        e,
                        String.format(
                                "Failed to serialize sync handler %s on %s.",
                                handler.getFullKey(),
                                localEndpoint));
            }
        }

        if (updates == 0) {
            return;
        }

        data.writeInt(-1);

        final PacketContainerSync packet = new PacketContainerSync(this.owner.windowId, data);
        if (localEndpoint == SyncEndpoint.SERVER) {
            if (this.owner.getInventoryPlayer().player instanceof EntityPlayerMP playerMP) {
                NetworkHandler.instance.sendTo(packet, playerMP);
            }
        } else {
            NetworkHandler.instance.sendToServer(packet);
        }
    }

    private <T extends AbstractSyncHandler> T register(final T handler) {
        if (this.frozen) {
            throw new IllegalStateException("Cannot register sync handler after synchronization has started.");
        }

        final AbstractSyncHandler existing = this.handlersByFullKey.putIfAbsent(handler.getFullKey(), handler);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate sync fullKey: " + handler.getFullKey());
        }

        this.handlers.add(handler);
        return handler;
    }

    private static void validateKey(final String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Sync key must not be empty.");
        }
        if (key.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Sync key must not contain '.': " + key);
        }
    }

    private final class ScopedSyncRegistrar implements SyncRegistrar {

        private final String scope;

        private ScopedSyncRegistrar(final String scope) {
            this.scope = scope;
        }

        @Override
        public @NotNull SyncRegistrar child(final @NotNull String key) {
            validateKey(key);
            return new ScopedSyncRegistrar(this.qualify(key));
        }

        @Override
        public @NotNull IntSyncHandler intS2C(final @NotNull String key) {
            return register(
                    new IntSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.SERVER_TO_CLIENT));
        }

        @Override
        public @NotNull IntSyncHandler intC2S(final @NotNull String key) {
            return register(
                    new IntSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.CLIENT_TO_SERVER));
        }

        @Override
        public @NotNull IntSyncHandler intSync(final @NotNull String key) {
            return register(new IntSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.BIDIRECTIONAL));
        }

        @Override
        public @NotNull LongSyncHandler longS2C(final @NotNull String key) {
            return register(
                    new LongSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.SERVER_TO_CLIENT));
        }

        @Override
        public @NotNull LongSyncHandler longC2S(final @NotNull String key) {
            return register(
                    new LongSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.CLIENT_TO_SERVER));
        }

        @Override
        public @NotNull LongSyncHandler longSync(final @NotNull String key) {
            return register(new LongSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.BIDIRECTIONAL));
        }

        @Override
        public @NotNull BooleanSyncHandler booleanS2C(final @NotNull String key) {
            return register(
                    new BooleanSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.SERVER_TO_CLIENT));
        }

        @Override
        public @NotNull BooleanSyncHandler booleanC2S(final @NotNull String key) {
            return register(
                    new BooleanSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.CLIENT_TO_SERVER));
        }

        @Override
        public @NotNull BooleanSyncHandler booleanSync(final @NotNull String key) {
            return register(
                    new BooleanSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.BIDIRECTIONAL));
        }

        @Override
        public @NotNull DoubleSyncHandler doubleS2C(final @NotNull String key) {
            return register(
                    new DoubleSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.SERVER_TO_CLIENT));
        }

        @Override
        public @NotNull DoubleSyncHandler doubleC2S(final @NotNull String key) {
            return register(
                    new DoubleSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.CLIENT_TO_SERVER));
        }

        @Override
        public @NotNull DoubleSyncHandler doubleSync(final @NotNull String key) {
            return register(
                    new DoubleSyncHandler(SyncManager.this, key, this.qualify(key), SyncDirection.BIDIRECTIONAL));
        }

        @Override
        public <T> @NotNull ObjectSyncHandler<T> objectS2C(final @NotNull String key, final @NotNull SyncCodec<T> codec,
                final @Nullable T initialValue) {
            return register(
                    new ObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.SERVER_TO_CLIENT,
                            codec,
                            initialValue));
        }

        @Override
        public <T, D> @NotNull DeltaObjectSyncHandler<T, D> objectS2C(final @NotNull String key,
                final @NotNull DeltaSyncCodec<T, D> codec, final @Nullable T initialValue) {
            return register(
                    new DeltaObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.SERVER_TO_CLIENT,
                            codec,
                            initialValue));
        }

        @Override
        public <T> @NotNull ObjectSyncHandler<T> objectC2S(final @NotNull String key, final @NotNull SyncCodec<T> codec,
                final @Nullable T initialValue) {
            return register(
                    new ObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.CLIENT_TO_SERVER,
                            codec,
                            initialValue));
        }

        @Override
        public <T, D> @NotNull DeltaObjectSyncHandler<T, D> objectC2S(final @NotNull String key,
                final @NotNull DeltaSyncCodec<T, D> codec, final @Nullable T initialValue) {
            return register(
                    new DeltaObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.CLIENT_TO_SERVER,
                            codec,
                            initialValue));
        }

        @Override
        public <T> @NotNull ObjectSyncHandler<T> object(final @NotNull String key, final @NotNull SyncCodec<T> codec,
                final @Nullable T initialValue) {
            return register(
                    new ObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.BIDIRECTIONAL,
                            codec,
                            initialValue));
        }

        @Override
        public <T, D> @NotNull DeltaObjectSyncHandler<T, D> object(final @NotNull String key,
                final @NotNull DeltaSyncCodec<T, D> codec, final @Nullable T initialValue) {
            return register(
                    new DeltaObjectSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.BIDIRECTIONAL,
                            codec,
                            initialValue));
        }

        @Override
        public <E extends Enum<E>> @NotNull ConfigEnumSyncHandler<E> configEnum(final @NotNull String key,
                final @NotNull Settings setting, final @NotNull Class<E> enumClass,
                final @NotNull IConfigManager configManager, final @Nullable E initialValue) {
            return register(
                    new ConfigEnumSyncHandler<>(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.BIDIRECTIONAL,
                            setting,
                            enumClass,
                            configManager,
                            initialValue));
        }

        @Override
        public @NotNull AEStackInventorySyncHandler aeStackInventoryS2C(final @NotNull String key,
                final @NotNull IAEStackInventory inventory) {
            return register(
                    new AEStackInventorySyncHandler(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.SERVER_TO_CLIENT,
                            inventory));
        }

        @Override
        public @NotNull AEStackInventorySyncHandler aeStackInventoryC2S(final @NotNull String key,
                final @NotNull IAEStackInventory inventory) {
            return register(
                    new AEStackInventorySyncHandler(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.CLIENT_TO_SERVER,
                            inventory));
        }

        @Override
        public @NotNull AEStackInventorySyncHandler aeStackInventory(final @NotNull String key,
                final @NotNull IAEStackInventory inventory) {
            return register(
                    new AEStackInventorySyncHandler(
                            SyncManager.this,
                            key,
                            this.qualify(key),
                            SyncDirection.BIDIRECTIONAL,
                            inventory));
        }

        private String qualify(final String key) {
            validateKey(key);
            return this.scope.isEmpty() ? key : this.scope + "." + key;
        }
    }
}
