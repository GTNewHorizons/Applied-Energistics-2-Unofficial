package appeng.gametests;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertChestStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.fillChest;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.setChestSlot;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.parts.IPart;
import appeng.api.storage.StorageName;
import appeng.core.AppEng;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class ImportExportBusTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String SOURCE_CHEST_LABEL = "source_chest";
    private static final String DESTINATION_CHEST_LABEL = "destination_chest";
    private static final String IMPORT_BUS_LABEL = "import_bus";
    private static final String EXPORT_BUS_LABEL = "export_bus";
    private static final String REDSTONE_LABEL = "redstone";

    // A filtered import bus should pull only matching stacks from the source chest into ME storage.
    @GameTest(template = "bus_io", timeoutTicks = 320)
    public static void importBusPullsFilteredStackIntoNetwork(GameTestHelper helper) {
        BusIO busIO = getBusIO(helper);
        ItemStack driveCell = cell1k();
        busIO.drive.setInventorySlotContents(0, driveCell);
        setChestSlot(busIO.sourceChest, 0, Blocks.cobblestone, 32);
        setChestSlot(busIO.sourceChest, 1, Blocks.dirt, 32);
        configureFilter(helper, busIO.importBus, Blocks.cobblestone);
        configureFilter(helper, busIO.exportBus, Blocks.dirt);

        helper.startSequence().thenWaitUntil(60, () -> assertBusIOActive(helper, busIO)).thenWaitUntil(300, () -> {
            assertNetworkStoredAmount(helper, busIO.controller, Blocks.cobblestone, 32);
            assertNetworkStoredAmount(helper, busIO.controller, Blocks.dirt, 0);
            assertChestStoredAmount(helper, busIO.sourceChest, Blocks.cobblestone, 0);
            assertChestStoredAmount(helper, busIO.sourceChest, Blocks.dirt, 32);
            assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 0);
        }).thenSucceed();
    }

    // A filtered export bus should move only the configured stack from ME storage into the destination chest.
    @GameTest(template = "bus_io", timeoutTicks = 320)
    public static void exportBusPushesFilteredStackToInventory(GameTestHelper helper) {
        BusIO busIO = getBusIO(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        insertItems(helper, driveCell, Blocks.dirt, 1);
        busIO.drive.setInventorySlotContents(0, driveCell);
        configureFilter(helper, busIO.importBus, Blocks.dirt);
        configureFilter(helper, busIO.exportBus, Blocks.cobblestone);

        helper.startSequence().thenWaitUntil(180, () -> {
            assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 1);
            assertChestStoredAmount(helper, busIO.destinationChest, Blocks.dirt, 0);
            assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 0);
            assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.dirt, 1);
        }).thenSucceed();
    }

    // A full destination inventory should make the export bus leave stored network items untouched.
    @GameTest(template = "bus_io", timeoutTicks = 240)
    public static void fullDestinationDoesNotVoidItems(GameTestHelper helper) {
        BusIO busIO = getBusIO(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 32);
        busIO.drive.setInventorySlotContents(0, driveCell);
        fillChest(busIO.destinationChest, Blocks.dirt);
        configureFilter(helper, busIO.importBus, Blocks.dirt);
        configureFilter(helper, busIO.exportBus, Blocks.cobblestone);

        helper.startSequence().thenWaitUntil(60, () -> assertBusIOActive(helper, busIO)).thenIdle(120)
                .thenExecute(() -> {
                    assertNetworkStoredAmount(helper, busIO.controller, Blocks.cobblestone, 32);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 0);
                    assertChestStoredAmount(
                            helper,
                            busIO.destinationChest,
                            Blocks.dirt,
                            busIO.destinationChest.getSizeInventory() * 64L);
                }).thenSucceed();
    }

    // With a redstone card installed, HIGH, LOW, and PULSE modes should gate export-bus work.
    @GameTest(template = "bus_io", timeoutTicks = 800)
    public static void redstoneModesGateBusOperation(GameTestHelper helper) {
        BusIO busIO = getBusIO(helper);
        ItemStack driveCell = cell1k();
        busIO.drive.setInventorySlotContents(0, driveCell);
        configureFilter(helper, busIO.importBus, Blocks.dirt);
        configureFilter(helper, busIO.exportBus, Blocks.cobblestone);
        installRedstoneUpgrade(helper, busIO.exportBus);
        configureRedstone(busIO.exportBus, RedstoneMode.HIGH_SIGNAL);
        setRedstoneInput(helper, 0);

        helper.startSequence().thenWaitUntil(60, () -> assertStorageNetworkActive(helper, busIO)).thenIdle(70)
                .thenExecute(() -> { injectCobblestone(helper, busIO, 1); }).thenIdle(70).thenExecute(() -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 0);
                }).thenExecute(() -> setRedstoneInput(helper, 15)).thenWaitUntil(90, () -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 1);
                }).thenExecute(() -> {
                    configureRedstone(busIO.exportBus, RedstoneMode.LOW_SIGNAL);
                    setRedstoneInput(helper, 15);
                    injectCobblestone(helper, busIO, 1);
                }).thenIdle(70).thenExecute(() -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 1);
                }).thenExecute(() -> setRedstoneInput(helper, 0)).thenWaitUntil(90, () -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 2);
                }).thenExecute(() -> {
                    configureRedstone(busIO.exportBus, RedstoneMode.SIGNAL_PULSE);
                    setRedstoneInput(helper, 0);
                    injectCobblestone(helper, busIO, 1);
                }).thenIdle(70).thenExecute(() -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 2);
                }).thenExecute(() -> setRedstoneInput(helper, 15)).thenWaitUntil(40, () -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 3);
                }).thenExecute(() -> injectCobblestone(helper, busIO, 1)).thenIdle(70).thenExecute(() -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 3);
                }).thenExecute(() -> setRedstoneInput(helper, 0)).thenIdle(5).thenExecute(() -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 3);
                }).thenExecute(() -> setRedstoneInput(helper, 15)).thenWaitUntil(40, () -> {
                    assertStoredAmount(helper, busIO.drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertChestStoredAmount(helper, busIO.destinationChest, Blocks.cobblestone, 4);
                }).thenSucceed();
    }

    private static BusIO getBusIO(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        TileEntityChest sourceChest = getSourceChest(helper);
        TileEntityChest destinationChest = getDestinationChest(helper);
        PartImportBus importBus = getImportBus(helper);
        PartExportBus exportBus = getExportBus(helper);

        return new BusIO(controller, drive, sourceChest, destinationChest, importBus, exportBus);
    }

    private static TileController getController(GameTestHelper helper) {
        return tile(helper, TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return tile(helper, TileDrive.class, DRIVE_LABEL);
    }

    private static TileEntityChest getSourceChest(GameTestHelper helper) {
        return tile(helper, TileEntityChest.class, SOURCE_CHEST_LABEL);
    }

    private static TileEntityChest getDestinationChest(GameTestHelper helper) {
        return tile(helper, TileEntityChest.class, DESTINATION_CHEST_LABEL);
    }

    private static PartImportBus getImportBus(GameTestHelper helper) {
        IPart part = part(helper, IMPORT_BUS_LABEL, ForgeDirection.WEST);
        helper.assertTrue(part instanceof PartImportBus, "Import bus label should contain an import bus");
        assert part instanceof PartImportBus;
        return (PartImportBus) part;
    }

    private static PartExportBus getExportBus(GameTestHelper helper) {
        IPart part = part(helper, EXPORT_BUS_LABEL, ForgeDirection.EAST);
        helper.assertTrue(part instanceof PartExportBus, "Export bus label should contain an export bus");
        assert part instanceof PartExportBus;
        return (PartExportBus) part;
    }

    private static void assertBusIOActive(GameTestHelper helper, BusIO busIO) {
        assertStorageNetworkActive(helper, busIO);
        assertActive(helper, busIO.importBus, "Import bus should receive a channel");
        assertActive(helper, busIO.exportBus, "Export bus should receive a channel");
    }

    private static void assertStorageNetworkActive(GameTestHelper helper, BusIO busIO) {
        assertActive(helper, busIO.controller.getProxy(), "Controller grid proxy should become active");
        assertActive(helper, busIO.drive.getProxy(), "Drive grid proxy should become active");
    }

    private static void configureFilter(GameTestHelper helper, PartSharedItemBus<?> bus, Block block) {
        IAEStackInventory config = bus.getAEInventoryByName(StorageName.CONFIG);
        helper.assertNotNull(config, "Bus config inventory should exist");
        config.putAEStackInSlot(0, itemStack(block, 1));
    }

    private static void configureRedstone(PartSharedItemBus<?> bus, RedstoneMode redstoneMode) {
        bus.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, redstoneMode);
    }

    private static void installRedstoneUpgrade(GameTestHelper helper, PartUpgradeable bus) {
        installUpgrade(helper, bus, AEApi.instance().definitions().materials().cardRedstone().maybeStack(1).get(), 0);
    }

    private static void installUpgrade(GameTestHelper helper, PartUpgradeable bus, ItemStack upgrade, int slot) {
        IInventory upgrades = bus.getInventoryByName("upgrades");
        helper.assertNotNull(upgrades, "Bus upgrade inventory should exist");
        upgrades.setInventorySlotContents(slot, upgrade);
    }

    private static void setRedstoneInput(GameTestHelper helper, int strength) {
        AEGameTestHelpers.setRedstoneInput(helper, REDSTONE_LABEL, strength);
    }

    private static void injectCobblestone(GameTestHelper helper, BusIO busIO, long amount) {
        helper.assertNull(
                injectIntoGrid(busIO.controller, Blocks.cobblestone, amount),
                "Injected cobblestone should fit into the drive cell");
    }

    @Desugar
    private record BusIO(TileController controller, TileDrive drive, TileEntityChest sourceChest,
            TileEntityChest destinationChest, PartImportBus importBus, PartExportBus exportBus) {

    }
}
