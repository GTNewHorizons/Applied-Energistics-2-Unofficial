package appeng.items.contents;

import net.minecraft.item.ItemStack;

import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.Platform;

public class WirelessTerminalPins extends AppEngInternalAEInventory {

    private final ItemStack is;

    public WirelessTerminalPins(final ItemStack is) {
        super(null, 9);
        this.is = is;
        this.readFromNBT(Platform.openNbtData(is), "pins");
    }

    @Override
    public void markDirty() {
        this.writeToNBT(Platform.openNbtData(is), "pins");
    }
}
