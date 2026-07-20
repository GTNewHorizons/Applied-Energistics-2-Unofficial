package appeng.gametests.storage.drive;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertItemRemainder;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemInventory;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.simulateInjectIntoGrid;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
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

    private static TileController getController(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
    }

    private static TileChest getMEChest(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileChest.class, ME_CHEST_LABEL);
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
