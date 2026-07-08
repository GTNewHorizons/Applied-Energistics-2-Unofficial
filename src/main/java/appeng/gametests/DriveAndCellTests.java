package appeng.gametests;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemInventory;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.tile;

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

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
        }).thenExecute(() -> drive.setInventorySlotContents(0, cell))
                .thenWaitUntil(20, () -> { assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 100); })
                .thenSucceed();
    }

    // A partitioned cell should accept only stacks matching its configured partition list.
    @GameTest(template = "drive_cells", timeoutTicks = 100)
    public static void partitionedCellRejectsUnconfiguredItem(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        ItemStack cell = cell1k();
        partitionCell(helper, cell, Blocks.cobblestone);

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
        }).thenExecute(() -> drive.setInventorySlotContents(0, cell)).thenIdle(5).thenExecute(() -> {
            IAEItemStack acceptedRemainder = injectIntoGrid(controller, Blocks.cobblestone, 64);
            IAEItemStack rejectedRemainder = injectIntoGrid(controller, Blocks.dirt, 64);

            helper.assertNull(acceptedRemainder, "Configured stack should enter the partitioned cell");
            assertRemainder(helper, rejectedRemainder, Blocks.dirt, 64);
            assertStoredAmount(helper, cell, Blocks.cobblestone, 64);
            assertStoredAmount(helper, cell, Blocks.dirt, 0);
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

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute(() -> {
            meChest.setPriority(100);
            drive.setPriority(0);
            meChest.setInventorySlotContents(1, highPriorityCell);
            drive.setInventorySlotContents(0, lowPriorityCell);
        }).thenIdle(5).thenExecute(() -> {
            IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 128);

            helper.assertNull(remainder, "Injected items should fit into available network storage");
            assertStoredAmount(helper, highPriorityCell, Blocks.cobblestone, 128);
            assertStoredAmount(helper, lowPriorityCell, Blocks.cobblestone, 0);
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

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, meChest.getProxy(), "ME chest grid proxy should become active");
        }).thenExecute(() -> {
            meChest.setPriority(100);
            drive.setPriority(0);
            meChest.setInventorySlotContents(1, highPriorityCell);
            drive.setInventorySlotContents(0, lowPriorityCell);
        }).thenWaitUntil(
                20,
                () -> { assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY); })
                .thenExecute(() -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Overflow should fit into lower-priority storage");
                    assertStoredAmount(helper, highPriorityCell, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY);
                    assertStoredAmount(helper, lowPriorityCell, Blocks.cobblestone, 64);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, CELL_1K_ONE_TYPE_CAPACITY + 64);
                }).thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return tile(helper, TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return tile(helper, TileDrive.class, DRIVE_LABEL);
    }

    private static TileChest getMEChest(GameTestHelper helper) {
        return tile(helper, TileChest.class, ME_CHEST_LABEL);
    }

    private static void partitionCell(GameTestHelper helper, ItemStack cell, Block block) {
        IMEInventoryHandler<IAEItemStack> handler = itemInventory(helper, cell);
        helper.assertTrue(handler instanceof ICellInventoryHandler, "Item cell should expose a configurable inventory");
        ICellInventoryHandler<IAEItemStack> cellHandler = (ICellInventoryHandler<IAEItemStack>) handler;
        ICellInventory<IAEItemStack> cellInventory = cellHandler.getCellInv();
        helper.assertNotNull(cellInventory, "Item cell inventory should expose cell details");

        IAEStackInventory config = cellInventory.getConfigAEInventory();
        config.putAEStackInSlot(0, itemStack(block, 1));
    }

    private static void assertRemainder(GameTestHelper helper, IAEItemStack remainder, Block block,
            long expectedAmount) {
        helper.assertNotNull(remainder, "Rejected stack should be returned as a remainder");
        helper.assertTrue(remainder.isSameType(new ItemStack(block, 1)), "Rejected remainder item should match");
        helper.assertEquals(expectedAmount, remainder.getStackSize(), "Rejected remainder amount should match");
    }
}
