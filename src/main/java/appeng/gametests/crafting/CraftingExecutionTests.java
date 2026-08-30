package appeng.gametests.crafting;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.networkStoredAmount;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.me.GridAccessException;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.inventory.IAEStackInventory;
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
    private static final Block TEST_RECIPE_CORNER = Blocks.bedrock;
    private static final Block TEST_RECIPE_EDGE = Blocks.obsidian;
    private static final Block TEST_RECIPE_CENTER = Blocks.diamond_block;
    private static final Block TEST_RECIPE_OUTPUT = Blocks.sponge;
    private static final int TEST_RECIPE_OUTPUT_AMOUNT = 2;

    // A scoped real crafting recipe should execute through the CPU, interface, molecular assembler, and ME storage.
    @GameTest(template = "crafting_cpu", timeoutTicks = 520)
    public static void molecularAssemblerCraftsScopedShapedRecipe(GameTestHelper helper) {
        registerScopedCraftingRecipe(helper);
        CraftingNetwork network = getCraftingNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, TEST_RECIPE_CORNER, 4);
        insertItems(helper, driveCell, TEST_RECIPE_EDGE, 4);
        insertItems(helper, driveCell, TEST_RECIPE_CENTER, 1);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for crafting CPU, interface, assembler, drive, and controller to activate",
                        100,
                        () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        "install scoped shaped-recipe encoded pattern",
                        () -> installPattern(network.blockInterface, encodedScopedCraftingPattern()))
                .thenWaitUntil(
                        "wait for scoped shaped-recipe pattern advertisement",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, TEST_RECIPE_OUTPUT).isEmpty(),
                                "Scoped shaped-recipe output should be advertised"))
                .thenExecute(
                        "submit one scoped shaped-recipe craft",
                        () -> submitCraft(helper, network.controller, TEST_RECIPE_OUTPUT, 1))
                .thenWaitUntil(
                        "wait for real assembler craft to consume the nine supplied blocks and store "
                                + TEST_RECIPE_OUTPUT_AMOUNT
                                + " sponge blocks",
                        260,
                        () -> {
                            assertNetworkStoredAmount(
                                    helper,
                                    network.controller,
                                    TEST_RECIPE_OUTPUT,
                                    TEST_RECIPE_OUTPUT_AMOUNT);
                            assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_CORNER, 0);
                            assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_EDGE, 0);
                            assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_CENTER, 0);
                            ItemStack storedCell = network.drive.getStackInSlot(0);
                            assertStoredAmount(helper, storedCell, TEST_RECIPE_OUTPUT, TEST_RECIPE_OUTPUT_AMOUNT);
                            assertStoredAmount(helper, storedCell, TEST_RECIPE_CORNER, 0);
                            assertStoredAmount(helper, storedCell, TEST_RECIPE_EDGE, 0);
                            assertStoredAmount(helper, storedCell, TEST_RECIPE_CENTER, 0);
                            assertNotRequesting(helper, network.controller, TEST_RECIPE_OUTPUT);
                        })
                .thenSucceed();
    }

    // Crafting inventories must receive requested output before matching sticky storage claims any excess.
    @GameTest(template = "crafting_cpu", timeoutTicks = 520)
    public static void stickyStorageDoesNotInterceptCraftingResults(GameTestHelper helper) {
        registerScopedCraftingRecipe(helper);
        CraftingNetwork network = getCraftingNetwork(helper);
        ItemStack stickyOutputCell = cell1k();
        ItemStack ingredientCell = cell1k();
        configureStickyCell(helper, stickyOutputCell, TEST_RECIPE_OUTPUT);
        insertItems(helper, ingredientCell, TEST_RECIPE_CORNER, 4);
        insertItems(helper, ingredientCell, TEST_RECIPE_EDGE, 4);
        insertItems(helper, ingredientCell, TEST_RECIPE_CENTER, 1);
        helper.setSlot(DRIVE_LABEL, 0, stickyOutputCell);
        helper.setSlot(DRIVE_LABEL, 1, ingredientCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for sticky crafting test network to activate",
                        100,
                        () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        "install " + TEST_RECIPE_OUTPUT_AMOUNT + "-output scoped shaped-recipe pattern",
                        () -> installPattern(network.blockInterface, encodedScopedCraftingPattern()))
                .thenWaitUntil(
                        "wait for " + TEST_RECIPE_OUTPUT_AMOUNT + "-output scoped shaped-recipe pattern advertisement",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, TEST_RECIPE_OUTPUT).isEmpty(),
                                TEST_RECIPE_OUTPUT_AMOUNT + "-output scoped shaped-recipe should be advertised"))
                .thenExecute(
                        "submit one requested scoped shaped-recipe output",
                        () -> submitCraft(helper, network.controller, TEST_RECIPE_OUTPUT, 1))
                .thenWaitUntil("wait for crafting result and excess to reach sticky storage", 260, () -> {
                    assertNetworkStoredAmount(
                            helper,
                            network.controller,
                            TEST_RECIPE_OUTPUT,
                            TEST_RECIPE_OUTPUT_AMOUNT);
                    assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_CORNER, 0);
                    assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_EDGE, 0);
                    assertNetworkStoredAmount(helper, network.controller, TEST_RECIPE_CENTER, 0);
                    assertStoredAmount(
                            helper,
                            network.drive.getStackInSlot(0),
                            TEST_RECIPE_OUTPUT,
                            TEST_RECIPE_OUTPUT_AMOUNT);
                    assertNotRequesting(helper, network.controller, TEST_RECIPE_OUTPUT);
                }).thenSucceed();
    }

    // A processing pattern should push inputs out, wait for the declared output, then complete once it returns.
    @GameTest(template = "crafting_cpu", timeoutTicks = 620)
    public static void processingPatternPushesInputsAndAcceptsReturnedOutput(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        helper.startSequence()
                .thenWaitUntil(
                        "wait for processing-pattern crafting network to activate",
                        100,
                        () -> assertCraftingNetworkActive(helper, network))
                .thenExecute("replace assembler role with processing-output chest", () -> placeProcessingTarget(helper))
                .thenExecute("install locked cobblestone-to-stone processing pattern", () -> {
                    installPattern(
                            network.blockInterface,
                            encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1));
                    network.blockInterface.getConfigManager()
                            .putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
                })
                .thenWaitUntil(
                        "wait for processing pattern output advertisement",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Processing pattern output should be advertised"))
                .thenExecute(
                        "submit one stone processing craft",
                        () -> submitCraft(helper, network.controller, Blocks.stone, 1))
                .thenWaitUntil("wait for interface to push cobblestone and lock pending returned stone", 220, () -> {
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    helper.assertInventoryCount(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone), 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.LOCK_UNTIL_RESULT,
                            "Interface should wait for the processing result");
                }).thenExecute("return declared stone output to the ME network", () -> {
                    helper.clearSlot(ASSEMBLER_LABEL, 0);
                    IAEItemStack remainder = injectIntoGrid(network.controller, Blocks.stone, 1);
                    helper.assertNull(remainder, "Returned processing output should fit into the network");
                }).thenWaitUntil("wait for returned stone to finish the job and unlock the interface", 160, () -> {
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 0);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.stone, 1);
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.NONE,
                            "Returned output should unlock the interface");
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                }).thenSucceed();
    }

    // An in-flight processing job must survive persistence and reconstruction of every tile in its crafting CPU.
    @GameTest(template = "crafting_cpu", timeoutTicks = 760)
    public static void activeProcessingJobSurvivesCpuReconstruction(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        helper.assertTrue(network.cpuStorage != network.cpuUnit, "CPU storage and unit labels must resolve distinctly");
        TestPos interfacePos = helper.pos(INTERFACE_LABEL);
        TestPos assemblerPos = helper.pos(ASSEMBLER_LABEL);
        helper.assertEquals(
                1L,
                Math.abs(interfacePos.x() - assemblerPos.x()) + Math.abs(interfacePos.y() - assemblerPos.y())
                        + Math.abs(interfacePos.z() - assemblerPos.z()),
                "Interface and processing target must remain adjacent");

        placeProcessingTarget(helper);
        helper.assertInventoryEmpty(ASSEMBLER_LABEL);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 2);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        assertStoredAmount(helper, driveCell, Blocks.cobblestone, 2);
        assertStoredAmount(helper, driveCell, Blocks.stone, 0);

        AtomicReference<TileCraftingStorageTile> cpuStorage = new AtomicReference<>(network.cpuStorage);
        AtomicReference<TileCraftingTile> cpuUnit = new AtomicReference<>(network.cpuUnit);
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        AtomicReference<String> craftingId = new AtomicReference<>();
        AtomicReference<NBTTagCompound> storageState = new AtomicReference<>();
        AtomicReference<NBTTagCompound> unitState = new AtomicReference<>();
        AtomicReference<Block> storageBlock = new AtomicReference<>();
        AtomicReference<Block> unitBlock = new AtomicReference<>();
        AtomicBoolean resultReturned = new AtomicBoolean();
        WorldServer world = (WorldServer) network.controller.getWorldObj();

        TickCallbackHandle reconstructionConservesScenarioItems = helper.onEachTickDisabled(
                "CPU reconstruction never duplicates output, replays processing input, or drops items",
                () -> {
                    long tick = world.getTotalWorldTime();
                    long networkStone = networkStoredAmount(network.controller, Blocks.stone);
                    long chestCobblestone = helper.countItems(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone));
                    helper.assertTrue(
                            networkStone <= 1,
                            "At most one stone may exist after CPU reconstruction; tick=" + tick
                                    + ", network="
                                    + networkStone);
                    helper.assertEquals(
                            resultReturned.get() ? 0L : 2L,
                            chestCobblestone,
                            "Processing input must not be replayed or lost; tick=" + tick
                                    + ", chestCobblestone="
                                    + chestCobblestone);
                    helper.assertEquals(
                            0L,
                            networkStoredAmount(network.controller, Blocks.cobblestone),
                            "Dispatched cobblestone must not return to storage; tick=" + tick);
                    helper.assertTrue(
                            craftingCpuDrops(helper).isEmpty(),
                            "CPU reconstruction must not create item drops; tick=" + tick);
                });

        helper.startSequence().thenWaitUntil("wait for one usable processing CPU and powered network", 160, () -> {
            assertActive(helper, network.controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, network.drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, cpuStorage.get().getProxy(), "CPU storage should receive a channel");
            assertActive(helper, cpuUnit.get().getProxy(), "CPU unit should receive a channel");
            assertActive(helper, network.blockInterface.getProxy(), "Interface should receive a channel");
            helper.assertTrue(cpuStorage.get().isFormed(), "CPU storage should form a crafting CPU");
            helper.assertTrue(cpuUnit.get().isFormed(), "CPU unit should form a crafting CPU");
            helper.assertTrue(
                    cpuStorage.get().getCluster() == cpuUnit.get().getCluster(),
                    "CPU tiles should belong to one coherent cluster");
            helper.assertEquals(
                    1L,
                    craftingGrid(network.controller).getCpus().size(),
                    "Exactly one usable crafting CPU should be present");
            assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 2);
            assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
            helper.assertInventoryEmpty(ASSEMBLER_LABEL);
        }).thenExecute("install locked two-cobblestone-to-one-stone processing pattern", () -> {
            installPattern(network.blockInterface, encodedProcessingPattern(Blocks.cobblestone, 2, Blocks.stone, 1));
            network.blockInterface.getConfigManager()
                    .putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
        }).thenWaitUntil(
                "wait for reconstruction-test processing pattern advertisement",
                80,
                () -> helper.assertFalse(
                        craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                        "Two-cobblestone processing pattern should be advertised"))
                .thenExecute("submit one stone processing craft", () -> {
                    link.set(submitCraft(helper, network.controller, Blocks.stone, 1));
                    craftingId.set(link.get().getCraftingID());
                    helper.assertTrue(
                            craftingGrid(network.controller).isRequesting(itemStack(Blocks.stone, 1)),
                            "Submitted stone craft should be tracked as an active request");
                }).thenWaitUntil("wait for processing input dispatch and pending stone result", 240, () -> {
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                    helper.assertInventoryCount(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone), 2);
                    helper.assertFalse(link.get().isCanceled(), "Dispatched crafting link must remain active");
                    helper.assertFalse(link.get().isDone(), "Dispatched crafting link must still await stone");
                    helper.assertTrue(
                            cpuStorage.get().getCluster() instanceof CraftingCPUCluster,
                            "Dispatched job should have a formed crafting CPU cluster");
                    CraftingCPUCluster cluster = (CraftingCPUCluster) cpuStorage.get().getCluster();
                    helper.assertTrue(cluster.isBusy(), "Crafting CPU should remain busy waiting for returned stone");
                    IAEStack<?> finalOutput = cluster.getFinalMultiOutput();
                    helper.assertTrue(
                            finalOutput instanceof IAEItemStack
                                    && ((IAEItemStack) finalOutput).isSameType(itemStack(Blocks.stone, 1))
                                    && finalOutput.getStackSize() == 1,
                            "Active processing job should expect exactly one stone; observed=" + finalOutput);
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.LOCK_UNTIL_RESULT,
                            "Interface should be locked pending the returned stone");
                }).thenExecute("serialize both crafting CPU tiles independently", () -> {
                    helper.assertTrue(
                            cpuStorage.get().getCluster() == cpuUnit.get().getCluster(),
                            "Both saved members should still share one cluster");
                    storageState.set(new NBTTagCompound());
                    unitState.set(new NBTTagCompound());
                    cpuStorage.get().writeToNBT(storageState.get());
                    cpuUnit.get().writeToNBT(unitState.get());
                    boolean storageOwnsJob = storageState.get().getBoolean("core") && storageState.get().hasKey("link");
                    boolean unitOwnsJob = unitState.get().getBoolean("core") && unitState.get().hasKey("link");
                    helper.assertEquals(
                            1L,
                            (storageOwnsJob ? 1L : 0L) + (unitOwnsJob ? 1L : 0L),
                            "Exactly one saved CPU member must own the core/job payload; storageCore="
                                    + storageState.get().getBoolean("core")
                                    + ", storageLink="
                                    + storageState.get().hasKey("link")
                                    + ", unitCore="
                                    + unitState.get().getBoolean("core")
                                    + ", unitLink="
                                    + unitState.get().hasKey("link"));
                    storageBlock.set(
                            world.getBlock(cpuStorage.get().xCoord, cpuStorage.get().yCoord, cpuStorage.get().zCoord));
                    unitBlock.set(world.getBlock(cpuUnit.get().xCoord, cpuUnit.get().yCoord, cpuUnit.get().zCoord));
                }).thenIdle(1).thenExecuteAtStart("reconstruct both crafting CPU tile entities from saved NBT", () -> {
                    TileCraftingStorageTile oldStorage = cpuStorage.get();
                    TileCraftingTile oldUnit = cpuUnit.get();
                    world.removeTileEntity(oldStorage.xCoord, oldStorage.yCoord, oldStorage.zCoord);
                    world.removeTileEntity(oldUnit.xCoord, oldUnit.yCoord, oldUnit.zCoord);

                    TileCraftingStorageTile freshStorage = reconstructCraftingTile(
                            helper,
                            oldStorage,
                            storageState.get(),
                            new TileCraftingStorageTile());
                    TileCraftingTile freshUnit = reconstructCraftingTile(
                            helper,
                            oldUnit,
                            unitState.get(),
                            new TileCraftingTile());
                    cpuStorage.set(freshStorage);
                    cpuUnit.set(freshUnit);
                    helper.assertTrue(freshStorage != oldStorage, "CPU storage identity should be fresh");
                    helper.assertTrue(freshUnit != oldUnit, "CPU unit identity should be fresh");
                    helper.assertTrue(
                            world.getBlock(freshStorage.xCoord, freshStorage.yCoord, freshStorage.zCoord)
                                    == storageBlock.get(),
                            "CPU storage block must remain unchanged during tile reconstruction");
                    helper.assertTrue(
                            world.getBlock(freshUnit.xCoord, freshUnit.yCoord, freshUnit.zCoord) == unitBlock.get(),
                            "CPU unit block must remain unchanged during tile reconstruction");
                    helper.assertTrue(
                            craftingCpuDrops(helper).isEmpty(),
                            "Tile-only CPU reconstruction must not create item drops");
                    reconstructionConservesScenarioItems.enable();
                }).thenWaitUntil("wait for reconstructed CPU to reform the same active job", 240, () -> {
                    assertActive(helper, cpuStorage.get().getProxy(), "Reconstructed CPU storage should become active");
                    assertActive(helper, cpuUnit.get().getProxy(), "Reconstructed CPU unit should become active");
                    helper.assertTrue(cpuStorage.get().isFormed(), "Reconstructed CPU storage should reform");
                    helper.assertTrue(cpuUnit.get().isFormed(), "Reconstructed CPU unit should reform");
                    helper.assertTrue(
                            cpuStorage.get().getCluster() == cpuUnit.get().getCluster(),
                            "Reconstructed CPU tiles should share one coherent cluster");
                    helper.assertTrue(
                            cpuStorage.get().getCluster() instanceof CraftingCPUCluster,
                            "Reconstructed tiles should expose a crafting CPU cluster");
                    CraftingCPUCluster cluster = (CraftingCPUCluster) cpuStorage.get().getCluster();
                    helper.assertEquals(
                            1L,
                            craftingGrid(network.controller).getCpus().size(),
                            "Exactly one reconstructed crafting CPU should be usable");
                    helper.assertTrue(
                            craftingGrid(network.controller).getCpus().contains(cluster),
                            "Crafting cache should contain the reconstructed cluster");
                    helper.assertTrue(cluster.isBusy(), "Reconstructed CPU should retain the active processing job");
                    ICraftingLink restoredLink = cluster.getLastCraftingLink();
                    helper.assertNotNull(restoredLink, "Reconstructed CPU should restore its crafting link");
                    helper.assertTrue(
                            craftingId.get().equals(restoredLink.getCraftingID()),
                            "Reconstructed crafting link should preserve ID " + craftingId.get()
                                    + "; observed="
                                    + restoredLink.getCraftingID());
                    helper.assertFalse(restoredLink.isCanceled(), "Reconstructed crafting link should remain active");
                    helper.assertFalse(restoredLink.isDone(), "Reconstructed crafting link should still await stone");
                    IAEStack<?> finalOutput = cluster.getFinalMultiOutput();
                    helper.assertTrue(
                            finalOutput instanceof IAEItemStack
                                    && ((IAEItemStack) finalOutput).isSameType(itemStack(Blocks.stone, 1))
                                    && finalOutput.getStackSize() == 1,
                            "Reconstructed job should still expect exactly one stone; observed=" + finalOutput);
                    helper.assertTrue(
                            craftingGrid(network.controller).isRequesting(itemStack(Blocks.stone, 1)),
                            "Reconstructed job should remain an active stone request");
                    helper.assertInventoryCount(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone), 2);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                }).thenIdle(1)
                .thenExecuteAtStart(
                        "remove dispatched input and return one stone through the ME interface path",
                        () -> {
                            int removed = helper.extractItem(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone), 2);
                            helper.assertEquals(
                                    2L,
                                    removed,
                                    "Processor chest should return exactly two dispatched cobblestone");
                            helper.assertInventoryCount(ASSEMBLER_LABEL, new ItemStack(Blocks.cobblestone), 0);
                            resultReturned.set(true);
                            IAEItemStack remainder = injectIntoGrid(network.controller, Blocks.stone, 1);
                            helper.assertNull(
                                    remainder,
                                    "Exactly one returned stone should be accepted through the ME network");
                        })
                .thenWaitUntil("wait for reconstructed job to complete exactly once", 160, () -> {
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 0);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.stone, 1);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    helper.assertInventoryEmpty(ASSEMBLER_LABEL);
                    CraftingCPUCluster cluster = (CraftingCPUCluster) cpuStorage.get().getCluster();
                    helper.assertFalse(cluster.isBusy(), "Reconstructed CPU should have no pending task or output");
                    helper.assertNull(
                            cluster.getLastCraftingLink(),
                            "Completed reconstructed CPU should clear its active crafting link");
                    helper.assertTrue(
                            network.blockInterface.getInterfaceDuality().getCraftingLockedReason()
                                    == LockCraftingMode.NONE,
                            "Accepted stone should clear the interface processing-output lock");
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                }).thenExecuteFor(80, () -> {
                    long tick = world.getTotalWorldTime();
                    long networkStone = networkStoredAmount(network.controller, Blocks.stone);
                    helper.assertEquals(
                            1L,
                            networkStone,
                            "Replay guard requires exactly one stored stone; tick=" + tick
                                    + ", observed="
                                    + networkStone);
                    helper.assertEquals(
                            0L,
                            networkStoredAmount(network.controller, Blocks.cobblestone),
                            "Replay guard forbids cobblestone returning to storage; tick=" + tick);
                    helper.assertInventoryEmpty(ASSEMBLER_LABEL);
                    helper.assertTrue(
                            craftingCpuDrops(helper).isEmpty(),
                            "Replay guard forbids item drops; tick=" + tick);
                    helper.assertEquals(
                            1L,
                            craftingGrid(network.controller).getCpus().size(),
                            "Replay guard requires one coherent reconstructed CPU; tick=" + tick);
                    long busyCpus = 0;
                    for (ICraftingCPU cpu : craftingGrid(network.controller).getCpus()) {
                        if (cpu.isBusy()) {
                            busyCpus++;
                        }
                    }
                    helper.assertEquals(0L, busyCpus, "Replay guard forbids a new active job; tick=" + tick);
                    helper.assertNull(
                            ((CraftingCPUCluster) cpuStorage.get().getCluster()).getLastCraftingLink(),
                            "Replay guard requires no active crafting link; tick=" + tick);
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                })
                .thenExecute("stop reconstruction conservation callback", reconstructionConservesScenarioItems::remove)
                .thenExecute("assert final processing conservation", () -> {
                    helper.assertEquals(
                            0L,
                            networkStoredAmount(network.controller, Blocks.cobblestone),
                            "Cobblestone conservation failed: 2 - 2 = 0");
                    helper.assertEquals(
                            1L,
                            networkStoredAmount(network.controller, Blocks.stone),
                            "Stone conservation failed: 0 + 1 = 1");
                }).thenSucceed();
    }

    // Cancelling a blocked processing job should return CPU-held ingredients without producing output.
    @GameTest(template = "crafting_cpu", timeoutTicks = 380)
    public static void cancelledJobReturnsIngredients(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        helper.startSequence()
                .thenWaitUntil(
                        "wait for cancellation-test crafting network to activate",
                        100,
                        () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        "install blocked cobblestone-to-stone processing pattern",
                        () -> installPattern(
                                network.blockInterface,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        "wait for blocked processing pattern advertisement",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocked processing pattern should be advertised"))
                .thenExecute("submit blocked stone craft and verify active request", () -> {
                    link.set(submitCraft(helper, network.controller, Blocks.stone, 1));
                    IAEStack<?> requestedOutput = itemStack(Blocks.stone, 1);
                    helper.assertTrue(
                            craftingGrid(network.controller).isRequesting(requestedOutput),
                            "Submitted craft should be tracked as an active request");
                }).thenExecute("cancel blocked crafting link", () -> {
                    link.get().cancel();
                    helper.assertTrue(link.get().isCanceled(), "Crafting link should be canceled");
                }).thenWaitUntil("wait for canceled job to return cobblestone without producing stone", 120, () -> {
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, network.controller, Blocks.stone, 0);
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                }).thenSucceed();
    }

    // Breaking a CPU tile during a job should stop the craft without duplicating ingredients or outputs.
    @GameTest(template = "crafting_cpu", timeoutTicks = 380)
    public static void cpuBreakCancelsWithoutDuplication(GameTestHelper helper) {
        CraftingNetwork network = getCraftingNetwork(helper);
        AtomicReference<ICraftingLink> link = new AtomicReference<>();
        Set<EntityItem> cpuBreakDrops = new HashSet<>();
        helper.afterTest(() -> {
            cpuBreakDrops.addAll(craftingCpuDrops(helper));
            cpuBreakDrops.forEach(EntityItem::setDead);
        });
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        TickCallbackHandle cpuBreakDoesNotDuplicateOrProduceOutput = helper
                .onEachTickDisabled("CPU break does not duplicate ingredients or produce output", () -> {
                    cpuBreakDrops.addAll(craftingCpuDrops(helper));
                    long accountedCobblestone = networkStoredAmount(network.controller, Blocks.cobblestone)
                            + droppedItemAmount(cpuBreakDrops, Blocks.cobblestone);
                    long accountedStone = networkStoredAmount(network.controller, Blocks.stone)
                            + droppedItemAmount(cpuBreakDrops, Blocks.stone);
                    helper.assertTrue(
                            accountedCobblestone <= 1,
                            "At most one ingredient may exist while CPU break cancellation settles; observed="
                                    + accountedCobblestone);
                    helper.assertEquals(0L, accountedStone, "CPU break must never produce the requested output");
                });

        helper.startSequence()
                .thenWaitUntil(
                        "wait for CPU-break crafting network to activate",
                        100,
                        () -> assertCraftingNetworkActive(helper, network))
                .thenExecute(
                        "install blocked cobblestone-to-stone processing pattern",
                        () -> installPattern(
                                network.blockInterface,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        "wait for CPU-break processing pattern advertisement",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocked processing pattern should be advertised"))
                .thenExecute(
                        "submit blocked stone craft",
                        () -> link.set(submitCraft(helper, network.controller, Blocks.stone, 1)))
                .thenWaitUntil(
                        "wait for CPU to take the cobblestone ingredient",
                        100,
                        () -> assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 0))
                .thenExecute("break CPU unit during active processing job", () -> {
                    destroyBlock(helper, CPU_UNIT_LABEL);
                    cpuBreakDrops.addAll(craftingCpuDrops(helper));
                    cpuBreakDoesNotDuplicateOrProduceOutput.enable();
                }).thenWaitUntil("wait for CPU-break cancellation and ingredient recovery", 40, () -> {
                    cpuBreakDrops.addAll(craftingCpuDrops(helper));
                    long accountedCobblestone = networkStoredAmount(network.controller, Blocks.cobblestone)
                            + droppedItemAmount(cpuBreakDrops, Blocks.cobblestone);
                    long accountedStone = networkStoredAmount(network.controller, Blocks.stone)
                            + droppedItemAmount(cpuBreakDrops, Blocks.stone);

                    helper.assertTrue(link.get().isCanceled(), "Crafting link should be canceled when the CPU breaks");
                    helper.assertEquals(
                            1L,
                            accountedCobblestone,
                            "Ingredient should exist exactly once after CPU break");
                    helper.assertEquals(0L, accountedStone, "CPU break should not produce the requested output");
                    assertNotRequesting(helper, network.controller, Blocks.stone);
                })
                .thenExecute("stop CPU-break conservation invariant", cpuBreakDoesNotDuplicateOrProduceOutput::disable)
                .thenSucceed();
    }

    private static CraftingNetwork getCraftingNetwork(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
        TileDrive drive = helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
        TileCraftingStorageTile cpuStorage = helper
                .assertTileEntityPresent(TileCraftingStorageTile.class, CPU_STORAGE_LABEL);
        TileCraftingTile cpuUnit = helper.assertTileEntityPresent(TileCraftingTile.class, CPU_UNIT_LABEL);
        TileInterface blockInterface = helper.assertTileEntityPresent(TileInterface.class, INTERFACE_LABEL);
        TileMolecularAssembler assembler = helper
                .assertTileEntityPresent(TileMolecularAssembler.class, ASSEMBLER_LABEL);

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

    private static void placeProcessingTarget(GameTestHelper helper) {
        helper.setBlock(ASSEMBLER_LABEL, Blocks.chest);
        helper.assertTileEntityPresent(ASSEMBLER_LABEL);
    }

    private static void installPattern(TileInterface blockInterface, ItemStack encodedPattern) {
        InventoryHelper.setSlot(blockInterface.getInterfaceDuality().getPatterns(), 0, encodedPattern);
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

    private static ItemStack encodedScopedCraftingPattern() {
        ItemStack encodedPattern = encodedPattern();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", true);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(TEST_RECIPE_CORNER, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_EDGE, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_CORNER, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_EDGE, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_CENTER, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_EDGE, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_CORNER, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_EDGE, 1));
        inputs.appendTag(itemTag(TEST_RECIPE_CORNER, 1));
        outputs.appendTag(itemTag(TEST_RECIPE_OUTPUT, TEST_RECIPE_OUTPUT_AMOUNT));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static void registerScopedCraftingRecipe(GameTestHelper helper) {
        ItemStack[] inputs = { new ItemStack(TEST_RECIPE_CORNER), new ItemStack(TEST_RECIPE_EDGE),
                new ItemStack(TEST_RECIPE_CORNER), new ItemStack(TEST_RECIPE_EDGE), new ItemStack(TEST_RECIPE_CENTER),
                new ItemStack(TEST_RECIPE_EDGE), new ItemStack(TEST_RECIPE_CORNER), new ItemStack(TEST_RECIPE_EDGE),
                new ItemStack(TEST_RECIPE_CORNER) };
        IRecipe recipe = new ShapedRecipes(3, 3, inputs, new ItemStack(TEST_RECIPE_OUTPUT, TEST_RECIPE_OUTPUT_AMOUNT));
        ScopedCraftingRecipe scopedRecipe = new ScopedCraftingRecipe(
                CraftingManager.getInstance().getRecipeList(),
                recipe);

        helper.afterTest(scopedRecipe::remove);
        scopedRecipe.register();
    }

    private static void configureStickyCell(GameTestHelper helper, ItemStack cell, Block partition) {
        helper.assertTrue(cell.getItem() instanceof ICellWorkbenchItem, "Item cell should expose workbench settings");
        ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cell.getItem();
        IInventory upgrades = cellItem.getUpgradesInventory(cell);
        helper.assertNotNull(upgrades, "Item cell upgrade inventory should exist");
        InventoryHelper
                .setSlot(upgrades, 0, AEApi.instance().definitions().materials().cardSticky().maybeStack(1).get());

        IAEStackInventory config = cellItem.getConfigAEInventory(cell);
        helper.assertNotNull(config, "Item cell config inventory should exist");
        config.putAEStackInSlot(0, itemStack(partition, 1));
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

    private static void destroyBlock(GameTestHelper helper, String label) {
        helper.destroyBlock(label);
    }

    private static <T extends TileCraftingTile> T reconstructCraftingTile(GameTestHelper helper,
            TileCraftingTile previous, NBTTagCompound state, T replacement) {
        WorldServer world = (WorldServer) previous.getWorldObj();
        int x = previous.xCoord;
        int y = previous.yCoord;
        int z = previous.zCoord;
        replacement.readFromNBT((NBTTagCompound) state.copy());
        world.setTileEntity(x, y, z, replacement);
        TileEntity installed = world.getTileEntity(x, y, z);
        helper.assertTrue(
                installed == replacement,
                "Fresh " + replacement.getClass().getSimpleName() + " should attach at unchanged CPU block");
        return replacement;
    }

    private static List<EntityItem> craftingCpuDrops(GameTestHelper helper) {
        TestPos storage = helper.pos(CPU_STORAGE_LABEL);
        TestPos unit = helper.pos(CPU_UNIT_LABEL);
        TestPos min = new TestPos(
                Math.min(storage.x(), unit.x()) - 1,
                Math.min(storage.y(), unit.y()) - 1,
                Math.min(storage.z(), unit.z()) - 1);
        TestPos max = new TestPos(
                Math.max(storage.x(), unit.x()) + 1,
                Math.max(storage.y(), unit.y()) + 1,
                Math.max(storage.z(), unit.z()) + 1);

        // CraftingCPUCluster may spill inventory at the broken tile or any free block adjacent to another CPU tile.
        return helper.getEntities(EntityItem.class, min, max);
    }

    private static long droppedItemAmount(Collection<EntityItem> drops, Block block) {
        ItemStack expected = new ItemStack(block, 1);
        long amount = 0;

        for (EntityItem drop : drops) {
            ItemStack stack = drop.getEntityItem();
            if (!drop.isDead && stack != null && stack.isItemEqual(expected)) {
                amount += stack.stackSize;
            }
        }

        return amount;
    }

    @Desugar
    private record CraftingNetwork(TileController controller, TileDrive drive, TileCraftingStorageTile cpuStorage,
            TileCraftingTile cpuUnit, TileInterface blockInterface, TileMolecularAssembler assembler) {

    }

    private static final class ScopedCraftingRecipe {

        private final List<IRecipe> recipes;
        private final IRecipe recipe;
        private boolean registered;

        private ScopedCraftingRecipe(List<IRecipe> recipes, IRecipe recipe) {
            this.recipes = recipes;
            this.recipe = recipe;
        }

        private void register() {
            this.recipes.add(0, this.recipe);
            this.registered = true;
        }

        private void remove() {
            if (!this.registered) {
                return;
            }

            for (int index = 0; index < this.recipes.size(); index++) {
                if (this.recipes.get(index) == this.recipe) {
                    this.recipes.remove(index);
                    this.registered = false;
                    return;
                }
            }

            throw new AssertionError("Scoped crafting recipe should still be registered during test teardown");
        }
    }
}
