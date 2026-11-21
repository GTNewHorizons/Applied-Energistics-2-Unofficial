package appeng.gametests.compatibility.appliedenergistics2_ae2fc;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredFluidAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.fluidStack;
import static appeng.gametests.AEGameTestHelpers.insertFluids;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;

import com.glodblock.github.loader.ItemAndBlockHolder;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.misc.PartPatternRepeater;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "ae2fc")
public class TunnelPatternRepeaterCraftingTests {

    @GameTest(template = "network_core", timeoutTicks = 200)
    public static void targetLocalPatternPlansWithSourceNetworkFluidDefinition(GameTestHelper helper) {
        TileController sourceController = helper.assertTileEntityPresent(TileController.class, "controller");
        helper.setBlock("drive", AEApi.instance().definitions().blocks().creativeEnergyController().maybeBlock().get());
        TileController targetController = helper.assertTileEntityPresent(TileController.class, "drive");
        helper.setBlock("cable_2", AEApi.instance().definitions().blocks().iface().maybeBlock().get());
        TileInterface sourceInterface = helper.assertTileEntityPresent(TileInterface.class, "cable_2");
        helper.setBlock("cable_10", AEApi.instance().definitions().blocks().iface().maybeBlock().get());
        TileInterface targetInterface = helper.assertTileEntityPresent(TileInterface.class, "cable_10");
        helper.setBlock("cable_9", AEApi.instance().definitions().blocks().drive().maybeBlock().get());
        TileDrive targetDrive = helper.assertTileEntityPresent(TileDrive.class, "cable_9");
        placeCable(helper, "cable_1", AEColor.Red);
        TileCableBus accessorHost = placeCable(helper, "cable_3", AEColor.Red);
        TileCableBus providerHost = placeCable(helper, "cable_4", AEColor.Blue);
        for (int cable = 5; cable <= 8; cable++) {
            placeCable(helper, "cable_" + cable, AEColor.Blue);
        }
        ItemStack repeaterStack = AEApi.instance().definitions().parts().patternRepeater().maybeStack(1).get();
        accessorHost.addPart(repeaterStack, ForgeDirection.EAST, null);
        providerHost.addPart(repeaterStack.copy(), ForgeDirection.WEST, null);
        PartPatternRepeater accessor = (PartPatternRepeater) accessorHost.getPart(ForgeDirection.EAST);
        PartPatternRepeater provider = (PartPatternRepeater) providerHost.getPart(ForgeDirection.WEST);
        NBTTagCompound settings = new NBTTagCompound();
        settings.setTag("waitingStacks", new NBTTagList());
        settings.setBoolean("provider", true);
        provider.readFromNBT(settings);
        provider.gridChanged();
        UUID uuid = UUID.randomUUID();
        ItemStack definition = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound definitionTags = tags(
                new IAEStack<?>[] { itemStack(Blocks.cobblestone, 3), fluidStack(FluidRegistry.WATER, 1_000) },
                new IAEStack<?>[0]);
        ItemTunnelPattern.writeTunnelUuid(definitionTags, uuid);
        definition.setTagCompound(definitionTags);
        ItemStack reference = definition.copy();
        reference.stackSize = 2;
        ItemStack processing = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        processing.setTagCompound(
                tags(
                        new IAEStack<?>[] { AEApi.instance().storage().createItemStack(reference) },
                        new IAEStack<?>[] { itemStack(Blocks.stone, 1) }));
        ItemStack itemCell = cell1k();
        ItemStack fluidCell = ItemAndBlockHolder.CELL1K.stack();
        insertItems(helper, itemCell, Blocks.cobblestone, 12);
        insertFluids(helper, fluidCell, FluidRegistry.WATER, 4_000);

        helper.startSequence().thenWaitUntil("wait for separate active source and target networks", 80, () -> {
            assertActive(helper, sourceController.getProxy(), "Source controller should become active");
            assertActive(helper, targetController.getProxy(), "Target controller should become active");
            assertActive(helper, sourceInterface.getProxy(), "Source interface should become active");
            assertActive(helper, targetInterface.getProxy(), "Target interface should become active");
            assertActive(helper, targetDrive.getProxy(), "Target drive should become active");
            assertActive(helper, accessor, "Accessor should become active");
            assertActive(helper, provider, "Provider should become active");
            helper.assertNotSame(
                    sourceController.getProxy().getNode().getGrid(),
                    targetController.getProxy().getNode().getGrid(),
                    "Planning must cross two distinct networks");
        }).thenExecute("install the definition only in A and the recipe and real item/fluid storage only in B", () -> {
            InventoryHelper.setSlot(sourceInterface.getInterfaceDuality().getPatterns(), 0, definition);
            InventoryHelper.setSlot(targetInterface.getInterfaceDuality().getPatterns(), 0, processing);
            InventoryHelper.setSlot(targetDrive, 0, itemCell);
            InventoryHelper.setSlot(targetDrive, 1, fluidCell);
        }).thenWaitUntil("wait for B to see the shared definition and real water storage", 40, () -> {
            try {
                CraftingGridCache targetCache = (CraftingGridCache) targetController.getProxy().getCrafting();
                helper.assertNotNull(targetCache.getInputOnlyPattern(uuid), "B should import A's definition normally");
                helper.assertTrue(
                        ((CraftingGridCache) sourceController.getProxy().getCrafting()).getCraftingMultiPatterns()
                                .isEmpty(),
                        "A must contain definitions only");
                helper.assertEquals(
                        4_000L,
                        targetController.getProxy().getStorage().getFluidInventory().getStorageList()
                                .findPrecise(fluidStack(FluidRegistry.WATER, 1)).getStackSize(),
                        "B should expose its stored water to planning");
            } catch (GridAccessException exception) {
                throw new AssertionError("Network caches should be accessible", exception);
            }
        }).thenExecute("calculate executable standard and lite jobs through B's real crafting planners", () -> {
            for (boolean lite : new boolean[] { false, true }) {
                assertExecutablePlan(helper, targetController, lite);
            }
            assertStoredAmount(helper, targetDrive.getStackInSlot(0), Blocks.cobblestone, 12);
            assertStoredFluidAmount(helper, targetDrive.getStackInSlot(1), FluidRegistry.WATER, 4_000);
        }).thenSucceed();
    }

