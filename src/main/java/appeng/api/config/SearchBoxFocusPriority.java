package appeng.api.config;

/**
 * Specifies the priority of the search box focus.
 */
public enum SearchBoxFocusPriority {
    /**
     * Keypresses will always be sent to the search box if it focused.
     */
    ALWAYS,

    /**
     * Keypresses will always be sent to the search box if it focused not by autofocus
     */
    NO_AUTOSEARCH,

    /**
     * Keypresses will be sent to the search box only if it cannot be handled elsewhere (e.g., used to move items
     * between hotbar and inventory).
     */
    NEVER
}
