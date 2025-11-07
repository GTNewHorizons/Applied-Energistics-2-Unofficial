package appeng.helpers;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEStack;

public interface IPinsHandler {

    default int getPinCount() {
        return PinsState.getPinsCount();
    }

    default void setPin(IAEStack<?> is, int idx) {
        throw new UnsupportedOperationException("setPin is not supported by this handler");
    }

    default void setAEPins(IAEStack<?>[] pins) {
        throw new UnsupportedOperationException("setAEPins is not supported by this handler");
    }

    default IAEStack<?> getPin(int idx) {
        throw new UnsupportedOperationException("getPin is not supported by this handler");
    }

    default IAEStack<?> getAEPin(int idx) {
        throw new UnsupportedOperationException("getAEPin is not supported by this handler");
    }

    default PinsState getPinsState() {
        return PinsState.DISABLED;
    }

    default void setPinsState(PinsState state) {
        throw new UnsupportedOperationException("setPinsState is not supported by this handler");
    }

}