    private static void assertExecutablePlan(GameTestHelper helper, TileController controller, boolean lite) {
        try {
            CraftingGridCache cache = (CraftingGridCache) controller.getProxy().getCrafting();
            Future<ICraftingJob> future = cache.beginCraftingJob(
                    controller.getWorldObj(),
                    controller.getProxy().getGrid(),
                    new BaseActionSource(),
                    itemStack(Blocks.stone, 2),
                    CraftingMode.STANDARD,
                    lite,
                    null);
            ICraftingJob job = future.get(5_000, TimeUnit.MILLISECONDS);
            helper.assertFalse(
                    job.isSimulation(),
                    "Shared fluid definition should produce an executable plan; lite=" + lite);
            IItemList<IAEStack<?>> plan = AEApi.instance().storage().createAEStackList();
            job.populatePlan(plan);
            helper.assertEquals(
                    12L,
                    plan.findPrecise(itemStack(Blocks.cobblestone, 1)).getStackSize(),
                    "Plan should use the actual item quantity");
            helper.assertEquals(
                    4_000L,
                    plan.findPrecise(fluidStack(FluidRegistry.WATER, 1)).getStackSize(),
                    "Plan should use the actual shared-definition fluid quantity");
            for (IAEStack<?> input : plan) {
                helper.assertFalse(
                        input instanceof IAEItemStack item && item.getItem() instanceof ItemTunnelPattern,
                        "Plan must not report Tunnel Pattern items as missing ingredients");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Crafting job calculation should not be interrupted", exception);
        } catch (ExecutionException | TimeoutException | GridAccessException exception) {
            throw new AssertionError("Crafting job should calculate normally", exception);
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
        tags.setTag("in", in);
        tags.setTag("out", out);
        tags.setBoolean("crafting", false);
        return tags;
    }
}
