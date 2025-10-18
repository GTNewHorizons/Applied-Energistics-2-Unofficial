package appeng.tile.inventory;

import appeng.client.StorageName;

public interface IIAEStackInventory {

    void saveAEStackInv();

    IAEStackInventory getAEInventoryByName(StorageName name);
}
