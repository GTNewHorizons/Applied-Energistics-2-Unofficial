package appeng.gametests;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.pos;
import static appeng.gametests.AEGameTestHelpers.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers.Coord;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class NetworkCoreTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String DEVICE_A_LABEL = "device_a";
    private static final String DEVICE_B_LABEL = "device_b";
    private static final String BREAKABLE_CABLE_LABEL = "breakable_cable";
    private static final String TOGGLE_BUS_LABEL = "toggle_bus";
    private static final String REDSTONE_LABEL = "redstone";

    // Boots a controller-backed grid and gives channels to labelled devices.
    @GameTest(template = "network_core", timeoutTicks = 80)
    public static void networkBootsAndActivatesDevices(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        installCableLine(helper, 1, 10);
        IPart deviceA = placePart(helper, DEVICE_A_LABEL, ForgeDirection.UP, terminal());
        IPart deviceB = placePart(helper, DEVICE_B_LABEL, ForgeDirection.UP, storageMonitor());

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertActive(helper, deviceA, "Device A should receive a channel");
            assertActive(helper, deviceB, "Device B should receive a channel");
        }).thenSucceed();
    }

    // Splits the drive off the controller, then reconnects it without losing stored cell contents.
    @GameTest(template = "network_core", timeoutTicks = 120)
    public static void splitAndMergePreservesStorageVisibility(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        installCableLine(helper, 1, 10);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 100);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 100);
        }).thenExecute(() -> removeBlock(helper, BREAKABLE_CABLE_LABEL)).thenWaitUntil(30, () -> {
            assertActive(helper, controller.getProxy(), "Controller side should stay active after split");
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 0);
        }).thenExecute(() -> placeCable(helper, BREAKABLE_CABLE_LABEL)).thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should reactivate after merge");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should reactivate after merge");
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 100);
            assertStoredAmount(helper, drive.getStackInSlot(0), Blocks.cobblestone, 100);
        }).thenSucceed();
    }

    // A glass cable can carry eight channels, so the ninth device must be left without a channel.
    @GameTest(template = "network_core", timeoutTicks = 100)
    public static void channelLimitDeactivatesOverflowDevice(GameTestHelper helper) {
        getController(helper);
        getDrive(helper);
        installCableLine(helper, 1, 10);
        List<IPart> devices = new ArrayList<>();
        for (int xOffset = 1; xOffset <= 9; xOffset++) {
            devices.add(placePart(helper, offsetFromController(helper, xOffset, 0, 0), ForgeDirection.UP, terminal()));
        }

        helper.startSequence().thenWaitUntil(50, () -> {
            int activeDevices = countActive(devices);
            helper.assertEquals(8, activeDevices, "Only eight devices should receive channels on a glass cable");
            helper.assertEquals(1, devices.size() - activeDevices, "One device should overflow without a channel");
        }).thenSucceed();
    }

    // A toggle bus gates the downstream cable only while redstone is applied.
    @GameTest(template = "network_core", timeoutTicks = 140)
    public static void toggleBusGatesNetworkOnRedstone(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        installCableLine(helper, 1, 3);
        placeCable(helper, TOGGLE_BUS_LABEL);
        placePart(helper, TOGGLE_BUS_LABEL, ForgeDirection.EAST, toggleBus());
        installCableLine(helper, 5, 10);
        IPart upstreamDevice = placePart(helper, DEVICE_A_LABEL, ForgeDirection.UP, terminal());
        IPart downstreamDevice = placePart(helper, DEVICE_B_LABEL, ForgeDirection.UP, terminal());

        helper.startSequence().thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller side should boot without redstone");
            assertActive(helper, upstreamDevice, "Upstream device should stay active without redstone");
            assertInactive(helper, drive.getProxy(), "Drive should be gated while toggle bus is unpowered");
            assertInactive(helper, downstreamDevice, "Downstream device should be gated while toggle bus is unpowered");
        }).thenExecute(() -> setRedstoneInput(helper, 15)).thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller side should stay active with redstone");
            assertActive(helper, upstreamDevice, "Upstream device should stay active with redstone");
            assertActive(helper, drive.getProxy(), "Drive should become active when the toggle bus is powered");
            assertActive(
                    helper,
                    downstreamDevice,
                    "Downstream device should become active when the toggle bus is powered");
        }).thenExecute(() -> setRedstoneInput(helper, 0)).thenWaitUntil(40, () -> {
            assertActive(helper, controller.getProxy(), "Controller side should stay active after redstone is removed");
            assertActive(helper, upstreamDevice, "Upstream device should stay active after redstone is removed");
            assertInactive(helper, drive.getProxy(), "Drive should be gated again after redstone is removed");
            assertInactive(
                    helper,
                    downstreamDevice,
                    "Downstream device should be gated again after redstone is removed");
        }).thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return tile(helper, TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return tile(helper, TileDrive.class, DRIVE_LABEL);
    }

    private static void installCableLine(GameTestHelper helper, int firstXOffset, int lastXOffset) {
        for (int xOffset = firstXOffset; xOffset <= lastXOffset; xOffset++) {
            placeCable(helper, offsetFromController(helper, xOffset, 0, 0));
        }
    }

    private static TileCableBus placeCable(GameTestHelper helper, String label) {
        return placeCable(helper, pos(helper, label));
    }

    private static TileCableBus placeCable(GameTestHelper helper, Coord pos) {
        Block cableBusBlock = cableBusBlock();
        helper.setBlock(pos.x(), pos.y(), pos.z(), cableBusBlock);
        helper.assertBlockPresent(cableBusBlock, pos.x(), pos.y(), pos.z());
        TileCableBus cableBus = helper.assertTileEntityPresent(TileCableBus.class, pos.x(), pos.y(), pos.z());
        addPart(helper, cableBus, cableStack(), ForgeDirection.UNKNOWN);
        return cableBus;
    }

    private static IPart placePart(GameTestHelper helper, String label, ForgeDirection side, ItemStack stack) {
        return placePart(helper, pos(helper, label), side, stack);
    }

    private static IPart placePart(GameTestHelper helper, Coord pos, ForgeDirection side, ItemStack stack) {
        TestPos absolute = helper.absolute(pos.x(), pos.y(), pos.z());
        TileEntity tile = helper.getWorld().getTileEntity(absolute.x(), absolute.y(), absolute.z());
        helper.assertTrue(tile instanceof IPartHost, "Labelled cable position should contain an AE part host");
        assert tile instanceof IPartHost;
        IPartHost host = (IPartHost) tile;
        addPart(helper, host, stack, side);
        IPart part = host.getPart(side);
        helper.assertNotNull(part, "Placed part should be readable from its host");
        return part;
    }

    private static void addPart(GameTestHelper helper, IPartHost host, ItemStack stack, ForgeDirection side) {
        ForgeDirection placedSide = host.addPart(stack.copy(), side, null);
        helper.assertNotNull(placedSide, "AE part should be accepted by the cable bus");
    }

    private static void removeBlock(GameTestHelper helper, String label) {
        Coord pos = pos(helper, label);
        helper.destroyBlock(pos.x(), pos.y(), pos.z());
    }

    private static void setRedstoneInput(GameTestHelper helper, int strength) {
        AEGameTestHelpers.setRedstoneInput(helper, REDSTONE_LABEL, strength);
    }

    private static Coord offsetFromController(GameTestHelper helper, int x, int y, int z) {
        Coord controller = pos(helper, CONTROLLER_LABEL);
        return new Coord(controller.x() + x, controller.y() + y, controller.z() + z);
    }

    private static int countActive(List<IPart> parts) {
        int count = 0;
        for (IPart part : parts) {
            IGridNode node = part.getGridNode();
            if (node != null && node.isActive()) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack cableStack() {
        return AEApi.instance().definitions().parts().cableGlass().stack(AEColor.Transparent, 1);
    }

    private static ItemStack terminal() {
        return AEApi.instance().definitions().parts().terminal().maybeStack(1).get();
    }

    private static ItemStack storageMonitor() {
        return AEApi.instance().definitions().parts().storageMonitor().maybeStack(1).get();
    }

    private static ItemStack toggleBus() {
        return AEApi.instance().definitions().parts().toggleBus().maybeStack(1).get();
    }

    private static Block cableBusBlock() {
        return AEApi.instance().definitions().blocks().multiPart().maybeBlock().get();
    }

}
