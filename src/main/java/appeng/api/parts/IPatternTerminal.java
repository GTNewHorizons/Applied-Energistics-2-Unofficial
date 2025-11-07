package appeng.api.parts;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.PatternHelper;
import appeng.helpers.UltimatePatternHelper;
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.IConfigManagerHost;

public interface IPatternTerminal extends IIAEStackInventory, ITerminalHost, IConfigManagerHost, IViewCellStorage,
        IAEAppEngInventory, ITerminalPins, IActionHost {

    boolean isCraftingRecipe();

    void setCraftingRecipe(final boolean craftingMode);

    boolean isSubstitution();

    boolean canBeSubstitution();

    void setSubstitution(boolean canSubstitute);

    void setCanBeSubstitution(boolean beSubstitute);

    IInventory getInventoryByName(final String name);

    void exPatternTerminalCall(IAEStack<?>[] in, IAEStack<?>[] out);

    default void loadPatternFromItem(final ItemStack stack, World world, IAEStackInventory crafting,
            IAEStackInventory output) {
        if (stack != null && stack.getItem() instanceof ICraftingPatternItem pattern) {
            final NBTTagCompound encodedValue = stack.getTagCompound();

            if (encodedValue != null) {
                final ICraftingPatternDetails details = pattern.getPatternForItem(stack, world);
                final boolean substitute = encodedValue.getBoolean("substitute");
                final boolean beSubstitute = encodedValue.getBoolean("beSubstitute");
                final boolean isCrafting = encodedValue.getBoolean("crafting");
                final IAEStack<?>[] inItems;
                final IAEStack<?>[] outItems;

                if (details == null) {
                    if (stack.getItem() instanceof ItemEncodedPattern) {
                        inItems = PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems = PatternHelper
                                .loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), true, null);
                    } else {
                        inItems = UltimatePatternHelper
                                .loadIAEStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems = UltimatePatternHelper
                                .loadIAEStackFromNBT(encodedValue.getTagList("out", 10), true, null);
                    }
                } else {
                    inItems = details.getAEInputs();
                    outItems = details.getAEOutputs();
                }

                this.setCraftingRecipe(isCrafting);
                this.setSubstitution(substitute);
                this.setCanBeSubstitution(beSubstitute);

                exPatternTerminalCall(inItems, outItems);

                for (int x = 0; x < crafting.getSizeInventory(); x++) {
                    crafting.putAEStackInSlot(x, null);
                }

                for (int x = 0; x < output.getSizeInventory(); x++) {
                    output.putAEStackInSlot(x, null);
                }

                for (int x = 0; x < crafting.getSizeInventory() && x < inItems.length; x++) {
                    if (inItems[x] != null) {
                        crafting.putAEStackInSlot(x, inItems[x]);
                    }
                }

                for (int x = 0; x < output.getSizeInventory() && x < outItems.length; x++) {
                    if (outItems[x] != null) {
                        output.putAEStackInSlot(x, outItems[x]);
                    }
                }
            }
        }
    }
}
