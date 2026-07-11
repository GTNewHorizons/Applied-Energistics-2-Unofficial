package appeng.api.parts;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.PatternHelper;
import appeng.helpers.UltimatePatternHelper;
import appeng.items.misc.ItemEncodedPattern;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.IConfigManagerHost;
import appeng.util.item.AEItemStack;

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
                        inItems = PatternHelper
                                .loadIAEItemStackFromNBT(encodedValue.getTagList("in", NBT.TAG_COMPOUND), true, null);
                        outItems = PatternHelper
                                .loadIAEItemStackFromNBT(encodedValue.getTagList("out", NBT.TAG_COMPOUND), true, null);
                    } else {
                        inItems = UltimatePatternHelper
                                .loadIAEStackFromNBT(encodedValue.getTagList("in", NBT.TAG_COMPOUND), true, null);
                        outItems = UltimatePatternHelper
                                .loadIAEStackFromNBT(encodedValue.getTagList("out", NBT.TAG_COMPOUND), true, null);
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

    interface PatternEncodeListener {

        void onEncoded(IPatternTerminal terminal, ItemStack pattern);
    }

    Iterable<PatternEncodeListener> getPatternEncodeListeners();

    void addPatternEncodeListeners(final PatternEncodeListener listener);

    void removePatternEncodeListeners(final PatternEncodeListener listener);

    default boolean encode(String auther, World world) {
        var networkNode = getGridNode(ForgeDirection.UNKNOWN);
        final IGrid g = networkNode.getGrid();
        if (g == null) return false;
        var powerSource = new ChannelPowerSrc(networkNode, g.getCache(IEnergyGrid.class));
        var itemMonitor = (IMEMonitor<IAEItemStack>) getMEMonitor(ITEM_STACK_TYPE);
        var actionSource = new MachineSource(this);
        return encode(powerSource, itemMonitor, actionSource, auther, world);
    }

    boolean encode(IEnergySource powerSource, IMEMonitor<IAEItemStack> itemMonitor, BaseActionSource actionSource,
            String auther, World world);

    @NotNull
    static IAEItemStack createBlankPattern() {
        return AEItemStack.create(AEApi.instance().definitions().materials().blankPattern().maybeStack(1).get());
    }

    static boolean isBlankPattern(final ItemStack stack) {
        return stack != null && AEApi.instance().definitions().materials().blankPattern().isSameAs(stack);
    }

    static boolean isEncodedPattern(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemEncodedPattern;
    }
}
