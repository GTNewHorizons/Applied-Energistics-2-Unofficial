package appeng.helpers;

import appeng.api.storage.data.IAEStack;
import appeng.client.StorageName;

public interface IVirtualSlotSource {

    void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes);
}
