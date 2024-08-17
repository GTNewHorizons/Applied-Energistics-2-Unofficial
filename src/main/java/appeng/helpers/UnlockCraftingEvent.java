package appeng.helpers;

import appeng.api.config.LockCraftingMode;

/**
 * The types of event that the interface is waiting for to unlock crafting again.
 */
public enum UnlockCraftingEvent {

    PULSE(LockCraftingMode.LOCK_UNTIL_PULSE),
    RESULT(LockCraftingMode.LOCK_UNTIL_RESULT);

    private final LockCraftingMode correspondingMode;

    UnlockCraftingEvent(LockCraftingMode mode) {
        this.correspondingMode = mode;
    }

    public boolean matches(LockCraftingMode mode) {
        return this.correspondingMode == mode;
    }
}
