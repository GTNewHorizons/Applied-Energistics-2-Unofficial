package appeng.gametests.storage.drive;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertItemRemainder;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.extractFromGrid;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemInventory;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.simulateInjectIntoGrid;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AppEng;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class DriveAndCellTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String ME_CHEST_LABEL = "me_chest";
    private static final long CELL_1K_ONE_TYPE_CAPACITY = 8128;

    // A prefilled cell in a drive should be visible through the storage grid.
    @GameTest(template = "drive_cells", timeoutTicks = 80)
    public static void driveExposesInsertedCellContents(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        ItemStack cell = cell1k();
        insertItems(helper, cell, Blocks.cobblestone, 100);

        helper.startSequence().thenWaitUntil("wait for drive network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
        }).thenExecute("insert prefilled cell into drive", () -> helper.setSlot(DRIVE_LABEL, 0, cell))
                .thenWaitUntil(
                        "wait for prefilled cell contents to become network-visible",
                        20,
                        () -> assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 100))
                .thenSucceed();
    }

    // A partitioned cell should accept only stacks matching its configured partition list.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void partitionedCellRejectsUnconfiguredItem(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        ItemStack cell = cell1k();
        partitionCell(helper, cell, Blocks.cobblestone);

        helper.startSequence().thenWaitUntil("wait for drive network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
        }).thenExecute("insert partitioned cell into drive", () -> helper.setSlot(DRIVE_LABEL, 0, cell))
                .thenWaitUntil("wait for partition rules to become network-visible", 20, () -> {
                    helper.assertNull(
                            simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                            "Configured cobblestone should be accepted by the partitioned cell");
                    assertItemRemainder(helper, simulateInjectIntoGrid(controller, Blocks.dirt, 64), Blocks.dirt, 64);
                }).thenExecute("inject configured and rejected stacks", () -> {
                    IAEItemStack acceptedRemainder = injectIntoGrid(controller, Blocks.cobblestone, 64);
                    IAEItemStack rejectedRemainder = injectIntoGrid(controller, Blocks.dirt, 64);

                    helper.assertNull(acceptedRemainder, "Configured stack should enter the partitioned cell");
                    assertItemRemainder(helper, rejectedRemainder, Blocks.dirt, 64);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 64);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.dirt, 0);
                }).thenSucceed();
    }

    // New items should route to the higher-priority ME chest before the lower-priority drive.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void higherPriorityCellReceivesNewItemsFirst(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack highPriorityCell = cell1k();
        ItemStack lowPriorityCell = cell1k();

        helper.startSequence().thenWaitUntil("wait for prioritized storage network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("configure priorities and insert storage cells", () -> {
            meChest.setPriority(100);
            drive.setPriority(0);
            helper.setSlot(ME_CHEST_LABEL, 1, highPriorityCell);
            helper.setSlot(DRIVE_LABEL, 0, lowPriorityCell);
        }).thenWaitUntil(
                "wait for prioritized storage to accept the test stack",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.cobblestone, 128),
                        "Prioritized storage should accept 128 cobblestone"))
                .thenExecute("inject cobblestone and validate high-priority routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 128);

                    helper.assertNull(remainder, "Injected items should fit into available network storage");
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 128);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 128);
                }).thenSucceed();
    }

    // Once high-priority storage is full, overflow should route to the lower-priority drive cell.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void fullHighPriorityFallsBackToLowerPriority(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack highPriorityCell = cell1k();
        ItemStack lowPriorityCell = cell1k();
        insertItems(helper, highPriorityCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);

        helper.startSequence().thenWaitUntil("wait for prioritized storage network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("configure priorities and insert full and empty cells", () -> {
            meChest.setPriority(100);
            drive.setPriority(0);
            helper.setSlot(ME_CHEST_LABEL, 1, highPriorityCell);
            helper.setSlot(DRIVE_LABEL, 0, lowPriorityCell);
        }).thenWaitUntil("wait for the full high-priority cell to become visible", 20, () -> {
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
            helper.assertNull(
                    simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                    "Lower-priority drive cell should be ready to accept overflow");
        }).thenExecute("inject overflow and validate fallback routing", () -> {
            IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

            helper.assertNull(remainder, "Overflow should fit into lower-priority storage");
            assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 64);
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY + 64);
        }).thenSucceed();
    }

    // Sticky storage should claim matching items before non-sticky storage, regardless of numeric priority.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void stickyCellReceivesMatchingItemsBeforeHigherPriorityCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack highPriorityCell = cell1k();
        insertItems(helper, stickyCell, Blocks.cobblestone, 1);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for sticky priority network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert sticky and higher-priority non-sticky cells", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, highPriorityCell);
        }).thenWaitUntil(
                "wait for sticky storage routing to become available",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                        "Sticky cell should accept matching cobblestone"))
                .thenExecute("inject matching item and validate sticky routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Matching items should fit into the sticky cell");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 65);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 65);
                }).thenSucceed();
    }

    // A partitioned sticky cell should claim its configured item even when it does not already contain that item.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void partitionedStickyCellReceivesConfiguredItemBeforeHigherPriorityCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack highPriorityCell = cell1k();
        partitionCell(helper, stickyCell, Blocks.cobblestone);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for partitioned sticky network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert partitioned sticky and higher-priority non-sticky cells", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, highPriorityCell);
        }).thenWaitUntil(
                "wait for partitioned sticky storage routing to become available",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                        "Partitioned sticky cell should accept configured cobblestone"))
                .thenExecute("inject configured item and validate partitioned sticky routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Configured items should fit into the partitioned sticky cell");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 64);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 64);
                }).thenSucceed();
    }

    // Sticky storage should not claim item types that are neither partitioned nor already stored there.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void stickyCellLetsUnrelatedItemsFallBackToNormalCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack normalCell = cell1k();
        insertItems(helper, stickyCell, Blocks.cobblestone, 1);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for sticky fallback network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert sticky and normal cells", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, normalCell);
        }).thenWaitUntil(
                "wait for non-sticky fallback routing to become available",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.dirt, 64),
                        "Normal cell should accept an item unrelated to the sticky cell"))
                .thenExecute("inject unrelated item and validate normal fallback", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.dirt, 64);

                    helper.assertNull(remainder, "Unrelated items should fit into normal storage");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.dirt, 0);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.dirt, 64);
                    assertNetworkStoredAmount(helper, controller, Blocks.dirt, 64);
                }).thenSucceed();
    }

    // Once sticky storage accepts part of a matching stack, its remainder must not spill into non-sticky storage.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void stickyCellPreventsMatchingOverflowFromFallingBack(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack normalCell = cell1k();
        insertItems(helper, stickyCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY - 1);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for sticky overflow network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert nearly full sticky cell and empty normal cell", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, normalCell);
        }).thenWaitUntil("wait for sticky overflow routing to become available", 20, () -> {
            IAEItemStack remainder = simulateInjectIntoGrid(controller, Blocks.cobblestone, 64);
            assertItemRemainder(helper, remainder, Blocks.cobblestone, 63);
        }).thenExecute("inject matching overflow and validate non-sticky storage is skipped", () -> {
            IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

            assertItemRemainder(helper, remainder, Blocks.cobblestone, 63);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
            assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
        }).thenSucceed();
    }

    // A completely full sticky cell should still reserve its matching item type and prevent spill to normal storage.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void fullStickyCellPreventsMatchingItemsFromFallingBack(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack normalCell = cell1k();
        insertItems(helper, stickyCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for full sticky network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert full sticky cell and empty normal cell", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, normalCell);
        }).thenWaitUntil("wait for full sticky routing to become available", 20, () -> {
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
            helper.assertNull(
                    simulateInjectIntoGrid(controller, Blocks.dirt, 1),
                    "Normal cell should be available to prove matching items remain reserved by sticky storage");
            assertItemRemainder(
                    helper,
                    simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                    Blocks.cobblestone,
                    64);
        }).thenExecute("inject matching item and validate full sticky reservation", () -> {
            IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

            assertItemRemainder(helper, remainder, Blocks.cobblestone, 64);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
            assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
        }).thenSucceed();
    }

    // All matching sticky cells should be considered before insertion can fall through to normal storage.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void matchingStickyCellsAreFilledBeforeNormalCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack firstStickyCell = cell1k();
        ItemStack secondStickyCell = cell1k();
        ItemStack normalCell = cell1k();
        insertItems(helper, firstStickyCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY - 1);
        insertItems(helper, secondStickyCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY - 1);
        installStickyCard(helper, firstStickyCell);
        installStickyCard(helper, secondStickyCell);

        helper.startSequence().thenWaitUntil("wait for multiple sticky cells network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert two nearly full sticky cells and an empty normal cell", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, firstStickyCell);
            helper.setSlot(DRIVE_LABEL, 1, secondStickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, normalCell);
        }).thenWaitUntil(
                "wait for both sticky cells to accept the matching stack",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.cobblestone, 2),
                        "Two matching sticky cells should accept one item each"))
                .thenExecute("inject matching items and validate all sticky cells are used", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 2);

                    helper.assertNull(remainder, "Both matching items should fit into sticky storage");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
                    assertStoredAmount(helper, drive.getStackInSlot(1), Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY * 2);
                }).thenSucceed();
    }

    // Numeric priority should still determine insertion order among multiple matching sticky inventories.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void higherPriorityStickyCellReceivesMatchingItemsFirst(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack lowPriorityStickyCell = cell1k();
        ItemStack highPriorityStickyCell = cell1k();
        insertItems(helper, lowPriorityStickyCell, Blocks.cobblestone, 1);
        insertItems(helper, highPriorityStickyCell, Blocks.cobblestone, 1);
        installStickyCard(helper, lowPriorityStickyCell);
        installStickyCard(helper, highPriorityStickyCell);

        helper.startSequence().thenWaitUntil("wait for sticky priority ordering network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert lower- and higher-priority sticky cells", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, lowPriorityStickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, highPriorityStickyCell);
        }).thenWaitUntil(
                "wait for sticky priority ordering to become available",
                20,
                () -> helper.assertNull(
                        simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                        "Matching items should fit into sticky storage"))
                .thenExecute("inject matching item and validate sticky priority ordering", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Matching items should fit into high-priority sticky storage");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 65);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 66);
                }).thenSucceed();
    }

    // Extraction should drain normal storage before sticky storage so sticky contents remain available for routing.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void extractionDrainsNormalCellBeforeStickyCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileChest meChest = getMEChest(helper);
        ItemStack stickyCell = cell1k();
        ItemStack normalCell = cell1k();
        insertItems(helper, stickyCell, Blocks.cobblestone, 64);
        insertItems(helper, normalCell, Blocks.cobblestone, 64);
        installStickyCard(helper, stickyCell);

        helper.startSequence().thenWaitUntil("wait for sticky extraction network activation", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute("insert sticky and normal cells containing the same item", () -> {
            drive.setPriority(0);
            meChest.setPriority(100);
            helper.setSlot(DRIVE_LABEL, 0, stickyCell);
            helper.setSlot(ME_CHEST_LABEL, 1, normalCell);
        }).thenWaitUntil(
                "wait for both cells to become network-visible",
                20,
                () -> assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 128))
                .thenExecute("extract cobblestone and validate normal storage is drained first", () -> {
                    IAEItemStack extracted = extractFromGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNotNull(extracted, "Cobblestone should be extractable from network storage");
                    helper.assertEquals(
                            64L,
                            extracted.getStackSize(),
                            "Requested cobblestone amount should be extracted");
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 64);
                    assertStoredAmount(helper, meChest.getStackInSlot(1), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 64);
                }).thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
    }

    private static TileChest getMEChest(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileChest.class, ME_CHEST_LABEL);
    }

    private static void installStickyCard(GameTestHelper helper, ItemStack cell) {
        helper.assertTrue(cell.getItem() instanceof ICellWorkbenchItem, "Item cell should expose upgrade slots");
        IInventory upgrades = ((ICellWorkbenchItem) cell.getItem()).getUpgradesInventory(cell);
        helper.assertNotNull(upgrades, "Item cell upgrade inventory should exist");
        ItemStack stickyCard = AEApi.instance().definitions().materials().cardSticky().maybeStack(1).get();
        upgrades.setInventorySlotContents(0, stickyCard);
        upgrades.markDirty();
    }

    @SuppressWarnings("unchecked")
    private static void partitionCell(GameTestHelper helper, ItemStack cell, Block block) {
        IMEInventoryHandler<IAEItemStack> handler = itemInventory(helper, cell);
        helper.assertTrue(handler instanceof ICellInventoryHandler, "Item cell should expose a configurable inventory");
        ICellInventoryHandler<IAEItemStack> cellHandler = (ICellInventoryHandler<IAEItemStack>) handler;
        ICellInventory<IAEItemStack> cellInventory = cellHandler.getCellInv();
        helper.assertNotNull(cellInventory, "Item cell inventory should expose cell details");

        IAEStackInventory config = cellInventory.getConfigAEInventory();
        helper.assertNotNull(config, "Item cell config inventory should exist");
        config.putAEStackInSlot(0, itemStack(block, 1));
    }
}
