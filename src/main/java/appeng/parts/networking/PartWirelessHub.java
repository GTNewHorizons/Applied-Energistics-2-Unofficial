package appeng.parts.networking;

import net.minecraft.item.ItemStack;

import appeng.helpers.Reflected;

public class PartWirelessHub extends PartWirelessBase {

    @Reflected
    public PartWirelessHub(final ItemStack is) {
        super(is, 32, 17);
    }
}
