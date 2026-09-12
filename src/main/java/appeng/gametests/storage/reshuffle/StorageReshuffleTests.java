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
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.Constants.NBT;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestArguments;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.annotation.MethodSource;

import appeng.api.AEApi;
import appeng.api.config.ReshufflePhase;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AppEng;
import appeng.helpers.ReshuffleReport;
import appeng.helpers.ScanTask;
import appeng.me.GridAccessException;
import appeng.me.cache.NetworkMonitor;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.me.storage.NullInventory;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
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

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void missingDestinationRestoresItemsToTheirSource(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack firstSourceCell = cell1k();
        ItemStack secondSourceCell = cell1k();
        insertItems(helper, firstSourceCell, Blocks.cobblestone, 64);
        insertItems(helper, secondSourceCell, Blocks.cobblestone, 32);
        TileEntityChest[] removedCells = new TileEntityChest[1];

        helper.startSequence()
                .thenWaitUntil(
                        "wait for rollback network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install rollback sources", () -> {
                    helper.setSlot(SOURCE_DRIVE, 0, firstSourceCell);
                    helper.setSlot(ME_CHEST, 1, secondSourceCell);
                })
                .thenWaitUntil(
                        "wait for rollback source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 96))
                .thenExecute("start rollback reshuffle", fixture.reshuffler::startReshuffle)
                .thenWaitUntil("wait for extraction to finish", 40, () -> {
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertNotNull(report, "Running reshuffler should produce a report");
                    helper.assertEquals(ReshufflePhase.INJECTION, report.phase, "Reshuffler should reach injection");
                }).thenExecute("remove destinations while preserving their cells", () -> {
                    ItemStack firstRemovedCell = fixture.sourceDrive.getStackInSlot(0);
                    ItemStack secondRemovedCell = fixture.meChest.getStackInSlot(1);
                    helper.clearSlot(SOURCE_DRIVE, 0);
                    helper.clearSlot(ME_CHEST, 1);
                    helper.destroyBlock(TARGET_DRIVE);
                    helper.setBlock(TARGET_DRIVE, Blocks.chest);
                    removedCells[0] = helper.assertTileEntityPresent(TileEntityChest.class, TARGET_DRIVE);
                    removedCells[0].setInventorySlotContents(0, firstRemovedCell);
                    removedCells[0].setInventorySlotContents(1, secondRemovedCell);
                })
                .thenWaitUntil(
                        "wait for rollback reshuffle completion",
                        40,
                        () -> assertDone(helper, fixture.reshuffler))
                .thenExecute("verify source rollback", () -> {
                    assertStoredAmount(helper, removedCells[0].getStackInSlot(0), Blocks.cobblestone, 64);
                    assertStoredAmount(helper, removedCells[0].getStackInSlot(1), Blocks.cobblestone, 32);
                    helper.assertEquals(
                            0L,
                            pendingAmount(fixture.reshuffler, Blocks.cobblestone),
                            "Direct rollback should not leave pending items");
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

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void pendingItemsSerializeExactlyOnce(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack firstSourceCell = cell1k();
        ItemStack secondSourceCell = cell1k();
        insertItems(helper, firstSourceCell, Blocks.cobblestone, 64);
        insertItems(helper, secondSourceCell, Blocks.cobblestone, 32);
        TileStorageReshuffle[] restored = new TileStorageReshuffle[1];

        helper.startSequence()
                .thenWaitUntil(
                        "wait for persistence network activation",
                        40,
                        () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install persistence sources", () -> {
                    helper.setSlot(SOURCE_DRIVE, 0, firstSourceCell);
                    helper.setSlot(TARGET_DRIVE, 0, secondSourceCell);
                })
                .thenWaitUntil(
                        "wait for persistence source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 96))
                .thenExecute("start persistent reshuffle", fixture.reshuffler::startReshuffle)
                .thenWaitUntil("wait for persistent extraction to finish", 40, () -> {
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertNotNull(report, "Running reshuffler should produce a report");
                    helper.assertEquals(ReshufflePhase.INJECTION, report.phase, "Reshuffler should reach injection");
                }).thenExecute("reload with pending extraction", () -> {
                    NBTTagCompound savedState = new NBTTagCompound();
                    fixture.reshuffler.writeToNBT(savedState);
                    helper.assertEquals(
                            1,
                            savedState.getTagList("injectQueue", NBT.TAG_COMPOUND).tagCount(),
                            "Identical pending stacks should serialize as one stack");

                    helper.destroyBlock(RESHUFFLER);
                    Block reshufflerBlock = AEApi.instance().definitions().blocks().storageReshuffle().maybeBlock()
                            .get();
                    helper.setBlock(RESHUFFLER, reshufflerBlock);
                    restored[0] = helper.assertTileEntityPresent(TileStorageReshuffle.class, RESHUFFLER);
                    restored[0].readFromNBT(savedState);
                }).thenWaitUntil("wait for restored reshuffler activation", 40, () -> {
                    assertActive(helper, restored[0].getProxy(), "Restored reshuffler should be active");
                    helper.assertEquals(
                            96L,
                            pendingAmount(restored[0], Blocks.cobblestone),
                            "Restored recovery queue should contain both sources exactly once");
                }).thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void errorMovesPendingItemsToRecoveryQueue(GameTestHelper helper) {
        Fixture fixture = placeFixture(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 64);

        helper.startSequence()
                .thenWaitUntil("wait for error network activation", 40, () -> { assertFixtureActive(helper, fixture); })
                .thenExecute("install error source", () -> { helper.setSlot(SOURCE_DRIVE, 0, sourceCell); })
                .thenWaitUntil(
                        "wait for error source contents",
                        20,
                        () -> assertNetworkStoredAmount(helper, fixture.controller, Blocks.cobblestone, 64))
                .thenExecute("start reshuffle with a failing destination", () -> {
                    installFailingDestination(helper, fixture.controller);
                    fixture.reshuffler.startReshuffle();
                }).thenWaitUntil("wait for tile-tick failure handling", 40, () -> {
                    ReshuffleReport report = fixture.reshuffler.getReshuffleReport();
                    helper.assertNotNull(report, "Failed reshuffler should produce a report");
                    helper.assertFalse(fixture.reshuffler.isReshuffleRunning(), "Failed reshuffler should stop");
                    helper.assertEquals(ReshufflePhase.ERROR, report.phase, "Task should report the error");
                    helper.assertEquals(
                            64L,
                            pendingAmount(fixture.reshuffler, Blocks.cobblestone),
                            "Pending stack should enter the tile recovery queue");
                    helper.assertFalse(
                            itemNetworkMonitor(fixture.controller).isLocked(),
                            "Failure handling should unlock the item monitor");
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

    @SuppressWarnings("unchecked")
    private static void installFailingDestination(GameTestHelper helper, TileController controller) {
        Object handler = itemNetworkMonitor(controller).getHandler();
        helper.assertTrue(handler instanceof NetworkInventoryHandler<?>, "Item monitor should use the network handler");
        ((NetworkInventoryHandler<IAEItemStack>) handler).addNewStorage(new FailingInventory());
    }

    @SuppressWarnings("unchecked")
    private static NetworkMonitor<IAEItemStack> itemNetworkMonitor(TileController controller) {
        return (NetworkMonitor<IAEItemStack>) getStorageGrid(controller).getItemInventory();
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

    private static final class FailingInventory extends NullInventory<IAEItemStack> {

        private boolean failed;

        @Override
        public boolean canAccept(IAEItemStack input) {
            if (!this.failed && input.isSameType(itemStack(Blocks.cobblestone, 1))) {
                this.failed = true;
                throw new IllegalStateException("Intentional reshuffle injection failure");
            }
            return false;
        }

        @Override
        public int getPriority() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean validForPass(int pass) {
            return pass == 1;
        }
    }

    @Desugar
    private record Fixture(TileDrive sourceDrive, TileDrive targetDrive, TileController controller,
            TileStorageReshuffle reshuffler, TileChest meChest) {

    }
}
