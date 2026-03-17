package appeng.items.contents;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;

public class PinList {

    /** Crafting pin slots: 0 to CRAFTING_SLOTS-1. Player pin slots: PLAYER_OFFSET to PLAYER_OFFSET+PLAYER_SLOTS-1. */
    public static final int CRAFTING_SLOTS = 16 * 9;
    public static final int PLAYER_OFFSET = CRAFTING_SLOTS;
    public static final int PLAYER_SLOTS = 16 * 9;
    public static final int TOTAL_SLOTS = CRAFTING_SLOTS + PLAYER_SLOTS;

    IAEStack<?>[] pins;

    public PinList() {
        this.pins = new IAEStack[TOTAL_SLOTS];
    }

    public int size() {
        return this.pins.length;
    }

    @Nullable
    public IAEStack<?> getPin(int index) {
        return pins[index];
    }

    public void setPin(int index, @Nullable IAEStack<?> pin) {
        this.pins[index] = pin;
    }
}
