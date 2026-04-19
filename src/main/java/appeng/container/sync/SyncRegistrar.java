package appeng.container.sync;

import org.jetbrains.annotations.NotNull;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.ConfigEnumSyncHandler;
import appeng.container.sync.handlers.DeltaObjectSyncHandler;
import appeng.container.sync.handlers.DoubleSyncHandler;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.container.sync.handlers.LongSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;

public interface SyncRegistrar {

    /**
     * Creates a child registrar whose handlers are registered under the given key as an additional fullKey scope
     * segment.
     */
    @NotNull
    SyncRegistrar child(@NotNull String key);

    @NotNull
    IntSyncHandler intS2C(@NotNull String key);

    @NotNull
    IntSyncHandler intC2S(@NotNull String key);

    @NotNull
    IntSyncHandler intSync(@NotNull String key);

    @NotNull
    LongSyncHandler longS2C(@NotNull String key);

    @NotNull
    LongSyncHandler longC2S(@NotNull String key);

    @NotNull
    LongSyncHandler longSync(@NotNull String key);

    @NotNull
    BooleanSyncHandler booleanS2C(@NotNull String key);

    @NotNull
    BooleanSyncHandler booleanC2S(@NotNull String key);

    @NotNull
    BooleanSyncHandler booleanSync(@NotNull String key);

    @NotNull
    DoubleSyncHandler doubleS2C(@NotNull String key);

    @NotNull
    DoubleSyncHandler doubleC2S(@NotNull String key);

    @NotNull
    DoubleSyncHandler doubleSync(@NotNull String key);

    @NotNull
    <T, D> DeltaObjectSyncHandler<T, D> objectS2C(@NotNull String key, @NotNull DeltaSyncCodec<T, D> codec,
            T initialValue);

    @NotNull
    <T> ObjectSyncHandler<T> objectS2C(@NotNull String key, @NotNull SyncCodec<T> codec, T initialValue);

    @NotNull
    <T, D> DeltaObjectSyncHandler<T, D> objectC2S(@NotNull String key, @NotNull DeltaSyncCodec<T, D> codec,
            T initialValue);

    @NotNull
    <T> ObjectSyncHandler<T> objectC2S(@NotNull String key, @NotNull SyncCodec<T> codec, T initialValue);

    @NotNull
    <T, D> DeltaObjectSyncHandler<T, D> object(@NotNull String key, @NotNull DeltaSyncCodec<T, D> codec,
            T initialValue);

    @NotNull
    <T> ObjectSyncHandler<T> object(@NotNull String key, @NotNull SyncCodec<T> codec, T initialValue);

    @NotNull
    <E extends Enum<E>> ConfigEnumSyncHandler<E> configEnum(@NotNull String key, @NotNull Settings setting,
            @NotNull Class<E> enumClass, @NotNull IConfigManager configManager, E initialValue);
}
