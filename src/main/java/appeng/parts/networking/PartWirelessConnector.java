package appeng.parts.networking;

import net.minecraft.item.ItemStack;

import appeng.helpers.Reflected;

public class PartWirelessConnector extends PartWirelessBase {

    @Reflected
    public PartWirelessConnector(final ItemStack is) {
        super(is, 1, 0);
    }
}
