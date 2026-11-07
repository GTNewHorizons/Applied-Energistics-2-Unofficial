package appeng.parts.networking;

import net.minecraft.item.ItemStack;

import appeng.helpers.Reflected;

public class PartWirelessConnectorOuter extends PartWirelessOuterBase {

    @Reflected
    public PartWirelessConnectorOuter(final ItemStack is) {
        super(is, 1, 0);
    }
}
