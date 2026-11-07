package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.fluidStack;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.google.common.collect.ImmutableCollection;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.helpers.IInterfaceHost;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartPatternRepeater;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;

@GameTestHolder(AppEng.MOD_ID)
public class TunnelPatternTests {

    @GameTest(template = "interface_network", timeoutTicks = 200)
    public static void interfacesExposeResolvedInputsAndDefinitionUpdates(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        TileInterface blockInterface = helper.assertTileEntityPresent(TileInterface.class, "block_interface");
        PartInterface partInterface = part(helper, "part_interface_host", PartInterface.class);
        UUID uuid = UUID.randomUUID();
        ItemStack tunnel = tunnel(uuid, itemStack(Blocks.cobblestone, 3), fluidStack(FluidRegistry.WATER, 1_000));
        ItemStack reference = tunnel.copy();
        reference.stackSize = 2;
        ItemStack processing = processing(reference);

        helper.startSequence().thenWaitUntil("wait for interface network activation", 80, () -> {
            assertActive(helper, controller.getProxy(), "Controller should become active");
            assertActive(helper, blockInterface.getProxy(), "Block interface should become active");
            assertActive(helper, partInterface, "Part interface should become active");
        }).thenExecute("install item and fluid Tunnel Pattern inputs", () -> {
            InventoryHelper.setSlot(blockInterface.getInterfaceDuality().getPatterns(), 0, tunnel);
            InventoryHelper.setSlot(blockInterface.getInterfaceDuality().getPatterns(), 1, processing);
            InventoryHelper.setSlot(partInterface.getInterfaceDuality().getPatterns(), 0, processing.copy());
        }).thenWaitUntil("wait for resolved interface inputs", 40, () -> {
            assertMixedInputs(helper, firstPattern(helper, controller), 6, 2_000);
            assertProviderInputs(helper, blockInterface, 6, 2_000);
            assertProviderInputs(helper, partInterface, 6, 2_000);
        }).thenExecute("replace the definition without changing its UUID", () -> {
            InventoryHelper.setSlot(
                    blockInterface.getInterfaceDuality().getPatterns(),
                    0,
                    tunnel(uuid, itemStack(Blocks.cobblestone, 5), fluidStack(FluidRegistry.WATER, 500)));
        }).thenWaitUntil("wait for both provider instances to use the updated definition", 40, () -> {
            assertProviderInputs(helper, blockInterface, 10, 1_000);
            assertProviderInputs(helper, partInterface, 10, 1_000);
        }).thenExecute(
                "remove the definition",
                () -> { InventoryHelper.clearSlot(blockInterface.getInterfaceDuality().getPatterns(), 0); })
                .thenWaitUntil("wait for removed definitions to clear resolved inputs", 40, () -> {
                    assertUnresolvedInputs(helper, firstPattern(helper, controller));
                    assertUnresolvedInputs(helper, blockInterface.getInterfaceDuality().craftingList.get(0));
                    assertUnresolvedInputs(helper, partInterface.getInterfaceDuality().craftingList.get(0));
                }).thenSucceed();
    }

