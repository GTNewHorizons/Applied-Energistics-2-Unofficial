package appeng.items.contents;

import javax.annotation.Nullable;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEStack;

public class PinList {

    IAEStack<?>[] pins;

    public PinList() {
        this.pins = new IAEStack[PinsState.getPinsCount()];
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
