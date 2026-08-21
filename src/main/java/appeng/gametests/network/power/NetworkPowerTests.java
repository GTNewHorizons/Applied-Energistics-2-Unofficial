package appeng.gametests.network.power;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.storedAmount;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.parts.IPart;
import appeng.api.storage.StorageName;
import appeng.core.AppEng;
import appeng.parts.automation.PartExportBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.networking.TileEnergyAcceptor;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class NetworkPowerTests {

    private static final String ENERGY_ACCEPTOR_LABEL = "energy_acceptor";
    private static final String DRIVE_LABEL = "drive";
    private static final String EXPORT_BUS_LABEL = "export_bus";
    private static final String DESTINATION_CHEST_LABEL = "destination_chest";
    private static final String POWER_PROBE_LABEL = "power_probe";
    private static final long COBBLESTONE_COUNT = 16;

    @GameTest(template = "energy_acceptor_network", timeoutTicks = 1200)
    public static void energyAcceptorInjectionBootsGrid(GameTestHelper helper) {
        NetworkFixture network = setUpNetwork(helper);
        TickCallbackHandle noPrematureExport = watchForPrematureExport(helper, network);
        TickCallbackHandle conservation = watchCobblestoneConservation(helper, network);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the unpowered export preconditions",
                        60,
                        () -> assertUnpoweredExportPreconditions(helper, network))
                .thenExecute(
                        "inject power through the energy acceptor's public interface",
                        () -> injectTestPower(helper, network))
                .thenWaitUntil(
                        "wait for the probed energy grid to report powered",
                        80,
                        () -> assertGridPowered(helper, network))
                .thenWaitUntil(
                        "wait for the export bus to become active",
                        40,
                        () -> assertExportBusActive(helper, network))
                .thenWaitUntil(
                        "wait for all cobblestone to reach the destination",
                        1000,
                        () -> assertExportComplete(helper, network))
                .thenExecute("stop continuous invariant checks", () -> {
                    noPrematureExport.disable();
                    conservation.disable();
                }).thenSucceed();
    }

    private static NetworkFixture setUpNetwork(GameTestHelper helper) {
        TileEnergyAcceptor energyAcceptor = helper
                .assertTileEntityPresent(TileEnergyAcceptor.class, ENERGY_ACCEPTOR_LABEL);
        IAEPowerStorage acceptorPower = energyAcceptor;
        TileDrive drive = helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
        PartExportBus exportBus = part(helper, EXPORT_BUS_LABEL, PartExportBus.class);
        IPart powerProbe = part(helper, POWER_PROBE_LABEL, ForgeDirection.UNKNOWN);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, COBBLESTONE_COUNT);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        configureFilter(helper, exportBus);

        helper.assertTrue(
                acceptorPower.isAEPublicPowerStorage(),
                "Energy acceptor should expose its public AE power-storage interface");

        return new NetworkFixture(acceptorPower, drive, exportBus, powerProbe);
    }

    private static TickCallbackHandle watchForPrematureExport(GameTestHelper helper, NetworkFixture network) {
        return helper.onEachTick(() -> {
            if (!energyGrid(helper, network).isNetworkPowered()) {
                helper.assertInventoryCount(DESTINATION_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 0);
            }
        });
    }

    private static TickCallbackHandle watchCobblestoneConservation(GameTestHelper helper, NetworkFixture network) {
        return helper.onEachTick(() -> {
            long networkAmount = storedAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone);
            long destinationAmount = helper.countItems(DESTINATION_CHEST_LABEL, new ItemStack(Blocks.cobblestone));
            helper.assertEquals(
                    COBBLESTONE_COUNT,
                    networkAmount + destinationAmount,
                    "Cobblestone should be conserved; network=" + networkAmount + ", destination=" + destinationAmount);
        });
    }

    private static void assertUnpoweredExportPreconditions(GameTestHelper helper, NetworkFixture network) {
        helper.assertEquals(
                0.0,
                network.acceptorPower().getAECurrentPower(),
                0.000001,
                "Energy acceptor must remain empty before injection");
        helper.assertFalse(
                energyGrid(helper, network).isNetworkPowered(),
                "Energy grid should report unpowered before injection");
        assertInactive(helper, network.exportBus(), "Export bus should remain inactive without grid power");
        helper.assertInventoryEmpty(DESTINATION_CHEST_LABEL);
        assertStoredAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone, COBBLESTONE_COUNT);
    }

    private static void injectTestPower(GameTestHelper helper, NetworkFixture network) {
        IAEPowerStorage acceptorPower = network.acceptorPower();
        double overflow = acceptorPower.injectAEPower(acceptorPower.getAEMaxPower(), Actionable.MODULATE);

        helper.assertEquals(0.0, overflow, 0.000001, "Energy acceptor should accept all injected test power");
        helper.assertTrue(
                acceptorPower.getAECurrentPower() > 0,
                "Energy acceptor should contain injected power before the grid consumes it");
    }

    private static void assertGridPowered(GameTestHelper helper, NetworkFixture network) {
        helper.assertTrue(
                energyGrid(helper, network).isNetworkPowered(),
                "Energy grid behind power_probe should report powered");
    }

    private static void assertExportBusActive(GameTestHelper helper, NetworkFixture network) {
        assertActive(helper, network.exportBus(), "Export bus should wake after grid power is published");
    }

    private static void assertExportComplete(GameTestHelper helper, NetworkFixture network) {
        helper.assertInventoryCount(DESTINATION_CHEST_LABEL, new ItemStack(Blocks.cobblestone), COBBLESTONE_COUNT);
        assertStoredAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone, 0);
    }

    private static void configureFilter(GameTestHelper helper, PartExportBus exportBus) {
        IAEStackInventory config = exportBus.getAEInventoryByName(StorageName.CONFIG);
        helper.assertNotNull(config, "Export bus config inventory should exist");
        config.putAEStackInSlot(0, itemStack(Blocks.cobblestone, 1));
    }

    private static IEnergyGrid energyGrid(GameTestHelper helper, NetworkFixture network) {
        IGridNode node = network.powerProbe().getGridNode();
        helper.assertNotNull(node, "Power probe should have a grid node");
        IGrid grid = node.getGrid();
        helper.assertNotNull(grid, "Power probe should be attached to a grid");
        IEnergyGrid energyGrid = grid.getCache(IEnergyGrid.class);
        helper.assertNotNull(energyGrid, "Power probe grid should expose an energy cache");
        return energyGrid;
    }

    @Desugar
    private record NetworkFixture(IAEPowerStorage acceptorPower, TileDrive drive, PartExportBus exportBus,
            IPart powerProbe) {}
}
