package appeng.gametests.automation.storagebus;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertItemRemainder;
import static appeng.gametests.AEGameTestHelpers.assertNetworkMonitorStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.injectIntoGrid;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.simulateInjectIntoGrid;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AppEng;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class StorageBusTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String STORAGE_BUS_LABEL = "storage_bus";
    private static final String EXTERNAL_CHEST_LABEL = "external_chest";
    private static final String DRIVE_LABEL = "drive";
    private static final int STORAGE_BUS_REFRESH_TIMEOUT_TICKS = 80;

    // Exposes items in the adjacent vanilla chest through the ME item storage monitor.
    @GameTest(template = "storage_bus", timeoutTicks = 100, required = false)
    public static void storageBusExposesExternalChestContents(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone, 64));

        helper.startSequence().thenWaitUntil("wait for storage bus to expose 64 external cobblestone", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 64);
        }).thenSucceed();
    }

    // Reflects external chest mutations after the storage bus monitor refreshes.
    @GameTest(template = "storage_bus", timeoutTicks = 140, required = false)
    public static void storageBusReflectsExternalMutation(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone, 16));

        helper.startSequence().thenWaitUntil("wait for storage bus to expose the initial 16 cobblestone", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 16);
        }).thenExecute(
                "replace external stack with 40 cobblestone",
                () -> helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone, 40)))
                .thenWaitUntil(
                        "wait for storage monitor to refresh to 40 external cobblestone",
                        80,
                        () -> assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 40))
                .thenSucceed();
    }

    // READ mode exposes the external chest but refuses network insertions into it.
    @GameTest(template = "storage_bus", timeoutTicks = 220, required = false)
    public static void accessModeReadPreventsInsertion(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone));

        helper.startSequence().thenWaitUntil("wait for READ-mode test storage bus to become visible", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 1);
        }).thenExecute(
                "empty the external chest before enabling READ mode",
                () -> helper.clearSlot(EXTERNAL_CHEST_LABEL, 0))
                .thenWaitUntil(
                        "wait for cleared external chest to disappear from the network monitor",
                        60,
                        () -> assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 0))
                .thenExecute("configure storage bus for READ-only access while a neighbor refresh is queued", () -> {
                    storageBus.getConfigManager().putSetting(Settings.ACCESS, AccessRestriction.READ);
                    storageBus.onNeighborChanged();
                })
                .thenWaitUntil(
                        "wait for READ mode to reject simulated insertion",
                        STORAGE_BUS_REFRESH_TIMEOUT_TICKS,
                        () -> {
                            IAEItemStack remainder = simulateInjectIntoGrid(controller, Blocks.cobblestone, 64);
                            assertItemRemainder(helper, remainder, Blocks.cobblestone, 64);
                        })
                .thenExecute("attempt real insertion through READ-only storage bus", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    assertItemRemainder(helper, remainder, Blocks.cobblestone, 64);
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 0);
                }).thenSucceed();
    }

    // New items should route to the higher-priority external chest before the lower-priority drive cell.
    @GameTest(template = "storage_bus", timeoutTicks = 220, required = false)
    public static void storageBusPriorityBeatsDriveCell(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        TileDrive drive = getDrive(helper);
        ItemStack driveCell = cell1k();
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone));
        insertItems(helper, driveCell, Blocks.cobblestone, 1);
        storageBus.setPriority(100);
        drive.setPriority(0);

        helper.startSequence().thenWaitUntil("wait for priority test storage network to activate", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 1);
        }).thenExecute("empty the high-priority external chest", () -> helper.clearSlot(EXTERNAL_CHEST_LABEL, 0))
                .thenWaitUntil(
                        "wait for the empty external chest to disappear from the storage monitor",
                        60,
                        () -> assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 0))
                .thenExecute("insert lower-priority drive cell", () -> helper.setSlot(DRIVE_LABEL, 0, driveCell))
                .thenWaitUntil(
                        "wait for lower-priority drive contents to become visible",
                        60,
                        () -> { assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 1); })
                .thenExecute("inject 64 cobblestone and validate high-priority routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Injected items should fit into available network storage");
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 64);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 1);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 65);
                }).thenSucceed();
    }

    // A storage bus whitelist should accept matching items and reject non-matching insertions.
    @GameTest(template = "storage_bus", timeoutTicks = 220, required = false)
    public static void filteredStorageBusRejectsNonMatchingItems(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone));

        helper.startSequence().thenWaitUntil("wait for filtered storage bus network to activate", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 1);
        }).thenExecute(
                "empty the external chest before configuring its filter",
                () -> helper.clearSlot(EXTERNAL_CHEST_LABEL, 0))
                .thenWaitUntil(
                        "wait for the empty external chest to disappear from the storage monitor",
                        60,
                        () -> assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 0))
                .thenExecute("configure cobblestone-only storage bus filter while a neighbor refresh is queued", () -> {
                    configureStorageBusFilter(helper, storageBus, Blocks.cobblestone);
                    storageBus.onNeighborChanged();
                })
                .thenWaitUntil(
                        "wait for filter to accept cobblestone and reject dirt in simulation",
                        STORAGE_BUS_REFRESH_TIMEOUT_TICKS,
                        () -> {
                            helper.assertNull(
                                    simulateInjectIntoGrid(controller, Blocks.cobblestone, 1),
                                    "Storage bus should accept matching items");
                            IAEItemStack remainder = simulateInjectIntoGrid(controller, Blocks.dirt, 16);
                            assertItemRemainder(helper, remainder, Blocks.dirt, 16);
                        })
                .thenExecute("inject matching cobblestone and non-matching dirt", () -> {
                    IAEItemStack matchingRemainder = injectIntoGrid(controller, Blocks.cobblestone, 16);
                    IAEItemStack nonMatchingRemainder = injectIntoGrid(controller, Blocks.dirt, 16);

                    helper.assertNull(matchingRemainder, "Matching stack should enter the filtered storage bus");
                    assertItemRemainder(helper, nonMatchingRemainder, Blocks.dirt, 16);
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 16);
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.dirt), 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 16);
                }).thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
    }

    private static PartStorageBus getStorageBus(GameTestHelper helper) {
        return part(helper, STORAGE_BUS_LABEL, PartStorageBus.class);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
    }

    private static void configureStorageBusFilter(GameTestHelper helper, PartStorageBus storageBus, Block block) {
        IAEStackInventory config = storageBus.getAEInventoryByName(StorageName.CONFIG);
        helper.assertNotNull(config, "Storage bus config inventory should exist");
        config.putAEStackInSlot(0, itemStack(block, 1));
    }

}
