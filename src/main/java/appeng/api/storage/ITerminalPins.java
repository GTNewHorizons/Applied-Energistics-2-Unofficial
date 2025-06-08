package appeng.api.storage;

import appeng.api.networking.IGrid;
import appeng.tile.inventory.AppEngInternalAEInventory;

public interface ITerminalPins {

    AppEngInternalAEInventory getPins();

    IGrid getGrid();
}
