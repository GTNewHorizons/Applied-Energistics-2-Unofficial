package appeng.container.sync;

/**
 * Encodes and decodes values synchronized by {@link SyncRegistrar} object handlers.
 * <p>
 * Implementations define both the packet format and the value semantics used by the synchronization system to detect
 * changes. Use {@link SyncCodecs} for common codecs and factory methods.
 */
public interface SyncCodec<T> extends StreamCodec<T> {

    /**
     * Returns an isolated copy that can be retained as the last synchronized value.
     */
    T copy(T value);

    /**
     * Returns whether two values should be treated as equal for change detection.
     */
    boolean valuesEqual(T a, T b);

}
