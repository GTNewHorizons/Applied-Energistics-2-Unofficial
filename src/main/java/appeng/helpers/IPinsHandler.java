package appeng.helpers;

import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.items.contents.PinList;

public interface IPinsHandler {

    default int getPinCount() {
        return PinList.TOTAL_SLOTS;
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

    default PinsRows getCraftingPinsRows() {
        return PinsRows.DISABLED;
    }

    default void setCraftingPinsRows(PinsRows rows) {
        throw new UnsupportedOperationException("setCraftingPinsRows is not supported by this handler");
    }

    default PinsRows getPlayerPinsRows() {
        return PinsRows.DISABLED;
    }

    default void setPlayerPinsRows(PinsRows rows) {
        throw new UnsupportedOperationException("setPlayerPinsRows is not supported by this handler");
    }
}
