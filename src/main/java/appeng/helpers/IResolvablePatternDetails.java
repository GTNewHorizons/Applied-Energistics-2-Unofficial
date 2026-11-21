package appeng.helpers;

import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.items.misc.ItemTunnelPattern;

/** Processing pattern inputs are resolved by their owning grid, independently of the encoded pattern identity. */
public interface IResolvablePatternDetails extends ICraftingPatternDetails {

    IAEStack<?>[] getEncodedAEInputs();

    void setResolvedAEInputs(IAEStack<?>[] inputs);

    void resetResolvedAEInputs();

    default boolean requiresInputResolution() {
        if (this.isCraftable() || this.isInputOnly()) {
            return false;
        }
        for (IAEStack<?> input : this.getEncodedAEInputs()) {
            if (input instanceof IAEItemStack item && item.getItem() instanceof ItemTunnelPattern) {
                return true;
            }
        }
        return false;
    }

    /** Create an independent representation before exporting this pattern to another grid. */
    IResolvablePatternDetails copyForGrid(World world);
}
