package appeng.client.me;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IDisplayRepo;

public class PinSlotME extends InternalSlotME {

    public PinSlotME(final IDisplayRepo def, final int offset, final int displayX, final int displayY) {
        super(def, offset, displayX, displayY);
    }

    @Override
    ItemStack getStack() {
        return this.repo.getAEPin(offset) instanceof IAEItemStack ais ? ais.getItemStack() : null;
    }

    @Override
    IAEItemStack getAEStack() {
        return this.repo.getAEPin(offset) instanceof IAEItemStack ais ? ais : null;
    }
}
