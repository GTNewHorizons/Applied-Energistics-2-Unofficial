package appeng.items.misc;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.UltimatePatternHelper;

public class ItemEncodedUltimatePattern extends ItemEncodedPattern {

    public ItemEncodedUltimatePattern() {
        super();
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(final ItemStack is, final World w) {
        try {
            return new UltimatePatternHelper(is);
        } catch (final Throwable t) {
            return null;
        }
    }
}
