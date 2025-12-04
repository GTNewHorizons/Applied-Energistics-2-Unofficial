package appeng.api.parts;

import appeng.api.implementations.tiles.ISegmentedInventory;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.util.IConfigManagerHost;

public interface ICraftingTerminal extends ITerminalHost, IConfigManagerHost, IViewCellStorage, IAEAppEngInventory,
        ITerminalPins, ISegmentedInventory {
}
