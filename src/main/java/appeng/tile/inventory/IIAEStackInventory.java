package appeng.tile.inventory;

import appeng.api.storage.StorageName;

public interface IIAEStackInventory {

    void saveAEStackInv();

    IAEStackInventory getAEInventoryByName(StorageName name);
}
