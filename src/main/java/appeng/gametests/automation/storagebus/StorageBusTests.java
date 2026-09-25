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
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.ReshuffleActionSource;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.IMEInventoryHandler;
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
    @GameTest(template = "storage_bus", timeoutTicks = 100)
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

    @GameTest(template = "storage_bus", timeoutTicks = 100)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void reshufflerAccessControlsExternalChest(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone, 64));

        helper.startSequence().thenWaitUntil("wait for storage bus inventory", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            helper.assertFalse(storageBus.getCellArray(ITEM_STACK_TYPE).isEmpty(), "Storage bus inventory is missing");
        }).thenExecute("verify reshuffler access modes", () -> {
            IMEInventoryHandler inventory = storageBus.getCellArray(ITEM_STACK_TYPE).get(0);
            ReshuffleActionSource reshuffleSource = new ReshuffleActionSource(controller);
            MachineSource ordinarySource = new MachineSource(controller);

            for (AccessRestriction access : AccessRestriction.values()) {
                storageBus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, access);

                helper.assertEquals(access, inventory.getReshuffleAccess(), "Storage bus should report its access");
                helper.assertEquals(
                        access.hasPermission(AccessRestriction.READ),
                        inventory.extractItems(itemStack(Blocks.cobblestone, 1), Actionable.SIMULATE, reshuffleSource)
                                != null,
                        access + " should control reshuffler extraction");
                helper.assertEquals(
                        access.hasPermission(AccessRestriction.WRITE),
                        inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, reshuffleSource) == null,
                        access + " should control reshuffler insertion");
                helper.assertNotNull(
                        inventory.extractItems(itemStack(Blocks.cobblestone, 1), Actionable.SIMULATE, ordinarySource),
                        "Ordinary extraction should remain allowed");
                helper.assertNull(
                        inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, ordinarySource),
                        "Ordinary insertion should remain allowed");
            }

            storageBus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, AccessRestriction.NO_ACCESS);
            helper.assertEquals(
                    AccessRestriction.NO_ACCESS,
                    reloadStorageBus(storageBus).getReshuffleAccess(),
                    "Storage bus should retain reshuffler access after reload");
        }).thenSucceed();
    }

    @GameTest(template = "storage_bus", timeoutTicks = 240)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void normalAccessAlsoRestrictsReshuffler(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        storageBus.getConfigManager().putSetting(Settings.ACCESS, AccessRestriction.WRITE);
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone, 64));

        helper.startSequence().thenWaitUntil("wait for input-only storage bus", 80, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            IMEInventoryHandler inventory = storageBus.getInternalHandler();
            helper.assertNotNull(inventory, "Storage bus inventory is missing");
            helper.assertEquals(AccessRestriction.WRITE, inventory.getAccess(), "Bus should be input-only");
        }).thenExecute("verify input-only reshuffler access", () -> {
            IMEInventoryHandler inventory = storageBus.getInternalHandler();
            ReshuffleActionSource source = new ReshuffleActionSource(controller);
            MachineSource ordinarySource = new MachineSource(controller);
            helper.assertEquals(
                    AccessRestriction.WRITE,
                    inventory.getReshuffleAccess(),
                    "Input-only bus should not be an extraction source, including during subnet traversal");
            helper.assertNull(
                    inventory.extractItems(itemStack(Blocks.cobblestone, 1), Actionable.SIMULATE, source),
                    "Reshuffler should not extract through an input-only bus");
            helper.assertNull(
                    inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, source),
                    "Reshuffler should insert through an input-only bus");

            storageBus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, AccessRestriction.READ);
            helper.assertEquals(
                    AccessRestriction.NO_ACCESS,
                    inventory.getReshuffleAccess(),
                    "Input-only bus plus extract-only reshuffler access should deny both directions");
            helper.assertNull(
                    inventory.extractItems(itemStack(Blocks.cobblestone, 1), Actionable.SIMULATE, source),
                    "Conflicting settings should deny reshuffler extraction");
            helper.assertNotNull(
                    inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, source),
                    "Conflicting settings should deny reshuffler insertion");
            helper.assertNull(
                    inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, ordinarySource),
                    "Conflicting reshuffler setting should not prevent ordinary insertion");

            storageBus.getConfigManager().putSetting(Settings.RESHUFFLE_ACCESS, AccessRestriction.READ_WRITE);
            storageBus.getConfigManager().putSetting(Settings.ACCESS, AccessRestriction.READ);
        }).thenWaitUntil("wait for output-only storage bus", 80, () -> {
            IMEInventoryHandler inventory = storageBus.getInternalHandler();
            helper.assertNotNull(inventory, "Storage bus inventory is missing");
            helper.assertEquals(AccessRestriction.READ, inventory.getAccess(), "Bus should be output-only");
        }).thenExecute("verify output-only reshuffler access", () -> {
            IMEInventoryHandler inventory = storageBus.getInternalHandler();
            ReshuffleActionSource source = new ReshuffleActionSource(controller);
            helper.assertEquals(
                    AccessRestriction.READ,
                    inventory.getReshuffleAccess(),
                    "Output-only bus should not be an injection target");
            helper.assertNotNull(
                    inventory.extractItems(itemStack(Blocks.cobblestone, 1), Actionable.SIMULATE, source),
                    "Reshuffler should extract through an output-only bus");
            helper.assertNotNull(
                    inventory.injectItems(itemStack(Blocks.dirt, 1), Actionable.SIMULATE, source),
                    "Reshuffler should not insert through an output-only bus");
        }).thenSucceed();
    }

    // Reflects external chest mutations after the storage bus monitor refreshes.
    @GameTest(template = "storage_bus", timeoutTicks = 140)
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
    @GameTest(template = "storage_bus", timeoutTicks = 220)
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
    @GameTest(template = "storage_bus", timeoutTicks = 220)
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

    // A sticky storage bus should claim matching items before non-sticky storage with a higher numeric priority.
    @GameTest(template = "storage_bus", timeoutTicks = 220)
    public static void stickyStorageBusReceivesMatchingItemsBeforeHigherPriorityDrive(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        TileDrive drive = getDrive(helper);
        ItemStack driveCell = cell1k();
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone));
        installStickyCard(helper, storageBus);
        storageBus.setPriority(0);
        drive.setPriority(100);

        helper.startSequence().thenWaitUntil("wait for sticky storage bus network activation", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 1);
        }).thenExecute("insert higher-priority non-sticky drive cell", () -> helper.setSlot(DRIVE_LABEL, 0, driveCell))
                .thenWaitUntil(
                        "wait for sticky storage bus routing to become available",
                        60,
                        () -> helper.assertNull(
                                simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                                "Sticky storage bus should accept matching cobblestone"))
                .thenExecute("inject matching item and validate sticky storage bus routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Matching items should fit into the sticky external inventory");
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 65);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 65);
                }).thenSucceed();
    }

    // A sticky storage bus should let unrelated item types fall through to normal network storage.
    @GameTest(template = "storage_bus", timeoutTicks = 220)
    public static void stickyStorageBusLetsUnrelatedItemsFallBackToDrive(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        TileDrive drive = getDrive(helper);
        ItemStack driveCell = cell1k();
        helper.setSlot(EXTERNAL_CHEST_LABEL, 0, new ItemStack(Blocks.cobblestone));
        installStickyCard(helper, storageBus);
        storageBus.setPriority(0);
        drive.setPriority(100);

        helper.startSequence().thenWaitUntil("wait for sticky storage bus fallback network activation", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 1);
        }).thenExecute("insert higher-priority non-sticky drive cell", () -> helper.setSlot(DRIVE_LABEL, 0, driveCell))
                .thenWaitUntil(
                        "wait for non-sticky drive fallback routing to become available",
                        60,
                        () -> helper.assertNull(
                                simulateInjectIntoGrid(controller, Blocks.dirt, 64),
                                "Drive cell should accept an item unrelated to the sticky storage bus"))
                .thenExecute("inject unrelated item and validate drive fallback", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.dirt, 64);

                    helper.assertNull(remainder, "Unrelated items should fit into the drive cell");
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.dirt), 0);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.dirt, 64);
                    assertNetworkStoredAmount(helper, controller, Blocks.dirt, 64);
                }).thenSucceed();
    }

    // A filtered sticky storage bus should claim configured items even when the external inventory is empty.
    @GameTest(template = "storage_bus", timeoutTicks = 220)
    public static void filteredStickyStorageBusReceivesConfiguredItemBeforeHigherPriorityDrive(GameTestHelper helper) {
        TileController controller = getController(helper);
        PartStorageBus storageBus = getStorageBus(helper);
        TileDrive drive = getDrive(helper);
        ItemStack driveCell = cell1k();
        configureStorageBusFilter(helper, storageBus, Blocks.cobblestone);
        installStickyCard(helper, storageBus);
        storageBus.setPriority(0);
        drive.setPriority(100);

        helper.startSequence().thenWaitUntil("wait for filtered sticky storage bus network activation", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, storageBus, "Storage bus should receive a channel");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
        }).thenExecute("insert higher-priority non-sticky drive cell and refresh storage bus", () -> {
            helper.setSlot(DRIVE_LABEL, 0, driveCell);
            storageBus.onNeighborChanged();
        }).thenWaitUntil(
                "wait for filtered sticky storage bus routing to become available",
                STORAGE_BUS_REFRESH_TIMEOUT_TICKS,
                () -> {
                    assertFilteredStickyStorageBusReady(helper, storageBus, Blocks.cobblestone);
                    helper.assertNull(
                            simulateInjectIntoGrid(controller, Blocks.cobblestone, 64),
                            "Configured cobblestone should fit into available network storage");
                }).thenExecute("inject configured item and validate filtered sticky routing", () -> {
                    IAEItemStack remainder = injectIntoGrid(controller, Blocks.cobblestone, 64);

                    helper.assertNull(remainder, "Configured items should fit into the sticky external inventory");
                    helper.assertInventoryCount(EXTERNAL_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 64);
                    assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 0);
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 64);
                }).thenSucceed();
    }

    // A storage bus whitelist should accept matching items and reject non-matching insertions.
    @GameTest(template = "storage_bus", timeoutTicks = 220)
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

    // Reloading with the ore filter card installed must preserve the restoration copy used after card reinsertion.
    @GameTest(template = "storage_bus")
    public static void oreFilterSurvivesReloadAndCardReinsertion(GameTestHelper helper) {
        PartStorageBus storageBus = getStorageBus(helper);
        installOreFilterCard(helper, storageBus);
        storageBus.setFilter("ingotIron");

        PartStorageBus reloadedStorageBus = reloadStorageBus(storageBus);
        helper.assertEquals(
                "ingotIron",
                reloadedStorageBus.getFilter(),
                "Reloaded storage bus should retain its active ore filter");

        removeOreFilterCard(helper, reloadedStorageBus);
        helper.assertEquals(
                "",
                reloadedStorageBus.getFilter(),
                "Removing the ore filter card should temporarily disable its filter");
        installOreFilterCard(helper, reloadedStorageBus);
        helper.assertEquals(
                "ingotIron",
                reloadedStorageBus.getFilter(),
                "Reinserting the ore filter card after reload should restore its filter");
        helper.succeed();
    }

    @GameTest(template = "multi_storage_bus")
    public static void multipleStorageBusReadCorrectly(GameTestHelper helper) {
        TileController controller = getController(helper);
        helper.startSequence().thenWaitUntil("wait network to wake up and expose correct items", 60, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.cobblestone, 128);
            assertNetworkMonitorStoredAmount(helper, controller, Blocks.dirt, 128);
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void assertFilteredStickyStorageBusReady(GameTestHelper helper, PartStorageBus storageBus,
            Block block) {
        List<IMEInventoryHandler> handlers = storageBus.getCellArray(ITEM_STACK_TYPE);
        helper.assertEquals(1, handlers.size(), "Storage bus should expose one item inventory handler");
        IMEInventoryHandler handler = handlers.get(0);
        helper.assertTrue(handler.getSticky(), "Storage bus inventory handler should be sticky");
        helper.assertTrue(
                handler.isPrioritized(itemStack(block, 1)),
                "Storage bus inventory handler should prioritize its configured item");
    }

    private static PartStorageBus reloadStorageBus(PartStorageBus storageBus) {
        NBTTagCompound data = new NBTTagCompound();
        storageBus.writeToNBT(data);

        ItemStack partItem = storageBus.getItemStack(PartItemStack.Network);
        PartStorageBus reloadedStorageBus = new PartStorageBus(partItem);
        reloadedStorageBus.readFromNBT(data);
        return reloadedStorageBus;
    }

    private static void installOreFilterCard(GameTestHelper helper, PartStorageBus storageBus) {
        IInventory upgrades = storageBus.getInventoryByName("upgrades");
        helper.assertNotNull(upgrades, "Storage bus upgrade inventory should exist");
        ItemStack card = AEApi.instance().definitions().materials().cardOreFilter().maybeStack(1).get();
        InventoryHelper.setSlot(upgrades, 0, card);
    }

    private static void installStickyCard(GameTestHelper helper, PartStorageBus storageBus) {
        IInventory upgrades = storageBus.getInventoryByName("upgrades");
        helper.assertNotNull(upgrades, "Storage bus upgrade inventory should exist");
        ItemStack card = AEApi.instance().definitions().materials().cardSticky().maybeStack(1).get();
        InventoryHelper.setSlot(upgrades, 0, card);
    }

    private static void removeOreFilterCard(GameTestHelper helper, PartStorageBus storageBus) {
        IInventory upgrades = storageBus.getInventoryByName("upgrades");
        helper.assertNotNull(upgrades, "Storage bus upgrade inventory should exist");
        InventoryHelper.clearSlot(upgrades, 0);
    }

}
