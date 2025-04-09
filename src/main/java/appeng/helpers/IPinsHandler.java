package appeng.helpers;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

public interface IPinsHandler {

    default void setPin(ItemStack is, int idx) {}

    default void setAEPin(IAEItemStack ais, int idx) {}
}
