package appeng.gametests.storage.ioport;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.cell4k;
import static appeng.gametests.AEGameTestHelpers.cell64k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemInventory;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.pos;
import static appeng.gametests.AEGameTestHelpers.storedAmount;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
import appeng.gametests.AEGameTestHelpers.Coord;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;

@GameTestHolder(AppEng.MOD_ID)
public class IOPortTests {

    private static final String IO_PORT_LABEL = "io_port";
    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String REDSTONE_LABEL = "redstone";
    private static final String AUTOMATION_LABEL = "automation";

    // Moves an empty cell from an input slot to an output slot.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void emptyCellMove(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack cell = cell1k();

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert empty cell into IO port", () -> {
                    ioport.setInventorySlotContents(0, cell.copy());
                    helper.assertTrue(
                            ItemStack.areItemStacksEqual(ioport.getStackInSlot(0), cell),
                            "Inserted cell should be present in the input slot before the IO port ticks");
                    helper.assertNull(ioport.getStackInSlot(6), "Output slot should be empty before the IO port ticks");
                }).thenExecute("assert empty cell reached output in the same tick", () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Input slot should be empty after one processing tick");
                    ItemStack expectedCell = cell.copy();
                    expectedCell.setTagCompound(new NBTTagCompound());
                    helper.assertTrue(
                            ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), expectedCell),
                            "Empty cell should reach the output slot after one processing tick");
                }).thenSucceed();
    }

    // Allows cell insertion only into input slots and extraction only from output slots.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void sidedInventoryAllowsCellInputAndOutputExtractionOnly(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        ItemStack cell = cell1k();
        ItemStack nonCell = new ItemStack(Items.apple);
        int side = ForgeDirection.UNKNOWN.ordinal();

        helper.assertTrue(ioport.isItemValidForSlot(0, cell), "Storage cells should be valid items");
        helper.assertFalse(ioport.isItemValidForSlot(0, nonCell), "Non-cell items should be invalid");
        helper.assertTrue(ioport.canInsertItem(0, cell, side), "Input slots should accept storage cells");
        helper.assertFalse(ioport.canInsertItem(6, cell, side), "Output slots should not accept storage cells");
        helper.assertFalse(ioport.canInsertItem(0, nonCell, side), "Input slots should not accept non-cell items");
        helper.assertFalse(ioport.canExtractItem(0, cell, side), "Input slots should not allow extraction");
        helper.assertTrue(ioport.canExtractItem(6, cell, side), "Output slots should allow extraction");
        helper.assertEquals(
                12,
                ioport.getAccessibleSlotsBySide(ForgeDirection.UNKNOWN).length,
                "All 12 slots should be exposed");
        helper.succeed();
    }

    // Exports items from a filled cell into ME network storage in EMPTY mode.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void emptyModeExportsCellContentsToNetwork(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenWaitUntil("wait for source cell to empty into the drive and move to output", 10, () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Exported cell should leave the input slot");
                    helper.assertNotNull(ioport.getStackInSlot(6), "Exported cell should move to the output slot");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                }).thenSucceed();
    }

    // Imports items from ME network storage into an empty cell in FILL mode.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void fillModeImportsNetworkContentsIntoCell(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.EMPTY);
        ItemStack targetCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 100);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                }).thenWaitUntil("wait for target cell to receive all network items and move to output", 10, () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Imported cell should leave the input slot");
                    helper.assertNotNull(ioport.getStackInSlot(6), "Imported cell should move to the output slot");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 100);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                }).thenSucceed();
    }

    // Imports only stacks allowed by the target cell partition in FILL mode.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void partitionedTargetCellOnlyImportsMatchingStacks(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.EMPTY);
        ItemStack targetCell = cell1k();
        partitionCell(helper, targetCell, Blocks.cobblestone);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 100);
        insertItems(helper, driveCell, Blocks.dirt, 100);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                }).thenWaitUntil("wait for partitioned import to stop at non-matching stacks", 10, () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "Partitioned cell should leave input once no more matching stacks can be imported");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "Partitioned cell should move to output after importing matching stacks");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 100);
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.dirt, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.dirt, 100);
                }).thenSucceed();
    }

    // Covers PR1197: FILL plus MoveWhenEmpty moves the cell when the remaining network amount exactly matches the
    // transfer budget.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void fillMoveWhenEmptyMovesCellWhenNetworkHasExactlyTransferBudget(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.EMPTY);
        ItemStack targetCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 256);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert target and source cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                }).thenExecute("assert exact-budget import completes in the same tick", () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "Cell should leave input when the network is exactly drained");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "Cell should move to output when the network is exactly drained");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 256);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                }).thenSucceed();
    }

    // Covers PR1259: FILL plus MoveWhenEmpty does not move the cell when the network starts empty.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void fillMoveWhenEmptyDoesNotMoveWhenNetworkStartsEmpty(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.EMPTY);
        ItemStack targetCell = cell1k();
        ItemStack driveCell = cell1k();
        ContinuousInvariant emptyNetworkKeepsCell = continuousInvariant(
                helper,
                "an empty network must not move an unfilled target cell",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Cell should stay in input");
                    helper.assertNull(ioport.getStackInSlot(6), "Cell should not move to output");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                    emptyNetworkKeepsCell.enable();
                }).thenIdle(5).thenExecute("finish empty-network observation window", emptyNetworkKeepsCell::disable)
                .thenSucceed();
    }

    // Covers PR1259: FILL plus MoveWhenEmpty moves the cell when the target cell becomes full.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void fillMoveWhenEmptyMovesWhenTargetCellBecomesFull(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.EMPTY);
        ItemStack targetCell = cell1k();
        ItemStack driveCell = cell64k();
        insertItems(helper, driveCell, Blocks.cobblestone, 9000);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                }).thenWaitUntil("wait for target cell to become full and move to output", 45, () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "Full target cell should leave input in move-when-empty mode");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "Full target cell should move to output even when the network still has items");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 8128);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 872);
                }).thenSucceed();
    }

    // Keeps a cell in input under FullnessMode.EMPTY until the source cell is empty.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void emptyFullnessWaitsUntilSourceCellIsEmpty(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 300);
        ContinuousInvariant nonEmptySourceStaysInInput = continuousInvariant(
                helper,
                "source cell must remain in input while it still contains items",
                () -> {
                    if (storedAmount(helper, sourceCell, Blocks.cobblestone) > 0) {
                        helper.assertNotNull(ioport.getStackInSlot(0), "Non-empty source cell should remain in input");
                        helper.assertNull(ioport.getStackInSlot(6), "Non-empty source cell should not reach output");
                    }
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                    nonEmptySourceStaysInInput.enable();
                }).thenWaitUntil("wait until the source cell is empty", 10, () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Emptied cell should leave the input slot");
                    helper.assertNotNull(ioport.getStackInSlot(6), "Emptied cell should move to the output slot");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 300);
                }).thenExecute("stop source-cell invariant", nonEmptySourceStaysInInput::disable).thenSucceed();
    }

    // Moves a partially transferred cell under FullnessMode.HALF.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void halfFullnessMovesPartiallyTransferredCell(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.EMPTY, FullnessMode.HALF);
        ItemStack sourceCell = cell1k();
        ItemStack nearlyFullDriveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 300);
        insertItems(helper, nearlyFullDriveCell, Blocks.cobblestone, 8028);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, nearlyFullDriveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenWaitUntil("wait for HALF mode to move the partially transferred source cell", 20, () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "HALF mode should remove the partially transferred cell from input");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "HALF mode should move the partially transferred cell to output");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 200);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 8128);
                }).thenSucceed();
    }

    // Moves a FILL-mode target cell only after it becomes full under FullnessMode.FULL.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void fullFullnessMovesOnlyAfterTargetCellIsFull(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        configure(ioport, OperationMode.FILL, FullnessMode.FULL);
        ItemStack targetCell = cell1k();
        ItemStack driveCell = cell64k();
        insertItems(helper, driveCell, Blocks.cobblestone, 9000);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, targetCell);
                }).thenWaitUntil("wait for FULL mode to fill the target cell before moving it", 40, () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Full target cell should leave the input slot");
                    helper.assertNotNull(ioport.getStackInSlot(6), "Full target cell should move to the output slot");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 8128);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 872);
                }).thenSucceed();
    }

    // Moves cells from multiple input slots to output slots without loss or overwrite.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void multipleInputSlotsMoveToOutputSlots(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ContinuousInvariant cellConservation = continuousInvariant(
                helper,
                "six supplied cells must remain accounted for while moving between slots",
                () -> helper.assertEquals(
                        6,
                        countFilledSlots(ioport, 0, 12),
                        "Total IO port cell count should remain six"));

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    for (int slot = 0; slot < 6; slot++) {
                        ioport.setInventorySlotContents(slot, cell1k());
                    }
                    cellConservation.enable();
                }).thenIdle(4).thenExecute("assert five cells moved after five processing ticks", () -> {
                    helper.assertEquals(1, countFilledSlots(ioport, 0, 6), "Input slots should contain 1 cell");
                    helper.assertEquals(5, countFilledSlots(ioport, 6, 12), "Output slots should contain 5 cells");
                }).thenIdle(1).thenExecute("assert all six cells moved after six processing ticks", () -> {
                    for (int slot = 0; slot < 6; slot++) {
                        helper.assertNull(ioport.getStackInSlot(slot), "All input slots should become empty");
                    }
                    helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output slots should contain 6 cells");
                }).thenExecute("stop cell-conservation invariant", cellConservation::disable).thenSucceed();
    }

    // Keeps a cell queued while output is full, then moves it when an output slot opens.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void outputFullKeepsCellQueuedUntilSlotOpens(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack queuedCell = cell4k();
        ContinuousInvariant fullOutputRetainsCell = continuousInvariant(
                helper,
                "a full output inventory must retain the queued cell in input",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Queued cell should remain in input");
                    helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output should remain full");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    for (int slot = 6; slot < 12; slot++) {
                        ioport.setInventorySlotContents(slot, cell1k());
                    }
                    ioport.setInventorySlotContents(0, queuedCell);
                    fullOutputRetainsCell.enable();
                }).thenIdle(5).thenExecute("open an output slot", () -> {
                    fullOutputRetainsCell.disable();
                    ioport.setInventorySlotContents(6, null);
                }).thenIdle(1).thenExecute("assert queued cell entered the opened output slot", () -> {
                    helper.assertNull(ioport.getStackInSlot(0), "Input cell should leave once an output slot opens");
                    helper.assertTrue(
                            ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), queuedCell),
                            "Queued cell should move into the opened output slot");
                }).thenSucceed();
    }

    // Queues an emptied source cell while output is full, then moves it with its contents preserved.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void outputFullQueuesTransferredCellUntilSlotOpens(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell4k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);
        ContinuousInvariant transferredCellRemainsQueued = continuousInvariant(
                helper,
                "a transferred cell must remain queued while output is full",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Transferred cell should remain in input");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    for (int slot = 6; slot < 12; slot++) {
                        ioport.setInventorySlotContents(slot, cell1k());
                    }
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                    transferredCellRemainsQueued.enable();
                }).thenIdle(5).thenExecute("open an output slot", () -> {
                    transferredCellRemainsQueued.disable();
                    ioport.setInventorySlotContents(6, null);
                }).thenIdle(1).thenExecute("assert transferred cell entered the opened output slot", () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "Transferred cell should leave input once output opens");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "Transferred cell should move into the opened output slot");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                }).thenSucceed();
    }

    // Preserves a queued transferred cell through network loss and resumes without duplication or loss.
    @GameTest(template = "ioport", timeoutTicks = 120)
    public static void queuedCellSurvivesPowerLoss(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell4k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);
        ContinuousInvariant inactivePortRetainsQueuedCell = continuousInvariant(
                helper,
                "inactive IO port must retain the queued cell without moving or duplicating contents",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Queued cell should remain in input while inactive");
                    helper.assertNull(ioport.getStackInSlot(6), "Opened output should stay empty while inactive");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    for (int slot = 6; slot < 12; slot++) {
                        ioport.setInventorySlotContents(slot, cell1k());
                    }
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenWaitUntil("wait for transferred cell to queue behind full output", 10, () -> {
                    helper.assertNotNull(
                            ioport.getStackInSlot(0),
                            "Transferred cell should be queued in input while output is full");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                }).thenExecute("remove controller power and channel", () -> removePowerAndChannel(helper))
                .thenWaitUntil(
                        "wait for IO port to lose its channel after controller removal",
                        40,
                        () -> assertInactive(
                                helper,
                                ioport.getProxy(),
                                "IO port should lose its channel when the controller is removed"))
                .thenExecute("open output slot while IO port is inactive", () -> {
                    ioport.setInventorySlotContents(6, null);
                    inactivePortRetainsQueuedCell.enable();
                }).thenIdle(5).thenExecute("restore controller power and channel", () -> {
                    inactivePortRetainsQueuedCell.disable();
                    restorePowerAndChannel(helper);
                }).thenWaitUntil("wait for queued cell processing to resume after controller restoration", 40, () -> {
                    assertIOPortActive(helper, ioport);
                    helper.assertEquals(0, countFilledSlots(ioport, 0, 6), "No input cells should remain after resume");
                    helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output cells should be exactly full");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "Queued cell should move into the reopened output slot after power returns");
                    assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
                }).thenSucceed();
    }

    // Keeps source cell contents unchanged when EMPTY mode cannot export into full network storage.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void emptyModeKeepsSourceCellWhenDestinationStorageIsFull(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack fullDriveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);
        insertItems(helper, fullDriveCell, Blocks.cobblestone, 8128);
        ContinuousInvariant fullDestinationPreservesSource = continuousInvariant(
                helper,
                "full destination storage must preserve the source cell and its contents",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Source cell should remain in input");
                    helper.assertNull(ioport.getStackInSlot(6), "Source cell should not move to output");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 100);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 8128);
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, fullDriveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                    fullDestinationPreservesSource.enable();
                }).thenIdle(5)
                .thenExecute("finish full-destination observation window", fullDestinationPreservesSource::disable)
                .thenSucceed();
    }

    // Transfers 256 item units per tick without upgrades.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noUpgradeTransfersTwoHundredFiftySixItemsPerTick(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 300);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenExecute("assert the first tick transfers exactly 256 items", () -> {
                    helper.assertNotNull(
                            ioport.getStackInSlot(0),
                            "Cell should remain in input after exhausting transfer budget");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 44);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 256);
                }).thenSucceed();
    }

    // Transfers 512 item units per tick with one Speed upgrade.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void speedUpgradeTransfersFiveHundredTwelveItemsPerTick(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        installUpgrade(ioport, AEApi.instance().definitions().materials().cardSpeed().maybeStack(1).get(), 0);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 600);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenExecute("assert the first tick transfers exactly 512 items", () -> {
                    helper.assertNotNull(
                            ioport.getStackInSlot(0),
                            "Cell with remaining contents should stay in input after speed transfer");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 88);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 512);
                }).thenSucceed();
    }

    // Transfers 2048 item units per tick with all three Speed upgrade slots filled.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void maxSpeedUpgradesApplyExpectedBudget(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        installSpeedUpgrades(ioport);
        helper.assertEquals(3, ioport.getInstalledUpgrades(Upgrades.SPEED), "All Speed upgrades should be installed");
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 3000);

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    drive.setInventorySlotContents(0, driveCell);
                    ioport.setInventorySlotContents(0, sourceCell);
                }).thenExecute("assert the first tick transfers exactly 2048 items", () -> {
                    helper.assertNotNull(
                            ioport.getStackInSlot(0),
                            "Cell with remaining contents should stay in input after max-speed transfer");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 952);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 2048);
                }).thenSucceed();
    }

    // Runs in HIGH_SIGNAL mode only after redstone power is applied.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void redstoneHighSignalRequiresPower(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ContinuousInvariant unpoweredHighSignalDoesNotRun = continuousInvariant(
                helper,
                "HIGH_SIGNAL mode must not run without redstone power",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Unpowered cell should remain in input");
                    helper.assertNull(ioport.getStackInSlot(6), "Unpowered cell should not reach output");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    installRedstoneUpgrade(ioport);
                    configureRedstone(ioport, RedstoneMode.HIGH_SIGNAL);
                    ioport.setInventorySlotContents(0, cell1k());
                    unpoweredHighSignalDoesNotRun.enable();
                }).thenIdle(5).thenExecute("apply redstone power", () -> {
                    unpoweredHighSignalDoesNotRun.disable();
                    setRedstoneInput(helper, 15);
                }).thenIdle(1).thenExecute("assert HIGH_SIGNAL operation on the next tick", () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "HIGH_SIGNAL mode should remove the input cell when powered");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "HIGH_SIGNAL mode should move the cell to output when powered");
                }).thenSucceed();
    }

    // Runs in LOW_SIGNAL mode only after redstone power is removed.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void redstoneLowSignalRunsOnlyWithoutPower(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ContinuousInvariant poweredLowSignalDoesNotRun = continuousInvariant(
                helper,
                "LOW_SIGNAL mode must not run while redstone is powered",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Powered cell should remain in input");
                    helper.assertNull(ioport.getStackInSlot(6), "Powered cell should not reach output");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    setRedstoneInput(helper, 15);
                    installRedstoneUpgrade(ioport);
                    configureRedstone(ioport, RedstoneMode.LOW_SIGNAL);
                    ioport.setInventorySlotContents(0, cell1k());
                    poweredLowSignalDoesNotRun.enable();
                }).thenIdle(5).thenExecute("remove redstone power", () -> {
                    poweredLowSignalDoesNotRun.disable();
                    setRedstoneInput(helper, 0);
                }).thenIdle(1).thenExecute("assert LOW_SIGNAL operation on the next tick", () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "LOW_SIGNAL mode should remove the input cell without power");
                    helper.assertNotNull(
                            ioport.getStackInSlot(6),
                            "LOW_SIGNAL mode should move the cell to output without power");
                }).thenSucceed();
    }

    // Processes exactly one cell for a single redstone pulse in SIGNAL_PULSE mode.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void redstonePulseModeRunsAfterPulse(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ContinuousInvariant noPulseDoesNotRun = continuousInvariant(
                helper,
                "SIGNAL_PULSE mode must not run before a pulse",
                () -> {
                    helper.assertEquals(2, countFilledSlots(ioport, 0, 6), "Both cells should remain in input");
                    helper.assertEquals(0, countFilledSlots(ioport, 6, 12), "No cell should reach output");
                });
        ContinuousInvariant onePulseMovesOnlyOneCell = continuousInvariant(
                helper,
                "one signal pulse must not process a second cell",
                () -> {
                    helper.assertEquals(1, countFilledSlots(ioport, 0, 6), "One cell should remain in input");
                    helper.assertEquals(1, countFilledSlots(ioport, 6, 12), "Exactly one cell should be in output");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    installRedstoneUpgrade(ioport);
                    configureRedstone(ioport, RedstoneMode.SIGNAL_PULSE);
                    ioport.setInventorySlotContents(0, cell1k());
                    ioport.setInventorySlotContents(1, cell1k());
                    noPulseDoesNotRun.enable();
                }).thenIdle(5).thenExecute("apply one redstone pulse", () -> {
                    noPulseDoesNotRun.disable();
                    setRedstoneInput(helper, 15);
                }).thenIdle(1).thenExecute("assert one pulse processes one cell on the next tick", () -> {
                    helper.assertEquals(
                            1,
                            countFilledSlots(ioport, 0, 6),
                            "SIGNAL_PULSE mode should leave one input cell after one pulse");
                    helper.assertEquals(
                            1,
                            countFilledSlots(ioport, 6, 12),
                            "SIGNAL_PULSE mode should move exactly one cell after one pulse");
                }).thenExecute("begin post-pulse invariant window", onePulseMovesOnlyOneCell::enable).thenIdle(5)
                .thenExecute("finish post-pulse invariant window", onePulseMovesOnlyOneCell::disable).thenSucceed();
    }

    // A real hopper should reject non-cells, then insert a storage cell for processing.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void sidedAutomationRejectsNonCellsAndInsertsStorageCells(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        ItemStack cell = cell1k();
        ItemStack nonCell = new ItemStack(Items.apple);
        Coord automationPos = pos(helper, AUTOMATION_LABEL);
        ForgeDirection towardIOPort = directionBetween(helper, AUTOMATION_LABEL, IO_PORT_LABEL);
        helper.setBlock(automationPos.x(), automationPos.y(), automationPos.z(), Blocks.hopper, towardIOPort.ordinal());
        TileEntityHopper hopper = tile(helper, TileEntityHopper.class, AUTOMATION_LABEL);
        ContinuousInvariant rejectedItemStaysInHopper = continuousInvariant(
                helper,
                "hopper must not insert an apple into the IO port",
                () -> {
                    helper.assertTrue(
                            ItemStack.areItemStacksEqual(nonCell, hopper.getStackInSlot(0)),
                            "Automation input should still contain the rejected apple");
                    helper.assertNull(ioport.getStackInSlot(0), "IO port input should reject the apple");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("supply non-cell through automation", () -> {
                    hopper.setInventorySlotContents(0, nonCell.copy());
                    rejectedItemStaysInHopper.enable();
                }).thenIdle(5).thenExecute("replace rejected item with a storage cell", () -> {
                    rejectedItemStaysInHopper.disable();
                    hopper.setInventorySlotContents(0, cell.copy());
                }).thenWaitUntil("wait for hopper-fed cell to be processed", 10, () -> {
                    helper.assertNull(hopper.getStackInSlot(0), "Hopper should insert the storage cell");
                    helper.assertNotNull(ioport.getStackInSlot(6), "Processed hopper-fed cell should reach output");
                }).thenSucceed();
    }

    // Keeps source cell contents intact when no destination storage exists.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noDestinationStorageKeepsSourceCellUnchanged(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 1);
        ContinuousInvariant noDestinationPreservesSource = continuousInvariant(
                helper,
                "missing destination storage must preserve the source cell",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Input cell should remain without a destination");
                    helper.assertNull(ioport.getStackInSlot(6), "Cell should not move without a destination");
                    assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 1);
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert source cell without destination storage", () -> {
                    ioport.setInventorySlotContents(0, sourceCell);
                    noDestinationPreservesSource.enable();
                }).thenIdle(10)
                .thenExecute("finish missing-destination observation window", noDestinationPreservesSource::disable)
                .thenSucceed();
    }

    // Preserves queued cells without duplication or loss when settings change while output is full.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void settingChangeWhileQueuedDoesNotDuplicateOrLoseCell(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack queuedCell = cell4k();
        ContinuousInvariant queuedCellIsConserved = continuousInvariant(
                helper,
                "queued cell must remain exactly once while output is full",
                () -> {
                    helper.assertNotNull(ioport.getStackInSlot(0), "Queued cell should remain in input");
                    helper.assertEquals(
                            7,
                            countFilledSlots(ioport, 0, 12),
                            "Seven total cells should be accounted for");
                });

        helper.startSequence()
                .thenWaitUntilAtEnd("wait for IO port network activation", () -> assertIOPortActive(helper, ioport))
                .thenIdle(1).thenExecuteAtStart("insert test cells into the IO port network", () -> {
                    for (int slot = 6; slot < 12; slot++) {
                        ioport.setInventorySlotContents(slot, cell1k());
                    }
                    ioport.setInventorySlotContents(0, queuedCell);
                    queuedCellIsConserved.enable();
                }).thenIdle(5).thenExecute("change settings and open output slot", () -> {
                    queuedCellIsConserved.disable();
                    configure(ioport, OperationMode.EMPTY, FullnessMode.HALF);
                    ioport.setInventorySlotContents(6, null);
                }).thenWaitUntil("wait for the conserved queued cell to move once after settings change", 30, () -> {
                    helper.assertNull(
                            ioport.getStackInSlot(0),
                            "Queued cell should leave input after changing settings");
                    helper.assertTrue(
                            ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), queuedCell),
                            "Queued cell should move exactly once to output after changing settings");
                    helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output cell count should remain six");
                }).thenSucceed();
    }

    private static TileIOPort getIOPort(GameTestHelper helper) {
        return tile(helper, TileIOPort.class, IO_PORT_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return tile(helper, TileDrive.class, DRIVE_LABEL);
    }

    private static void setRedstoneInput(GameTestHelper helper, int strength) {
        AEGameTestHelpers.setRedstoneInput(helper, REDSTONE_LABEL, strength);
    }

    private static void assertIOPortActive(GameTestHelper helper, TileIOPort ioport) {
        assertActive(helper, ioport.getProxy(), "IO port network should become active");
    }

    private static void configure(TileIOPort ioport, OperationMode operationMode, FullnessMode fullnessMode) {
        ioport.getConfigManager().putSetting(Settings.OPERATION_MODE, operationMode);
        ioport.getConfigManager().putSetting(Settings.FULLNESS_MODE, fullnessMode);
    }

    private static void configureRedstone(TileIOPort ioport, RedstoneMode redstoneMode) {
        ioport.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, redstoneMode);
    }

    private static void installRedstoneUpgrade(TileIOPort ioport) {
        installUpgrade(ioport, AEApi.instance().definitions().materials().cardRedstone().maybeStack(1).get(), 0);
    }

    private static void installSpeedUpgrades(TileIOPort ioport) {
        ItemStack speedUpgrade = AEApi.instance().definitions().materials().cardSpeed().maybeStack(1).get();
        for (int slot = 0; slot < 3; slot++) {
            installUpgrade(ioport, speedUpgrade.copy(), slot);
        }
    }

    private static void installUpgrade(TileIOPort ioport, ItemStack upgrade, int slot) {
        IInventory upgrades = ioport.getInventoryByName("upgrades");
        upgrades.setInventorySlotContents(slot, upgrade);
    }

    @SuppressWarnings("unchecked")
    private static void partitionCell(GameTestHelper helper, ItemStack cell, Block block) {
        IMEInventoryHandler<IAEItemStack> handler = itemInventory(helper, cell);
        helper.assertTrue(handler instanceof ICellInventoryHandler, "Item cell should expose a configurable inventory");
        ICellInventoryHandler<IAEItemStack> cellHandler = (ICellInventoryHandler<IAEItemStack>) handler;
        ICellInventory<IAEItemStack> cellInventory = cellHandler.getCellInv();
        helper.assertNotNull(cellInventory, "Item cell inventory should expose cell details");

        IAEStackInventory config = cellInventory.getConfigAEInventory();
        config.putAEStackInSlot(0, itemStack(block, 1));
    }

    private static void removePowerAndChannel(GameTestHelper helper) {
        Coord pos = controllerPos(helper);
        helper.destroyBlock(pos.x(), pos.y(), pos.z());
    }

    private static void restorePowerAndChannel(GameTestHelper helper) {
        Coord pos = controllerPos(helper);
        Block controller = AEApi.instance().definitions().blocks().creativeEnergyController().maybeBlock().get();
        helper.setBlock(pos.x(), pos.y(), pos.z(), controller);
        helper.assertBlockPresent(controller, pos.x(), pos.y(), pos.z());
    }

    private static Coord controllerPos(GameTestHelper helper) {
        return pos(helper, CONTROLLER_LABEL);
    }

    private static ForgeDirection directionBetween(GameTestHelper helper, String fromRole, String toRole) {
        Coord from = pos(helper, fromRole);
        Coord to = pos(helper, toRole);
        int dx = Integer.signum(to.x() - from.x());
        int dy = Integer.signum(to.y() - from.y());
        int dz = Integer.signum(to.z() - from.z());
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction.offsetX == dx && direction.offsetY == dy && direction.offsetZ == dz) {
                return direction;
            }
        }
        throw new AssertionError("Roles '" + fromRole + "' and '" + toRole + "' must define a direction");
    }

    private static int countFilledSlots(TileIOPort ioport, int startInclusive, int endExclusive) {
        int count = 0;
        for (int slot = startInclusive; slot < endExclusive; slot++) {
            if (ioport.getStackInSlot(slot) != null) {
                count++;
            }
        }
        return count;
    }
}
