package appeng.gametests.network.power;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.storedAmount;

import java.util.HashSet;

import net.minecraft.entity.item.EntityItem;
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
import appeng.core.settings.TickRates;
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
    private static final long RECOVERY_COBBLESTONE_COUNT = 4;

    @GameTest(template = "energy_acceptor_network", timeoutTicks = 1200)
    public static void energyAcceptorInjectionBootsGrid(GameTestHelper helper) {
        NetworkFixture network = setUpNetwork(helper, COBBLESTONE_COUNT);
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
                        exportTimeout(COBBLESTONE_COUNT),
                        () -> assertExportComplete(helper, network))
                .thenExecute("stop continuous invariant checks", () -> {
                    noPrematureExport.disable();
                    conservation.disable();
                }).thenSucceed();
    }

    @GameTest(template = "energy_acceptor_network", timeoutTicks = 1400)
    public static void powerLossPausesAndRecoveryResumesExport(GameTestHelper helper) {
        NetworkFixture network = setUpNetwork(helper, RECOVERY_COBBLESTONE_COUNT);
        TransferSnapshot[] pausedAt = new TransferSnapshot[1];
        TickCallbackHandle conservation = watchCobblestoneConservation(helper, network);
        TickCallbackHandle paused = helper.onEachTickDisabled(
                "export remains paused without power",
                () -> assertTransferPaused(helper, network, pausedAt[0]));
        TickCallbackHandle complete = helper.onEachTickDisabled(
                "completed export does not duplicate or lose items",
                () -> assertTransferState(helper, network, 0, network.cobblestoneCount()));

        assertTransferState(helper, network, network.cobblestoneCount(), 0);

        helper.startSequence().thenExecuteAtStart("inject initial export power", () -> injectTestPower(helper, network))
                .thenWaitUntil("wait for powered export bus", 300, () -> {
                    assertGridPowered(helper, network);
                    assertExportBusActive(helper, network);
                    helper.assertTrue(
                            transferSnapshot(helper, network).networkAmount() > 0,
                            "Source cell should still contain cobblestone when export becomes ready");
                }).thenWaitUntil("wait for a strict partial export", exportTimeout(1), () -> {
                    TransferSnapshot current = transferSnapshot(helper, network);
                    helper.assertTrue(
                            current.destinationAmount() > 0 && current.destinationAmount() < network.cobblestoneCount(),
                            "Expected a strict partial destination count below " + network.cobblestoneCount()
                                    + "; observed="
                                    + current.destinationAmount());
                    assertConserved(helper, network, current);
                    pausedAt[0] = current;
                }).thenIdle(1).thenExecuteAtStart("drain all available grid power", () -> {
                    drainGridPower(helper, network);
                    paused.enable();
                }).thenWaitUntil("wait for grid and export bus to become unpowered", 160, () -> {
                    helper.assertFalse(
                            energyGrid(helper, network).isNetworkPowered(),
                            "Energy grid should report unpowered after draining all available power");
                    assertInactive(helper, network.exportBus(), "Export bus should become inactive after power loss");
                }).thenIdle(80).thenExecuteAtStart("restore export power", () -> {
                    assertTransferPaused(helper, network, pausedAt[0]);
                    paused.disable();
                    injectTestPower(helper, network);
                }).thenWaitUntil("wait for grid and export bus to recover", 240, () -> {
                    assertGridPowered(helper, network);
                    assertExportBusActive(helper, network);
                })
                .thenWaitUntil(
                        "wait for export to resume and finish",
                        exportTimeout(network.cobblestoneCount()),
                        () -> assertTransferState(helper, network, 0, network.cobblestoneCount()))
                .thenExecute("begin completed-export duplicate guard", complete::enable).thenIdle(60)
                .thenExecute("stop continuous invariant checks", () -> {
                    complete.disable();
                    conservation.disable();
                    assertTransferState(helper, network, 0, network.cobblestoneCount());
                }).thenSucceed();
    }

    private static NetworkFixture setUpNetwork(GameTestHelper helper, long cobblestoneCount) {
        TileEnergyAcceptor energyAcceptor = helper
                .assertTileEntityPresent(TileEnergyAcceptor.class, ENERGY_ACCEPTOR_LABEL);
        IAEPowerStorage acceptorPower = energyAcceptor;
        TileDrive drive = helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
        PartExportBus exportBus = part(helper, EXPORT_BUS_LABEL, PartExportBus.class);
        IPart powerProbe = part(helper, POWER_PROBE_LABEL, ForgeDirection.UNKNOWN);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, cobblestoneCount);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        configureFilter(helper, exportBus);

        helper.assertTrue(
                acceptorPower.isAEPublicPowerStorage(),
                "Energy acceptor should expose its public AE power-storage interface");

        return new NetworkFixture(acceptorPower, drive, exportBus, powerProbe, cobblestoneCount);
    }

    private static TickCallbackHandle watchForPrematureExport(GameTestHelper helper, NetworkFixture network) {
        return helper.onEachTick("unpowered network does not export", () -> {
            if (!energyGrid(helper, network).isNetworkPowered()) {
                helper.assertInventoryCount(DESTINATION_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 0);
            }
        });
    }

    private static TickCallbackHandle watchCobblestoneConservation(GameTestHelper helper, NetworkFixture network) {
        return helper.onEachTick(
                "cobblestone is conserved during export",
                () -> { assertConserved(helper, network, transferSnapshot(helper, network)); });
    }

    private static void assertTransferPaused(GameTestHelper helper, NetworkFixture network, TransferSnapshot expected) {
        helper.assertNotNull(expected, "Partial export snapshot should exist before the pause window");
        TransferSnapshot observed = transferSnapshot(helper, network);
        helper.assertEquals(
                expected.networkAmount(),
                observed.networkAmount(),
                "Unpowered export changed the source count; expected=" + expected + ", observed=" + observed);
        helper.assertEquals(
                expected.destinationAmount(),
                observed.destinationAmount(),
                "Unpowered export changed the destination count; expected=" + expected + ", observed=" + observed);
        assertConserved(helper, network, observed);
    }

    private static void assertTransferState(GameTestHelper helper, NetworkFixture network, long expectedNetwork,
            long expectedDestination) {
        TransferSnapshot observed = transferSnapshot(helper, network);
        helper.assertEquals(expectedNetwork, observed.networkAmount(), "Unexpected source count; observed=" + observed);
        helper.assertEquals(
                expectedDestination,
                observed.destinationAmount(),
                "Unexpected destination count; observed=" + observed);
        assertConserved(helper, network, observed);
    }

    private static void assertConserved(GameTestHelper helper, NetworkFixture network, TransferSnapshot observed) {
        helper.assertEquals(0L, observed.droppedAmount(), "Export must not drop cobblestone; observed=" + observed);
        helper.assertEquals(
                network.cobblestoneCount(),
                observed.networkAmount() + observed.destinationAmount() + observed.droppedAmount(),
                "Cobblestone should be conserved; observed=" + observed);
    }

    private static TransferSnapshot transferSnapshot(GameTestHelper helper, NetworkFixture network) {
        return new TransferSnapshot(
                storedAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone),
                helper.countItems(DESTINATION_CHEST_LABEL, new ItemStack(Blocks.cobblestone)),
                droppedCobblestone(helper));
    }

    private static long droppedCobblestone(GameTestHelper helper) {
        ItemStack expected = new ItemStack(Blocks.cobblestone);
        long amount = 0;
        for (EntityItem drop : helper.getEntities(EntityItem.class, -1, -1, -1, 4, 2, 2)) {
            ItemStack stack = drop.getEntityItem();
            if (stack != null && stack.isItemEqual(expected)) {
                amount += stack.stackSize;
            }
        }
        return amount;
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
        assertStoredAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone, network.cobblestoneCount());
    }

    private static void injectTestPower(GameTestHelper helper, NetworkFixture network) {
        IAEPowerStorage acceptorPower = network.acceptorPower();
        double overflow = acceptorPower.injectAEPower(acceptorPower.getAEMaxPower(), Actionable.MODULATE);

        helper.assertEquals(0.0, overflow, 0.000001, "Energy acceptor should accept all injected test power");
        helper.assertTrue(
                acceptorPower.getAECurrentPower() > 0,
                "Energy acceptor should contain injected power before the grid consumes it");
    }

    private static void drainGridPower(GameTestHelper helper, NetworkFixture network) {
        IEnergyGrid grid = energyGrid(helper, network);
        IAEPowerStorage acceptor = network.acceptorPower();
        double available = acceptor.getAECurrentPower();
        helper.assertTrue(available > 0, "Grid should have power available to drain during partial export");
        double extracted = grid.extractAEPower(available, Actionable.MODULATE, new HashSet<>());
        helper.assertEquals(available, extracted, 0.000001, "Grid should drain all available acceptor power");
        helper.assertEquals(
                0.0,
                network.acceptorPower().getAECurrentPower(),
                0.000001,
                "Energy acceptor should contain no power after the drain");
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
        helper.assertInventoryCount(
                DESTINATION_CHEST_LABEL,
                new ItemStack(Blocks.cobblestone),
                network.cobblestoneCount());
        assertStoredAmount(helper, network.drive().getStackInSlot(0), Blocks.cobblestone, 0);
    }

    private static void configureFilter(GameTestHelper helper, PartExportBus exportBus) {
        IAEStackInventory config = exportBus.getAEInventoryByName(StorageName.CONFIG);
        helper.assertNotNull(config, "Export bus config inventory should exist");
        config.putAEStackInSlot(0, itemStack(Blocks.cobblestone, 1));
    }

    private static int exportTimeout(long itemCount) {
        return Math.toIntExact((itemCount + 1) * TickRates.ExportBus.getMax());
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
            IPart powerProbe, long cobblestoneCount) {}

    @Desugar
    private record TransferSnapshot(long networkAmount, long destinationAmount, long droppedAmount) {}
}
