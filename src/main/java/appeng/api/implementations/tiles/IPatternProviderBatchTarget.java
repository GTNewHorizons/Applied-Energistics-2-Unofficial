package appeng.api.implementations.tiles;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/**
 * Optional capability for machines that can report how many executions of a pattern they can accept together.
 */
public interface IPatternProviderBatchTarget {

    /**
     * Returns the maximum number of executions that can be accepted in one push.
     *
     * The table contains the complete inputs for the candidate merged push; stack sizes are already multiplied by the
     * requested multiplier. Implementations must not mutate it while checking the limit. Returning zero means that this
     * target cannot accept the pattern in its current state.
     */
    default int getMaxPatternPushMultiplier(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final int maxMultiplier, final ForgeDirection ejectionDirection) {
        return maxMultiplier <= 0 ? 0 : 1;
    }
}
