package appeng.api.config;

public enum AdvancedBlockingMode {
    /**
     * Use the old blocking mode logic (ignore circuits, etc.)
     */
    DEFAULT,

    /**
     * Block on all items, including circuits.
     */
    BLOCK_ON_ALL
}