    @GameTest(template = "network_core", timeoutTicks = 200)
    public static void repeaterCopiesKeepEachNetworksResolvedInputsIndependent(GameTestHelper helper) {
        TileController sourceController = helper.assertTileEntityPresent(TileController.class, "controller");
        Block controller = AEApi.instance().definitions().blocks().creativeEnergyController().maybeBlock().get();
        helper.setBlock("drive", controller);
        TileController targetController = helper.assertTileEntityPresent(TileController.class, "drive");
        placeCable(helper, "cable_1", AEColor.Red);
        TileInterface sourceInterface = placeInterface(helper, "cable_2");
        TileCableBus accessorHost = placeCable(helper, "cable_3", AEColor.Red);
        TileCableBus providerHost = placeCable(helper, "cable_4", AEColor.Blue);
        for (int cable = 5; cable <= 9; cable++) {
            placeCable(helper, "cable_" + cable, AEColor.Blue);
        }
        TileInterface targetInterface = placeInterface(helper, "cable_10");
        ItemStack repeater = AEApi.instance().definitions().parts().patternRepeater().maybeStack(1).get();
        accessorHost.addPart(repeater, ForgeDirection.EAST, null);
        providerHost.addPart(repeater.copy(), ForgeDirection.WEST, null);
        PartPatternRepeater accessor = (PartPatternRepeater) accessorHost.getPart(ForgeDirection.EAST);
        PartPatternRepeater provider = (PartPatternRepeater) providerHost.getPart(ForgeDirection.WEST);
        NBTTagCompound providerSettings = new NBTTagCompound();
        providerSettings.setTag("waitingStacks", new NBTTagList());
        providerSettings.setBoolean("provider", true);
        provider.readFromNBT(providerSettings);
        provider.gridChanged();
        UUID uuid = UUID.randomUUID();
        ItemStack sourceDefinition = tunnel(uuid, fluidStack(FluidRegistry.WATER, 1_000));
        ItemStack reference = sourceDefinition.copy();
        reference.stackSize = 2;

        helper.startSequence().thenWaitUntil("wait for two isolated repeater networks", 80, () -> {
            assertActive(helper, sourceController.getProxy(), "Source controller should become active");
            assertActive(helper, targetController.getProxy(), "Target controller should become active");
            assertActive(helper, sourceInterface.getProxy(), "Source interface should become active");
            assertActive(helper, targetInterface.getProxy(), "Target interface should become active");
            assertActive(helper, accessor, "Accessor should become active");
            assertActive(helper, provider, "Provider should become active");
            helper.assertSame(provider, accessor.getPair(), "Repeaters should form a pair");
            helper.assertNotSame(
                    sourceController.getProxy().getNode().getGrid(),
                    targetController.getProxy().getNode().getGrid(),
                    "Repeaters should connect separate grids");
        }).thenExecute("install the source definition and processing pattern", () -> {
            InventoryHelper.setSlot(sourceInterface.getInterfaceDuality().getPatterns(), 0, sourceDefinition);
            InventoryHelper.setSlot(sourceInterface.getInterfaceDuality().getPatterns(), 1, processing(reference));
        }).thenWaitUntil("wait for the exported pattern to resolve independently", 40, () -> {
            ICraftingPatternDetails source = firstPattern(helper, sourceController);
            ICraftingPatternDetails exported = firstPattern(helper, targetController);
            helper.assertNotSame(source, exported, "The receiving grid must own its own detail instance");
            helper.assertEquals(source, exported, "Exported copies must preserve medium-routing identity");
            assertFluidInputs(helper, source, FluidRegistry.WATER, 2_000);
            assertUnresolvedInputs(helper, exported);
        }).thenExecute("install another definition for the same UUID in the receiving grid", () -> {
            InventoryHelper.setSlot(
                    targetInterface.getInterfaceDuality().getPatterns(),
                    0,
                    tunnel(uuid, fluidStack(FluidRegistry.LAVA, 500)));
        }).thenWaitUntil("wait for target resolution without changing source inputs", 40, () -> {
            assertFluidInputs(helper, firstPattern(helper, sourceController), FluidRegistry.WATER, 2_000);
            assertFluidInputs(helper, firstPattern(helper, targetController), FluidRegistry.LAVA, 1_000);
        }).thenExecute("rebuild the source grid with updated contents", () -> {
            InventoryHelper.setSlot(
                    sourceInterface.getInterfaceDuality().getPatterns(),
                    0,
                    tunnel(uuid, fluidStack(FluidRegistry.WATER, 1_500)));
        }).thenWaitUntil("wait for each grid to preserve its own definition", 40, () -> {
            assertFluidInputs(helper, firstPattern(helper, sourceController), FluidRegistry.WATER, 3_000);
            assertFluidInputs(helper, firstPattern(helper, targetController), FluidRegistry.LAVA, 1_000);
        }).thenExecute("install an ordinary processing pattern", () -> {
            InventoryHelper.setSlot(
                    sourceInterface.getInterfaceDuality().getPatterns(),
                    2,
                    processing(new ItemStack(Blocks.cobblestone, 4), Blocks.dirt));
        }).thenWaitUntil("wait for ordinary patterns to be shared without reparsing", 40, () -> {
            ICraftingPatternDetails source = firstPattern(helper, sourceController, Blocks.dirt);
            ICraftingPatternDetails exported = firstPattern(helper, targetController, Blocks.dirt);
            helper.assertSame(source, exported, "Patterns without Tunnel inputs should not be cloned");
            helper.assertSame(
                    source.getAEInputs(),
                    exported.getAEInputs(),
                    "Ordinary patterns should preserve their input array identity");
        }).thenSucceed();
    }

