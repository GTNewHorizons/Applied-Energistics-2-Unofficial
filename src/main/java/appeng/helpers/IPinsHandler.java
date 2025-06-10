package appeng.helpers;

import net.minecraft.item.ItemStack;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEItemStack;

public interface IPinsHandler {

    default int getPinCount() {
        return PinsState.getPinsCount();
    }

    default void setPin(ItemStack is, int idx) {}

    default void setAEPins(IAEItemStack[] pins) {}

    default IAEItemStack getPin(int idx) {
        return null;
    }
}
