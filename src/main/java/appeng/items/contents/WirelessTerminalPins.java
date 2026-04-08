package appeng.items.contents;

import net.minecraft.item.ItemStack;

import appeng.tile.inventory.AppEngInternalAEInventory;

public class WirelessTerminalPins extends AppEngInternalAEInventory {

    private final ItemStack is;

    public WirelessTerminalPins(final ItemStack is) {
        super(null, PinList.TOTAL_SLOTS);
        this.is = is;
        this.readFromNBT(is.getTagCompound(), "pins");
    }

    @Override
    public void markDirty() {
        this.writeToNBT(is, "pins");
    }
}
