package appeng.gametests.storage.reshuffle;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

import appeng.api.config.ReshufflePhase;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AppEng;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ReshuffleTask;
import appeng.helpers.ScanTask;
import appeng.me.GridAccessException;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;
import appeng.util.item.IAEStackList;

@GameTestHolder(AppEng.MOD_ID)
public final class StorageReshuffleTests {

    private static final String TEMPLATE = "storage_reshuffler";
    private static final String SOURCE_DRIVE = "source_drive";
    private static final String TARGET_DRIVE = "target_drive";
    private static final String CONTROLLER = "controller";
    private static final String RESHUFFLER = "reshuffler";
    private static final String ME_CHEST = "me_chest";
    private static final int TARGET_FILLER_COUNT = 8032;

    private StorageReshuffleTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    @MethodSource("insertOrders")
    public static void insertOrderControlsConstrainedCellPacking(GameTestHelper helper, boolean largeStacksFirst) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        ItemStack targetCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);
        insertItems(helper, sourceCell, Blocks.dirt, 32);
        insertItems(helper, sourceCell, Blocks.gravel, 16);
        insertItems(helper, targetCell, Blocks.stone, TARGET_FILLER_COUNT);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for reshuffler network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install source and target cells", () -> {
                    fixture.sourceDrive.setPriority(0);
                    fixture.meChest.setPriority(100);
                    helper.setSlot(SOURCE_DRIVE, 0, sourceCell);
                    helper.setSlot(ME_CHEST, 1, targetCell);
                }).thenWaitUntil("wait for source contents", 20, () -> {
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.dirt, 32);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.gravel, 16);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.stone, TARGET_FILLER_COUNT);
                }).thenExecute("start reshuffle", () -> {
                    fixture.reshuffler.getConfigManager()
                            .putSetting(Settings.INSERT_ORDER, largeStacksFirst ? YesNo.YES : YesNo.NO);
                    fixture.reshuffler.startReshuffle();
                }).thenWaitUntil("wait for reshuffle completion", 80, () -> { assertDone(helper, fixture.reshuffler); })
                .thenWaitUntil("wait for unlocked network contents", 20, () -> {
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.dirt, 32);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.gravel, 16);
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.stone, TARGET_FILLER_COUNT);
                }).thenExecute("verify cell contents and report", () -> {
                    ItemStack installedTargetCell = fixture.meChest.getStackInSlot(1);
                    assertStoredAmount(
                            helper,
                            installedTargetCell,
                            Blocks.stone,
                            largeStacksFirst ? TARGET_FILLER_COUNT : 7824);
                    assertStoredAmount(helper, installedTargetCell, Blocks.cobblestone, largeStacksFirst ? 32 : 64);
                    assertStoredAmount(helper, installedTargetCell, Blocks.dirt, largeStacksFirst ? 0 : 32);
                    assertStoredAmount(helper, installedTargetCell, Blocks.gravel, largeStacksFirst ? 0 : 16);

                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertEquals(8144.0, report.extractedItems, "Every item should be extracted once");
                    helper.assertEquals(8144.0, report.injectedItems, "Every item should be injected once");
                    helper.assertTrue(report.cantExtract.isEmpty(), "No items should fail extraction");
                    helper.assertTrue(report.cantInject.isEmpty(), "No items should fail injection");
                    helper.assertTrue(report.lostItems.isEmpty(), "Report should not contain lost items");
                    helper.assertTrue(report.gainedItems.isEmpty(), "Report should not contain gained items");
                }).thenSucceed();
    }

    public static List<GameTestArguments> insertOrders() {
        return Arrays.asList(
                GameTestArguments.named("small-stacks-first", false),
                GameTestArguments.named("large-stacks-first", true));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void emptyNetworkCompletes(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for empty reshuffler network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("start empty reshuffle", fixture.reshuffler::startReshuffle)
                .thenWaitUntil("wait for empty reshuffle completion", 40, () -> assertDone(helper, fixture.reshuffler))
                .thenExecute("verify empty report", () -> {
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertEquals(0.0, report.extractedItems, "Empty network should extract nothing");
                    helper.assertEquals(0.0, report.injectedItems, "Empty network should inject nothing");
                    helper.assertTrue(report.lostItems.isEmpty(), "Empty network should report no losses");
                    helper.assertTrue(report.gainedItems.isEmpty(), "Empty network should report no gains");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void scanFindsInstalledCellsAndTheirContents(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        ItemStack targetCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);

        helper.startSequence()
                .thenWaitUntil("wait for scan network activation", 40, () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install cells", () -> {
                    helper.setSlot(SOURCE_DRIVE, 0, sourceCell);
                    helper.setSlot(TARGET_DRIVE, 0, targetCell);
                })
                .thenWaitUntil(
                        "wait for scanned contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("scan network", fixture.reshuffler::scanNetwork).thenExecute("verify scan", () -> {
                    ScanTask scan = fixture.reshuffler.getScan();
                    helper.assertNotNull(scan, "Active reshuffler should produce a scan");
                    helper.assertEquals(2, scan.getScanCellsData().size(), "Scan should find both installed cells");
                    long scannedCobblestone = scan.getScanCellsData().stream()
                            .flatMap(record -> record.topStoredItems.stream())
                            .filter(stack -> stack.isSameType(itemStack(Blocks.cobblestone, 1)))
                            .mapToLong(IAEStack::getStackSize).sum();
                    helper.assertEquals(64L, scannedCobblestone, "Scan should report the source cell contents");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void disabledItemFilterLeavesItemsUntouched(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for filtered reshuffler network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install source and disable all stack types", () -> {
                    helper.setSlot(SOURCE_DRIVE, 0, sourceCell);
                    fixture.reshuffler.getTypeFilters().setAllEnabled(false);
                    fixture.reshuffler.onChangeTypeFilters();
                })
                .thenWaitUntil(
                        "wait for filtered source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("start filtered reshuffle", fixture.reshuffler::startReshuffle)
                .thenWaitUntil(
                        "wait for filtered reshuffle completion",
                        40,
                        () -> assertDone(helper, fixture.reshuffler))
                .thenExecute("verify filtered contents", () -> {
                    assertStoredAmount(helper, fixture.sourceDrive.getStackInSlot(0), Blocks.cobblestone, 64);
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertEquals(0.0, report.extractedItems, "Disabled item type should not be extracted");
                    helper.assertEquals(0.0, report.injectedItems, "Disabled item type should not be injected");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void missingDestinationRestoresItemsToTheirSource(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);
        ReshuffleTask[] task = new ReshuffleTask[1];
        ItemStack[] removedSourceCell = new ItemStack[1];
        IItemList<IAEStack<?>> cantInject = new IAEStackList();

        helper.startSequence()
                .thenWaitUntil(
                        "wait for rollback network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install rollback source", () -> { helper.setSlot(ME_CHEST, 1, sourceCell); })
                .thenWaitUntil(
                        "wait for rollback source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("extract then remove the destination", () -> {
                    task[0] = createTask(fixture.controller, cantInject, new AEStackTypeFilter(), false);
                    advanceToPhase(helper, task[0], ReshufflePhase.INJECTION);
                    removedSourceCell[0] = fixture.meChest.getStackInSlot(1);
                    helper.clearSlot(ME_CHEST, 1);
                    finishTask(helper, task[0]);
                }).thenExecute("verify source rollback", () -> {
                    assertStoredAmount(helper, removedSourceCell[0], Blocks.cobblestone, 64);
                    helper.assertTrue(cantInject.isEmpty(), "Direct rollback should not leave pending items");
                    helper.assertEquals(ReshufflePhase.DONE, task[0].getReport().phase, "Task should finish cleanly");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void cancellingDuringExtractionRestoresItems(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for cancellation network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install cancellation source", () -> { helper.setSlot(SOURCE_DRIVE, 0, sourceCell); })
                .thenWaitUntil(
                        "wait for cancellation source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("start cancellable reshuffle", fixture.reshuffler::startReshuffle)
                .thenWaitUntil("wait for extraction to begin", 40, () -> {
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertNotNull(report, "Running reshuffler should produce a report");
                    helper.assertTrue(report.extractedItems > 0, "Reshuffler should extract before cancellation");
                }).thenExecute("cancel during extraction", fixture.reshuffler::cancelReshuffle)
                .thenExecute("verify cancellation rollback", () -> {
                    assertStoredAmount(helper, fixture.sourceDrive.getStackInSlot(0), Blocks.cobblestone, 64);
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertNotNull(report, "Cancellation should produce a report");
                    helper.assertEquals(
                            ReshufflePhase.CANCEL,
                            report.phase,
                            "Cancelled task should report cancellation");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pendingItemsSerializeExactlyOnce(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);
        ReshuffleTask[] task = new ReshuffleTask[1];

        helper.startSequence()
                .thenWaitUntil(
                        "wait for persistence network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install persistence source", () -> { helper.setSlot(SOURCE_DRIVE, 0, sourceCell); })
                .thenWaitUntil(
                        "wait for persistence source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("serialize pending extraction", () -> {
                    task[0] = createTask(fixture.controller, new IAEStackList(), new AEStackTypeFilter(), false);
                    advanceToPhase(helper, task[0], ReshufflePhase.INJECTION);

                    NBTTagCompound tag = new NBTTagCompound();
                    task[0].nbt(tag);
                    IItemList<IAEStack<?>> restored = new IAEStackList();
                    ReshuffleTask.nbtLoad(tag, restored);
                    IAEStack<?> pending = restored.findPrecise(itemStack(Blocks.cobblestone, 1));

                    helper.assertNotNull(pending, "Serialized task should contain the pending stack");
                    helper.assertEquals(64L, pending.getStackSize(), "Pending stack should be serialized once");
                    helper.assertEquals(1, restored.size(), "Recovery list should contain one stack type");
                    task[0].cancel();
                })
                .thenExecute(
                        "verify persistence cleanup",
                        () -> {
                            assertStoredAmount(helper, fixture.sourceDrive.getStackInSlot(0), Blocks.cobblestone, 64);
                        })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void errorMovesPendingItemsToRecoveryQueue(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);
        IItemList<IAEStack<?>> cantInject = new IAEStackList();
        ReshuffleTask[] task = new ReshuffleTask[1];

        helper.startSequence()
                .thenWaitUntil("wait for error network activation", 40, () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install error source", () -> { helper.setSlot(SOURCE_DRIVE, 0, sourceCell); })
                .thenWaitUntil(
                        "wait for error source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("fail with an extracted stack pending", () -> {
                    task[0] = createTask(fixture.controller, cantInject, new AEStackTypeFilter(), false);
                    advanceToPhase(helper, task[0], ReshufflePhase.INJECTION);
                    task[0].error();
                }).thenExecute("verify pending stack entered recovery queue", () -> {
                    helper.assertEquals(
                            64L,
                            storedAmount(cantInject, Blocks.cobblestone),
                            "Recovery amount should match");
                    helper.assertEquals(
                            ReshufflePhase.ERROR,
                            task[0].getReport().phase,
                            "Task should report the error");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 560)
    public static void deferredRecoveryRetainsAndRetriesRemainder(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack constrainedCell = cell1k();
        ItemStack overflowCell = cell1k();
        insertItems(helper, constrainedCell, Blocks.cobblestone, TARGET_FILLER_COUNT);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for deferred recovery network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute(
                        "install constrained recovery destination",
                        () -> { helper.setSlot(TARGET_DRIVE, 0, constrainedCell); })
                .thenWaitUntil(
                        "wait for constrained recovery contents",
                        20,
                        () -> assertNetworkStoredAmount(
                                helper,
                                fixture.controller,
                                Blocks.cobblestone,
                                TARGET_FILLER_COUNT))
                .thenExecute("seed deferred recovery queue", () -> {
                    IItemList<IAEStack<?>> pending = new IAEStackList();
                    pending.add(itemStack(Blocks.cobblestone, 128));
                    NBTTagCompound tag = new NBTTagCompound();
                    tag.setTag("cantInject", Platform.writeAEStackListNBT(pending));
                    fixture.reshuffler.readFromNBT_TileStorageReshuffle(tag);
                }).thenWaitUntil("wait for partial deferred recovery", 260, () -> {
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 8128);
                    helper.assertEquals(
                            32L,
                            pendingAmount(fixture.reshuffler, Blocks.cobblestone),
                            "Rejected recovery remainder should persist");
                })
                .thenExecute(
                        "install destination for recovery remainder",
                        () -> { helper.setSlot(SOURCE_DRIVE, 0, overflowCell); })
                .thenWaitUntil("wait for recovery remainder retry", 260, () -> {
                    assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 8160);
                    helper.assertEquals(
                            0L,
                            pendingAmount(fixture.reshuffler, Blocks.cobblestone),
                            "Recovered remainder should leave the pending queue");
                }).thenSucceed();
    }

    private static Fixture placeFixture(GameTestHelper helper) {
        return new Fixture(
                helper.assertTileEntityPresent(TileDrive.class, SOURCE_DRIVE),
                helper.assertTileEntityPresent(TileDrive.class, TARGET_DRIVE),
                helper.assertTileEntityPresent(TileController.class, CONTROLLER),
                helper.assertTileEntityPresent(TileStorageReshuffle.class, RESHUFFLER),
                helper.assertTileEntityPresent(TileChest.class, ME_CHEST));
    }

    private static void assertFixtureActive(GameTestHelper helper, Fixture fixture) {
        assertActive(helper, fixture.sourceDrive.getProxy(), "Source drive should be active");
        assertActive(helper, fixture.targetDrive.getProxy(), "Target drive should be active");
        assertActive(helper, fixture.controller.getProxy(), "Controller should be active");
        assertActive(helper, fixture.reshuffler.getProxy(), "Reshuffler should be active");
        assertActive(helper, fixture.meChest.getProxy(), "ME chest should be active");
    }

    private static ReshuffleTask createTask(TileController controller, IItemList<IAEStack<?>> cantInject,
            AEStackTypeFilter filters, boolean largeStacksFirst) {
        return new ReshuffleTask(
                filters,
                getStorageGrid(controller),
                cantInject,
                new ReshuffleActionSource(controller),
                false,
                largeStacksFirst);
    }

    private static void advanceToPhase(GameTestHelper helper, ReshuffleTask task, ReshufflePhase phase) {
        task.initialize();
        for (int i = 0; i < 100 && task.getReport().phase != phase; i++) {
            task.processNextBatch();
        }
        helper.assertEquals(phase, task.getReport().phase, "Reshuffle should reach " + phase);
    }

    private static void finishTask(GameTestHelper helper, ReshuffleTask task) {
        for (int i = 0; i < 100 && task.isRunning(); i++) {
            task.processNextBatch();
        }
        helper.assertFalse(task.isRunning(), "Reshuffle should finish within the operation budget");
    }

    private static void assertDone(GameTestHelper helper, TileStorageReshuffle reshuffler) {
        ReshuffleReport report = reshuffler.getReshuffleReport();
        helper.assertNotNull(report, "Reshuffler should produce a report");
        helper.assertFalse(reshuffler.isReshuffleRunning(), "Reshuffler should no longer be running");
        helper.assertEquals(ReshufflePhase.DONE, report.phase, "Reshuffler should finish successfully");
    }

    private static IStorageGrid getStorageGrid(TileController controller) {
        try {
            return controller.getProxy().getStorage();
        } catch (GridAccessException e) {
            throw new AssertionError("Network storage should be accessible", e);
        }
    }

    private static long pendingAmount(TileStorageReshuffle reshuffler, Block block) {
        NBTTagCompound tag = new NBTTagCompound();
        reshuffler.writeToNBT_TileStorageReshuffle(tag);
        return storedAmount(Platform.readAEStackListNBT(tag.getTagList("cantInject", NBT.TAG_COMPOUND)), block);
    }

    private static long storedAmount(IItemList<IAEStack<?>> stacks, Block block) {
        IAEStack<?> stack = stacks.findPrecise(itemStack(block, 1));
        return stack == null ? 0 : stack.getStackSize();
    }

    private static final class Fixture {

        private final TileDrive sourceDrive;
        private final TileDrive targetDrive;
        private final TileController controller;
        private final TileStorageReshuffle reshuffler;
        private final TileChest meChest;

        private Fixture(TileDrive sourceDrive, TileDrive targetDrive, TileController controller,
                TileStorageReshuffle reshuffler, TileChest meChest) {
            this.sourceDrive = sourceDrive;
            this.targetDrive = targetDrive;
            this.controller = controller;
            this.reshuffler = reshuffler;
            this.meChest = meChest;
        }
    }
}
