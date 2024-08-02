package appeng.api.implementations.tiles;

import appeng.parts.p2p.PartP2PSound;

/**
 * Allows registering custom behaviour for handling P2P sound events.
 */
public interface ISoundP2PHandler {

    /**
     * @param p2p The tunnel asking just before proxying a sound event
     * @return True if the sound should be proxied, false to suppress proxying via this tunnel.
     */
    default boolean allowSoundProxying(PartP2PSound p2p) {
        return true;
    }

    /**
     * Invoked when a Sound P2P tunnel is attached to this block.
     */
    default void onSoundP2PAttach(PartP2PSound p2p) {}

    /**
     * Invoked when a Sound P2P tunnel is detached from this block (e.g. unloaded or removed).
     */
    default void onSoundP2PDetach(PartP2PSound p2p) {}
}