    private static void assertProviderInputs(GameTestHelper helper, IInterfaceHost host, long items, long fluid) {
        helper.assertNotNull(host.getInterfaceDuality().craftingList, "Interface should retain its pattern details");
        for (ICraftingPatternDetails details : host.getInterfaceDuality().craftingList) {
            if (!details.isInputOnly()) {
                assertMixedInputs(helper, details, items, fluid);
            }
        }
    }

    private static void assertMixedInputs(GameTestHelper helper, ICraftingPatternDetails details, long items,
            long fluid) {
        for (IAEStack<?>[] inputs : new IAEStack<?>[][] { details.getAEInputs(), details.getCondensedAEInputs() }) {
            helper.assertEquals(2, inputs.length, "Processing pattern should expose item and fluid inputs");
            helper.assertTrue(inputs[0] instanceof IAEItemStack, "First resolved input should be an item");
            helper.assertEquals(items, inputs[0].getStackSize(), "Item multiplier should be preserved");
            helper.assertTrue(inputs[1] instanceof IAEFluidStack, "Second resolved input should be a fluid");
            helper.assertEquals(fluid, inputs[1].getStackSize(), "Fluid multiplier should be preserved");
        }
    }

    private static void assertFluidInputs(GameTestHelper helper, ICraftingPatternDetails details, Fluid fluid,
            long amount) {
        IAEStack<?>[] inputs = details.getAEInputs();
        helper.assertEquals(1, inputs.length, "Pattern should resolve to one fluid input");
        helper.assertTrue(inputs[0] instanceof IAEFluidStack, "Input should be a fluid rather than a Tunnel Pattern");
        helper.assertSame(fluid, ((IAEFluidStack) inputs[0]).getFluid(), "Each grid should use its own definition");
        helper.assertEquals(amount, inputs[0].getStackSize(), "Fluid multiplier should be preserved");
    }

    private static void assertUnresolvedInputs(GameTestHelper helper, ICraftingPatternDetails details) {
        IAEStack<?>[] inputs = details.getAEInputs();
        helper.assertEquals(1, inputs.length, "Missing definitions should retain their original reference");
        helper.assertTrue(inputs[0] instanceof IAEItemStack, "Missing definition should remain an item reference");
        helper.assertTrue(
                ItemTunnelPattern.isTunnelPattern(((IAEItemStack) inputs[0]).getItemStack()),
                "Missing definitions should not leave previously expanded inputs");
    }

    private static ICraftingPatternDetails firstPattern(GameTestHelper helper, TileController controller) {
        return firstPattern(helper, controller, Blocks.stone);
    }

    private static ICraftingPatternDetails firstPattern(GameTestHelper helper, TileController controller,
            Block output) {
        try {
            CraftingGridCache cache = (CraftingGridCache) controller.getProxy().getCrafting();
            ImmutableCollection<ICraftingPatternDetails> patterns = cache
                    .getCraftingFor(itemStack(output, 1), null, -1, controller.getWorldObj());
            helper.assertFalse(patterns.isEmpty(), "Grid should advertise the processing pattern");
            return patterns.iterator().next();
        } catch (GridAccessException exception) {
            throw new AssertionError("Crafting grid should be accessible", exception);
        }
    }

    private static TileCableBus placeCable(GameTestHelper helper, String label, AEColor color) {
        helper.setBlock(label, AEApi.instance().definitions().blocks().multiPart().maybeBlock().get());
        TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, label);
        cable.addPart(
                AEApi.instance().definitions().parts().cableGlass().stack(color, 1),
                ForgeDirection.UNKNOWN,
                null);
        return cable;
    }

    private static TileInterface placeInterface(GameTestHelper helper, String label) {
        helper.setBlock(label, AEApi.instance().definitions().blocks().iface().maybeBlock().get());
        return helper.assertTileEntityPresent(TileInterface.class, label);
    }

    private static ItemStack tunnel(UUID uuid, IAEStack<?>... inputs) {
        ItemStack pattern = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound tags = tags(inputs, new IAEStack<?>[0]);
        ItemTunnelPattern.writeTunnelUuid(tags, uuid);
        pattern.setTagCompound(tags);
        return pattern;
    }

    private static ItemStack processing(ItemStack input) {
        return processing(input, Blocks.stone);
    }

    private static ItemStack processing(ItemStack input, Block output) {
        ItemStack pattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        pattern.setTagCompound(
                tags(
                        new IAEStack<?>[] { AEApi.instance().storage().createItemStack(input) },
                        new IAEStack<?>[] { itemStack(output, 1) }));
        return pattern;
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
