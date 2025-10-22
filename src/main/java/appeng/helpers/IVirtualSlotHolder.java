package appeng.helpers;

import appeng.api.storage.data.IAEStack;
import appeng.client.StorageName;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public interface IVirtualSlotHolder {

    void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks);
}
