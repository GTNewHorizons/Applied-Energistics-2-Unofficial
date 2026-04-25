package appeng.container.interfaces;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public interface IVirtualSlotHolder {

    void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks);
}
