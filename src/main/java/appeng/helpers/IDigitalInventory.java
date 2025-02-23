package appeng.helpers;

import appeng.api.storage.data.IAEItemStack;

public interface IDigitalInventory {

    IAEItemStack simulateAdd(final IAEItemStack toBeSimulated);

    IAEItemStack addItems(final IAEItemStack toBeAdded);

    default boolean isDigitalMode() {
        return true;
    }
}
