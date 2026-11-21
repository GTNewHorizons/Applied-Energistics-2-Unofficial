package appeng.gametests.compatibility.appliedenergistics2_gregtech;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.fluidStack;
import static appeng.gametests.AEGameTestHelpers.itemStack;

import java.lang.reflect.Proxy;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileController;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.objects.GTDualInputPattern;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME.PatternSlot;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "gregtech")
public class TunnelPatternGTTests {

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void inputAssemblyRetainsExpandedInputsForRecipeCaching(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        TileInterface definitionInterface = helper.assertTileEntityPresent(TileInterface.class, "block_interface");
        ItemStack definition = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound definitionTags = tags(
                new IAEStack<?>[] { itemStack(Blocks.cobblestone, 3), fluidStack(FluidRegistry.WATER, 1_000) },
                new IAEStack<?>[0]);
        ItemTunnelPattern.writeTunnelUuid(definitionTags, UUID.randomUUID());
        definition.setTagCompound(definitionTags);
        ItemStack reference = definition.copy();
        reference.stackSize = 2;
        ItemStack processing = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        processing.setTagCompound(
                tags(
                        new IAEStack<?>[] { AEApi.instance().storage().createItemStack(reference) },
                        new IAEStack<?>[] { itemStack(Blocks.stone, 1) }));

        helper.startSequence().thenWaitUntil("wait for the definition provider to activate", 80, () -> {
            assertActive(helper, controller.getProxy(), "Controller should become active");
            assertActive(helper, definitionInterface.getProxy(), "Definition interface should become active");
        }).thenExecute("verify GT's retained input assembly details and recipe lookup", () -> {
            InventoryHelper.setSlot(definitionInterface.getInterfaceDuality().getPatterns(), 0, definition);
            MTEHatchCraftingInputME hatch = new MTEHatchCraftingInputME(
                    "test_tunnel_pattern",
                    6,
                    new String[0],
                    null,
                    true);
            IGregTechTileEntity baseTile = (IGregTechTileEntity) Proxy.newProxyInstance(
                    IGregTechTileEntity.class.getClassLoader(),
                    new Class<?>[] { IGregTechTileEntity.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("getWorld")) return helper.getWorld();
                        if (method.getName().equals("setMetaTileEntity")) return null;
                        throw new UnsupportedOperationException(method.getName());
                    });
            hatch.setBaseMetaTileEntity(baseTile);
            PatternSlot<MTEHatchCraftingInputME> slot = new PatternSlot<>(processing, hatch);
            ICraftingPatternDetails retainedDetails = slot.getPatternDetails();
            try {
                CraftingGridCache cache = (CraftingGridCache) controller.getProxy().getCrafting();
                cache.addCraftingOption(hatch, retainedDetails);
                cache.setMockPatternsFromMethods();
            } catch (GridAccessException exception) {
                throw new AssertionError("Input assembly should access the crafting grid", exception);
            }

            GTDualInputPattern inputs = slot.getPatternInputs();
            helper.assertSame(retainedDetails, slot.getPatternDetails(), "GT should retain its original details");
            helper.assertEquals(1, inputs.inputItems.length, "GT should see the expanded item input");
            helper.assertSame(
                    Item.getItemFromBlock(Blocks.cobblestone),
                    inputs.inputItems[0].getItem(),
                    "GT should see cobblestone instead of a Tunnel Pattern reference");
            helper.assertEquals(6, inputs.inputItems[0].stackSize, "GT should see the multiplied item count");
            helper.assertEquals(1, inputs.inputFluid.length, "GT should see the expanded fluid input");
            helper.assertSame(FluidRegistry.WATER, inputs.inputFluid[0].getFluid(), "GT should see water");
            helper.assertEquals(2_000, inputs.inputFluid[0].amount, "GT should see the multiplied fluid count");

            RecipeMap<?> recipes = RecipeMapBuilder.of("ae2.test.tunnel_inputs." + UUID.randomUUID()).maxIO(1, 1, 1, 0)
                    .minInputs(1, 1).build();
            GTValues.RA.stdBuilder().itemInputs(new ItemStack(Blocks.cobblestone, 6))
                    .itemOutputs(new ItemStack(Blocks.stone)).fluidInputs(new FluidStack(FluidRegistry.WATER, 2_000))
                    .duration(20).eut(8).addTo(recipes);
            helper.assertTrue(
                    new ProcessingLogic().setRecipeMap(recipes).tryCachePossibleRecipesFromPattern(slot),
                    "GT recipe caching should accept the input assembly's expanded items and fluids");
        }).thenSucceed();
    }

    private static NBTTagCompound tags(IAEStack<?>[] inputs, IAEStack<?>[] outputs) {
        NBTTagCompound tags = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        NBTTagList out = new NBTTagList();
        for (IAEStack<?> input : inputs) {
            in.appendTag(input.toNBTGeneric());
        }
        for (IAEStack<?> output : outputs) {
            out.appendTag(output.toNBTGeneric());
        }
        tags.setBoolean("crafting", false);
        tags.setTag("in", in);
        tags.setTag("out", out);
        return tags;
    }
}
