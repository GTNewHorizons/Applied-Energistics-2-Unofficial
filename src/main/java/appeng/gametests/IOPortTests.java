package appeng.gametests;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.loader.ItemAndBlockHolder;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AppEng;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

@GameTestHolder(AppEng.MOD_ID)
public class IOPortTests {

    private static final int IOPORT_X = 0;
    private static final int IOPORT_Y = 0;
    private static final int IOPORT_Z = 0;
    private static final int DRIVE_X = 1;
    private static final int DRIVE_Y = 0;
    private static final int DRIVE_Z = 1;
    private static final int REDSTONE_X = 0;
    private static final int REDSTONE_Y = 0;
    private static final int REDSTONE_Z = 1;
    private static final BaseActionSource TEST_SOURCE = new BaseActionSource();

    // Moves an empty cell from an input slot to an output slot.
    @GameTest(template = "ioport", timeoutTicks = 20)
    public static void emptyCellMove(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack cell = cell1k();

        ioport.setInventorySlotContents(0, cell.copy());

        helper.startSequence().thenExecuteAtStart(() -> {
            helper.assertTrue(ItemStack.areItemStacksEqual(ioport.getStackInSlot(0), cell));
            helper.assertTrue(ioport.getStackInSlot(6) == null);
        }).thenExecute(() -> {
            helper.assertTrue(ioport.getStackInSlot(0) == null);
            cell.setTagCompound(new NBTTagCompound());
            helper.assertTrue(ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), cell));
        }).thenSucceed();
    }

    // Exports items from a filled cell into ME network storage in EMPTY mode.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void emptyModeExportsCellContentsToNetwork(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(10, () -> {
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
        ioport.setInventorySlotContents(0, targetCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(10, () -> {
            helper.assertNull(ioport.getStackInSlot(0), "Imported cell should leave the input slot");
            helper.assertNotNull(ioport.getStackInSlot(6), "Imported cell should move to the output slot");
            assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 100);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
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
        ioport.setInventorySlotContents(0, targetCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenExecute(() -> {
            helper.assertNull(ioport.getStackInSlot(0), "Cell should leave input when the network is exactly drained");
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
        ioport.setInventorySlotContents(0, targetCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(ioport.getStackInSlot(0), "Cell should stay in input when the network starts empty");
            helper.assertNull(ioport.getStackInSlot(6), "Cell should not move to output when no work was done");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
        }).thenSucceed();
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
        ioport.setInventorySlotContents(0, targetCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenIdle(40).thenExecute(() -> {
            helper.assertNull(ioport.getStackInSlot(0), "Full target cell should leave input in move-when-empty mode");
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
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Partially transferred cell should remain in the input slot");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 44);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 256);
        }).thenWaitUntil(10, () -> {
            helper.assertNull(ioport.getStackInSlot(0), "Emptied cell should leave the input slot");
            helper.assertNotNull(ioport.getStackInSlot(6), "Emptied cell should move to the output slot");
            assertStoredAmount(helper, ioport.getStackInSlot(6), Blocks.cobblestone, 0);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 300);
        }).thenSucceed();
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
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, nearlyFullDriveCell);

        helper.startSequence().thenWaitUntil(20, () -> {
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
        ioport.setInventorySlotContents(0, targetCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(40, () -> {
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
        for (int slot = 0; slot < 6; slot++) {
            ioport.setInventorySlotContents(slot, cell1k());
        }

        helper.startSequence().thenIdle(4).thenExecute(() -> {
            helper.assertEquals(0, countFilledSlots(ioport, 0, 5), "Input slots should contain 1 cell");
            helper.assertEquals(5, countFilledSlots(ioport, 6, 12), "Output slots should contain 5 cells");
        }).thenIdle(1).thenExecute(() -> {
            for (int slot = 0; slot < 6; slot++) {
                helper.assertNull(ioport.getStackInSlot(slot), "All input slots should become empty");
            }
            helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output slots should contain 6 cells");
        }).thenSucceed();
    }

    // Keeps a cell queued while output is full, then moves it when an output slot opens.
    @GameTest(template = "ioport", timeoutTicks = 40)
    public static void outputFullKeepsCellQueuedUntilSlotOpens(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        for (int slot = 6; slot < 12; slot++) {
            ioport.setInventorySlotContents(slot, cell1k());
        }
        ItemStack queuedCell = cell4k();
        ioport.setInventorySlotContents(0, queuedCell);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(ioport.getStackInSlot(0), "Input cell should be retained while output is full");
            helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output cell count should stay full");
            ioport.setInventorySlotContents(6, null);
        }).thenIdle(1).thenExecute(() -> {
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
        for (int slot = 6; slot < 12; slot++) {
            ioport.setInventorySlotContents(slot, cell1k());
        }
        ItemStack sourceCell = cell4k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 100);
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Transferred cell should stay in input while output is full");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 0);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
            ioport.setInventorySlotContents(6, null);
        }).thenIdle(1).thenExecute(() -> {
            helper.assertNull(ioport.getStackInSlot(0), "Transferred cell should leave input once output opens");
            helper.assertNotNull(ioport.getStackInSlot(6), "Transferred cell should move into the opened output slot");
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
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, fullDriveCell);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Source cell should remain in input when destination is full");
            helper.assertNull(
                    ioport.getStackInSlot(6),
                    "Source cell should not move to output when no export was possible");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 100);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 8128);
        }).thenSucceed();
    }

    // Transfers 256 item units per tick without upgrades.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noUpgradeTransfersTwoHundredFiftySixItemsPerTick(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        ItemStack sourceCell = cell1k();
        ItemStack driveCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 300);
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenExecute(() -> {
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
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Cell with remaining contents should stay in input after speed transfer");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 88);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 512);
        }).thenSucceed();
    }

    // Transfers 256,000 mB per tick for fluid cells without upgrades.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noUpgradeTransfersTwoHundredFiftySixBucketsOfFluidPerTick(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        TileDrive drive = getDrive(helper);
        Fluid water = FluidRegistry.WATER;
        helper.assertNotNull(water, "Water fluid should be registered");
        ItemStack sourceCell = fluidCell1k(helper);
        ItemStack driveCell = fluidCell1k(helper);
        insertFluids(helper, sourceCell, water, 300_000);
        ioport.setInventorySlotContents(0, sourceCell);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Fluid cell should remain in input after exhausting transfer budget");
            assertStoredFluidAmount(helper, ioport.getStackInSlot(0), water, 44_000);
            assertStoredFluidAmount(helper, drive.getStackInSlot(0), water, 256_000);
        }).thenSucceed();
    }

    // Runs in HIGH_SIGNAL mode only after redstone power is applied.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void redstoneHighSignalRequiresPower(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        installRedstoneUpgrade(ioport);
        configureRedstone(ioport, RedstoneMode.HIGH_SIGNAL);
        ioport.setInventorySlotContents(0, cell1k());

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(ioport.getStackInSlot(0), "HIGH_SIGNAL mode should keep the input cell without power");
            helper.setRedstoneInput(REDSTONE_X, REDSTONE_Y, REDSTONE_Z, 15);
        }).thenIdle(1).thenExecute(() -> {
            helper.assertNull(ioport.getStackInSlot(0), "HIGH_SIGNAL mode should remove the input cell when powered");
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
        installRedstoneUpgrade(ioport);
        configureRedstone(ioport, RedstoneMode.LOW_SIGNAL);
        helper.setRedstoneInput(REDSTONE_X, REDSTONE_Y, REDSTONE_Z, 15);
        ioport.setInventorySlotContents(0, cell1k());

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(ioport.getStackInSlot(0), "LOW_SIGNAL mode should keep the input cell while powered");
            helper.setRedstoneInput(REDSTONE_X, REDSTONE_Y, REDSTONE_Z, 0);
        }).thenIdle(1).thenExecute(() -> {
            helper.assertNull(ioport.getStackInSlot(0), "LOW_SIGNAL mode should remove the input cell without power");
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
        installRedstoneUpgrade(ioport);
        configureRedstone(ioport, RedstoneMode.SIGNAL_PULSE);
        ioport.setInventorySlotContents(0, cell1k());
        ioport.setInventorySlotContents(1, cell1k());

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertEquals(
                    2,
                    countFilledSlots(ioport, 0, 6),
                    "SIGNAL_PULSE mode should keep both cells before a pulse");
            helper.setRedstoneInput(REDSTONE_X, REDSTONE_Y, REDSTONE_Z, 15);
        }).thenIdle(5).thenExecute(() -> {
            helper.assertEquals(
                    1,
                    countFilledSlots(ioport, 0, 6),
                    "SIGNAL_PULSE mode should leave one input cell after one pulse");
            helper.assertEquals(
                    1,
                    countFilledSlots(ioport, 6, 12),
                    "SIGNAL_PULSE mode should move exactly one cell after one pulse");
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

    // Keeps source cell contents intact when no destination storage exists.
    @GameTest(template = "ioport", timeoutTicks = 30)
    public static void noDestinationStorageKeepsSourceCellUnchanged(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        ItemStack sourceCell = cell1k();
        insertItems(helper, sourceCell, Blocks.cobblestone, 1);
        ioport.setInventorySlotContents(0, sourceCell);

        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Input cell should remain when no destination storage exists");
            helper.assertNull(
                    ioport.getStackInSlot(6),
                    "Cell should not move to output when no destination storage exists");
            assertStoredAmount(helper, ioport.getStackInSlot(0), Blocks.cobblestone, 1);
        }).thenSucceed();
    }

    // Preserves queued cells without duplication or loss when settings change while output is full.
    @GameTest(template = "ioport", timeoutTicks = 60)
    public static void settingChangeWhileQueuedDoesNotDuplicateOrLoseCell(GameTestHelper helper) {
        TileIOPort ioport = getIOPort(helper);
        getDrive(helper);
        for (int slot = 6; slot < 12; slot++) {
            ioport.setInventorySlotContents(slot, cell1k());
        }
        ItemStack queuedCell = cell4k();
        ioport.setInventorySlotContents(0, queuedCell);

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertNotNull(
                    ioport.getStackInSlot(0),
                    "Queued cell should remain in input before changing settings");
            configure(ioport, OperationMode.EMPTY, FullnessMode.HALF);
            ioport.setInventorySlotContents(6, null);
        }).thenWaitUntil(30, () -> {
            helper.assertNull(ioport.getStackInSlot(0), "Queued cell should leave input after changing settings");
            helper.assertTrue(
                    ItemStack.areItemStacksEqual(ioport.getStackInSlot(6), queuedCell),
                    "Queued cell should move exactly once to output after changing settings");
            helper.assertEquals(6, countFilledSlots(ioport, 6, 12), "Output cell count should remain six");
        }).thenSucceed();
    }

    private static TileIOPort getIOPort(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileIOPort.class, IOPORT_X, IOPORT_Y, IOPORT_Z);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileDrive.class, DRIVE_X, DRIVE_Y, DRIVE_Z);
    }

    private static ItemStack cell1k() {
        return AEApi.instance().definitions().items().cell1k().maybeStack(1).get();
    }

    private static ItemStack cell4k() {
        return AEApi.instance().definitions().items().cell4k().maybeStack(1).get();
    }

    private static ItemStack cell64k() {
        return AEApi.instance().definitions().items().cell64k().maybeStack(1).get();
    }

    private static ItemStack fluidCell1k(GameTestHelper helper) {
        helper.assertNotNull(ItemAndBlockHolder.CELL1K, "AE2FC 1k fluid cell should be available");
        return ItemAndBlockHolder.CELL1K.stack();
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

    private static void installUpgrade(TileIOPort ioport, ItemStack upgrade, int slot) {
        IInventory upgrades = ioport.getInventoryByName("upgrades");
        upgrades.setInventorySlotContents(slot, upgrade);
    }

    private static void insertItems(GameTestHelper helper, ItemStack cell, Block block, long amount) {
        IAEItemStack remainder = itemInventory(helper, cell)
                .injectItems(itemStack(block, amount), Actionable.MODULATE, TEST_SOURCE);
        helper.assertNull(remainder, "Items should fit completely into the cell");
    }

    private static void insertFluids(GameTestHelper helper, ItemStack cell, Fluid fluid, long amount) {
        IAEFluidStack remainder = fluidInventory(helper, cell)
                .injectItems(fluidStack(fluid, amount), Actionable.MODULATE, TEST_SOURCE);
        helper.assertNull(remainder, "Fluids should fit completely into the cell");
    }

    private static void assertStoredAmount(GameTestHelper helper, ItemStack cell, Block block, long expectedAmount) {
        helper.assertNotNull(cell, "Cell should exist");
        helper.assertEquals(expectedAmount, storedAmount(helper, cell, block), "Stored item amount should match");
    }

    private static void assertStoredFluidAmount(GameTestHelper helper, ItemStack cell, Fluid fluid,
            long expectedAmount) {
        helper.assertNotNull(cell, "Cell should exist");
        helper.assertEquals(expectedAmount, storedFluidAmount(helper, cell, fluid), "Stored fluid amount should match");
    }

    private static long storedAmount(GameTestHelper helper, ItemStack cell, Block block) {
        IAEItemStack request = itemStack(block, Integer.MAX_VALUE);
        IAEItemStack extracted = itemInventory(helper, cell).extractItems(request, Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    private static long storedFluidAmount(GameTestHelper helper, ItemStack cell, Fluid fluid) {
        IAEFluidStack request = fluidStack(fluid, Long.MAX_VALUE);
        IAEFluidStack extracted = fluidInventory(helper, cell).extractItems(request, Actionable.SIMULATE, TEST_SOURCE);
        return extracted == null ? 0 : extracted.getStackSize();
    }

    @SuppressWarnings("unchecked")
    private static IMEInventoryHandler<IAEItemStack> itemInventory(GameTestHelper helper, ItemStack cell) {
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cell);
        helper.assertNotNull(cellHandler, "Cell handler should exist");
        IMEInventoryHandler<IAEItemStack> cellInv = cellHandler.getCellInventory(cell, null, ITEM_STACK_TYPE);
        helper.assertNotNull(cellInv, "Item cell inventory should exist");
        return cellInv;
    }

    @SuppressWarnings("unchecked")
    private static IMEInventoryHandler<IAEFluidStack> fluidInventory(GameTestHelper helper, ItemStack cell) {
        ICellHandler cellHandler = AEApi.instance().registries().cell().getHandler(cell);
        helper.assertNotNull(cellHandler, "Cell handler should exist");
        IMEInventoryHandler<IAEFluidStack> cellInv = cellHandler.getCellInventory(cell, null, FLUID_STACK_TYPE);
        helper.assertNotNull(cellInv, "Fluid cell inventory should exist");
        return cellInv;
    }

    private static IAEItemStack itemStack(Block block, long amount) {
        IAEItemStack stack = AEItemStack.create(new ItemStack(block, 1));
        stack.setStackSize(amount);
        return stack;
    }

    private static IAEFluidStack fluidStack(Fluid fluid, long amount) {
        IAEFluidStack stack = AEFluidStack.create(new FluidStack(fluid, 1));
        stack.setStackSize(amount);
        return stack;
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
