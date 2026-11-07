package appeng.parts.networking;

import net.minecraft.item.ItemStack;

import appeng.helpers.Reflected;

public class PartWirelessHubOuter extends PartWirelessOuterBase {

    @Reflected
    public PartWirelessHubOuter(final ItemStack is) {
        super(is, 32, 17);
    }
}
