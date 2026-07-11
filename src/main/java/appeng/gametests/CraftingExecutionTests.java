package appeng.gametests;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertChestStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.networkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.pos;
import static appeng.gametests.AEGameTestHelpers.tile;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers.Coord;
import appeng.me.GridAccessException;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;

@GameTestHolder(AppEng.MOD_ID)
public class CraftingExecutionTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String CPU_STORAGE_LABEL = "cpu_storage";
    private static final String CPU_UNIT_LABEL = "cpu_unit";
    private static final String INTERFACE_LABEL = "interface";
    private static final String ASSEMBLER_LABEL = "assembler";

    private static final int JOB_CALCULATION_TIMEOUT_MS = 5_000;

    // A crafting pattern should execute through the CPU, interface, molecular assembler, and ME storage.
    @GameTest(template = "crafting_cpu", timeoutTicks = 520)
    public static void molecularAssemblerCraftsEncodedPattern(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.planks, 4);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(100, () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(() -> installPattern(network.blockInterface, encodedCraftingTablePattern()))
                .thenWaitUntil(
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.crafting_table).isEmpty(),
                                "Crafting table pattern should be advertised"))
                .thenExecute(() -> submitCraft(helper, network.controller, Blocks.crafting_table, 1))
                .thenWaitUntil(260, () -> {
                    assertNetworkStoredAmount(helper, network.controller, Blocks.crafting_table, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.planks, 0);
                    assertStoredAmount(helper, driveCell, Blocks.crafting_table, 1);
                    assertStoredAmount(helper, driveCell, Blocks.planks, 0);
                    assertNotRequesting(helper, network.controller, Blocks.crafting_table);
                }).thenSucceed();
    }

    // A processing pattern should push inputs out, wait for the declared output, then complete once it returns.
    @GameTest(template = "crafting_cpu", timeoutTicks = 620)
    public static void processingPatternPushesInputsAndAcceptsReturnedOutput(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        AtomicReference<TileEntityChest> processingTarget = new AtomicReference<>();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(100, () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(() -> processingTarget.set(placeProcessingTarget(helper))).thenExecute(() -> {
                    installPattern(
                            network.blockInterface,
                            encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1));
                    network.blockInterface.getConfigManager()
                            .putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
                })
                .thenWaitUntil(
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Processing pattern output should be advertised"))
                .thenExecute(() -> submitCraft(helper, network.controller, Blocks.stone, 1)).thenWaitUntil(220, () -> {
                    assertStoredAmount(helper, driveCell, Blocks.cobblestone, 0);
                    assertChestStoredAmount(helper, processingTarget.get(), Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.LOCK_UNTIL_RESULT,
                            "Interface should wait for the processing result");
                }).thenExecute(() -> {
                    clearInventory(processingTarget.get());
                    IAEItemStack remainder = injectIntoGrid(network.controller, Blocks.stone, 1);
                    helper.assertNull(remainder, "Returned processing output should fit into the network");
                }).thenWaitUntil(160, () -> {
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 0);
                    assertStoredAmount(helper, driveCell, Blocks.stone, 1);
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.NONE,
                            "Returned output should unlock the interface");
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                }).thenSucceed();
    }

    // Cancelling a blocked processing job should return CPU-held ingredients without producing output.
    @GameTest(template = "crafting_cpu", timeoutTicks = 380)
    public static void cancelledJobReturnsIngredients(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(100, () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        () -> installPattern(
                                network.blockInterface,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocked processing pattern should be advertised"))
                .thenExecute(() -> {
                    link.set(submitCraft(helper, network.controller, Blocks.stone, 1));
                    IAEStack<?> requestedOutput = itemStack(Blocks.stone, 1);
                    helper.assertTrue(
                            craftingGrid(network.controller).isRequesting(requestedOutput),
                            "Submitted craft should be tracked as an active request");
                }).thenExecute(() -> {
                    link.get().cancel();
                    helper.assertTrue(link.get().isCanceled(), "Crafting link should be canceled");
                }).thenWaitUntil(120, () -> {
                    assertStoredAmount(helper, driveCell, Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                }).thenSucceed();
    }

    // Breaking a CPU tile during a job should stop the craft without duplicating ingredients or outputs.
    @GameTest(template = "crafting_cpu", timeoutTicks = 380)
    public static void cpuBreakCancelsWithoutDuplication(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        network.drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(100, () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        () -> installPattern(
                                network.blockInterface,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocked processing pattern should be advertised"))
                .thenExecute(() -> link.set(submitCraft(helper, network.controller, Blocks.stone, 1)))
                .thenWaitUntil(100, () -> assertStoredAmount(helper, driveCell, Blocks.cobblestone, 0))
                .thenExecute(() -> destroyBlock(helper, CPU_UNIT_LABEL)).thenIdle(10).thenExecute(() -> {
                    long accountedCobblestone = networkStoredAmount(network.controller, Blocks.cobblestone)
                            + droppedItemAmount(helper, CPU_UNIT_LABEL, Blocks.cobblestone);
                    long accountedStone = networkStoredAmount(network.controller, Blocks.stone)
                            + droppedItemAmount(helper, CPU_UNIT_LABEL, Blocks.stone);

                    helper.assertTrue(link.get().isCanceled(), "Crafting link should be canceled when the CPU breaks");
                    helper.assertEquals(
                            1L,
                            accountedCobblestone,
                            "Ingredient should exist exactly once after CPU break");
                    helper.assertEquals(0L, accountedStone, "CPU break should not produce the requested output");
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                }).thenSucceed();
    }

    private static CraftingNetwork getCraftingNetwork(GameTestHelper helper) {
        TileController controller = tile(helper, TileController.class, CONTROLLER_LABEL);
        TileDrive drive = tile(helper, TileDrive.class, DRIVE_LABEL);
        TileCraftingStorageTile cpuStorage = tile(helper, TileCraftingStorageTile.class, CPU_STORAGE_LABEL);
        TileCraftingTile cpuUnit = tile(helper, TileCraftingTile.class, CPU_UNIT_LABEL);
        TileInterface blockInterface = tile(helper, TileInterface.class, INTERFACE_LABEL);
        TileMolecularAssembler assembler = tile(helper, TileMolecularAssembler.class, ASSEMBLER_LABEL);

        return new CraftingNetwork(controller, drive, cpuStorage, cpuUnit, blockInterface, assembler);
    }

    private static void assertCraftingNetworkActive(GameTestHelper helper, CraftingNetwork network) {
        assertActive(helper, network.controller.getProxy(), "Controller grid proxy should become active");
        assertActive(helper, network.drive.getProxy(), "Drive grid proxy should become active");
        assertActive(helper, network.cpuStorage.getProxy(), "CPU storage should receive a channel");
        assertActive(helper, network.cpuUnit.getProxy(), "CPU unit should receive a channel");
        assertActive(helper, network.blockInterface.getProxy(), "Interface should receive a channel");
        assertActive(helper, network.assembler.getProxy(), "Assembler should receive a channel");
        helper.assertTrue(network.cpuStorage.isFormed(), "CPU storage should form a crafting CPU");
        helper.assertTrue(network.cpuUnit.isFormed(), "CPU unit should form a crafting CPU");
    }

    private static TileEntityChest placeProcessingTarget(GameTestHelper helper) {
        Coord assemblerPos = pos(helper, ASSEMBLER_LABEL);
        helper.setBlock(assemblerPos.x(), assemblerPos.y(), assemblerPos.z(), Blocks.chest);
        return helper
                .assertTileEntityPresent(TileEntityChest.class, assemblerPos.x(), assemblerPos.y(), assemblerPos.z());
    }

    private static void installPattern(TileInterface blockInterface, ItemStack encodedPattern) {
        blockInterface.getInterfaceDuality().getPatterns().setInventorySlotContents(0, encodedPattern);
    }

    private static ICraftingLink submitCraft(GameTestHelper helper, TileController controller, Block output,
            long amount) {
        BaseActionSource source = new BaseActionSource();

        try {
            IGrid grid = controller.getProxy().getGrid();
            ICraftingGrid crafting = controller.getProxy().getCrafting();
            IAEStack<?> requestedOutput = itemStack(output, amount);
            Future<ICraftingJob> future = crafting
                    .beginCraftingJob(controller.getWorldObj(), grid, source, requestedOutput, null);
            ICraftingJob job = future.get(JOB_CALCULATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            helper.assertFalse(job.isSimulation(), "Crafting job should be executable");
            ICraftingLink link = crafting.submitJob(job, null, null, false, source);
            helper.assertNotNull(link, "Crafting job should submit to an available CPU");
            return link;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Crafting job calculation should not be interrupted", e);
        } catch (ExecutionException | TimeoutException | GridAccessException e) {
            throw new AssertionError("Crafting job should calculate and submit", e);
        }
    }

    private static void assertNotRequesting(GameTestHelper helper, TileController controller, Block output) {
        IAEStack<?> requestedOutput = itemStack(output, 1);
        helper.assertFalse(
                craftingGrid(controller).isRequesting(requestedOutput),
                "Crafting grid should not still request the output");
    }

    private static ICraftingGrid craftingGrid(TileController controller) {
        try {
            return controller.getProxy().getCrafting();
        } catch (GridAccessException e) {
            throw new AssertionError("Network crafting cache should be accessible", e);
        }
    }

    private static Collection<?> craftingOptionsFor(TileController controller, Block output) {
        IAEStack<?> requestedOutput = itemStack(output, 1);
        return craftingGrid(controller).getCraftingFor(requestedOutput, null, -1, controller.getWorldObj());
    }

    private static ItemStack encodedCraftingTablePattern() {
        ItemStack encodedPattern = encodedPattern();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();

        patternTags.setBoolean("crafting", true);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(Blocks.planks, 1));
        inputs.appendTag(itemTag(Blocks.planks, 1));
        inputs.appendTag(new NBTTagCompound());
        inputs.appendTag(itemTag(Blocks.planks, 1));
        inputs.appendTag(itemTag(Blocks.planks, 1));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", new NBTTagList());
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static ItemStack encodedProcessingPattern(Block input, int inputAmount, Block output, int outputAmount) {
        ItemStack encodedPattern = encodedPattern();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(input, inputAmount));
        outputs.appendTag(itemTag(output, outputAmount));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static ItemStack encodedPattern() {
        return AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
    }

    private static NBTTagCompound itemTag(Block block, int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        Platform.writeItemStackToNBT(new ItemStack(block, amount), tag);
        return tag;
    }

    private static void clearInventory(IInventory inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            inventory.setInventorySlotContents(slot, null);
        }
        inventory.markDirty();
    }

    private static void destroyBlock(GameTestHelper helper, String label) {
        Coord pos = pos(helper, label);
        helper.destroyBlock(pos.x(), pos.y(), pos.z());
    }

    private static long droppedItemAmount(GameTestHelper helper, String centerLabel, Block block) {
        Coord center = pos(helper, centerLabel);
        TestPos absolute = helper.absolute(center.x(), center.y(), center.z());
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                absolute.x() - 4,
                absolute.y() - 2,
                absolute.z() - 4,
                absolute.x() + 5,
                absolute.y() + 3,
                absolute.z() + 5);
        List<EntityItem> drops = helper.getWorld().getEntitiesWithinAABB(EntityItem.class, box);
        ItemStack expected = new ItemStack(block, 1);
        long amount = 0;

        for (EntityItem drop : drops) {
            ItemStack stack = drop.getEntityItem();
            if (stack != null && stack.isItemEqual(expected)) {
                amount += stack.stackSize;
            }
        }

        return amount;
    }

    @Desugar
    private record CraftingNetwork(TileController controller, TileDrive drive, TileCraftingStorageTile cpuStorage,
            TileCraftingTile cpuUnit, TileInterface blockInterface, TileMolecularAssembler assembler) {

    }
}
