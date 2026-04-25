package appeng.container.interfaces;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;

public interface IVirtualSlotSource {

    void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes);
}
