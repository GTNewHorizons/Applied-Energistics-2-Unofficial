package appeng.gametests.network;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
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
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
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
    private static final String[] FULL_CABLE_LINE = { "cable_1", "cable_2", "cable_3", "cable_4", "cable_5", "cable_6",
            "cable_7", "cable_8", "cable_9", "cable_10" };
    private static final String[] UPSTREAM_CABLE_LINE = { "cable_1", "cable_2", "cable_3" };
    private static final String[] DOWNSTREAM_CABLE_LINE = { "cable_5", "cable_6", "cable_7", "cable_8", "cable_9",
            "cable_10" };
    private static final String[] CHANNEL_DEVICE_LABELS = { "channel_device_1", "channel_device_2", "channel_device_3",
            "channel_device_4", "channel_device_5", "channel_device_6", "channel_device_7", "channel_device_8",
            "channel_device_9" };

    // Boots a controller-backed grid and gives channels to labelled devices.
    @GameTest(template = "network_core", timeoutTicks = 80)
    public static void networkBootsAndActivatesDevices(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        installCableLine(helper, FULL_CABLE_LINE);
        IPart deviceA = placePart(helper, DEVICE_A_LABEL, ForgeDirection.UP, terminal());
        IPart deviceB = placePart(helper, DEVICE_B_LABEL, ForgeDirection.UP, storageMonitor());

        helper.startSequence()
                .thenWaitUntil("wait for controller, drive, and both labelled devices to activate", 40, () -> {
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
        installCableLine(helper, FULL_CABLE_LINE);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 100);
        drive.setInventorySlotContents(0, driveCell);

        helper.startSequence().thenWaitUntil("wait for connected drive contents to become network-visible", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
            assertActive(helper, drive.getProxy(), "Drive grid proxy should become active");
            assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 100);
        }).thenExecute("remove the breakable cable", () -> removeBlock(helper, BREAKABLE_CABLE_LABEL))
                .thenWaitUntil("wait for split controller side to lose drive visibility", 30, () -> {
                    assertActive(helper, controller.getProxy(), "Controller side should stay active after split");
                    assertNetworkStoredAmount(helper, controller, Blocks.cobblestone, 0);
                }).thenExecute("restore the breakable cable", () -> placeCable(helper, BREAKABLE_CABLE_LABEL))
                .thenWaitUntil("wait for merged network to restore drive visibility without data loss", 40, () -> {
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
        installCableLine(helper, FULL_CABLE_LINE);
        List<IPart> devices = new ArrayList<>();
        for (String deviceLabel : CHANNEL_DEVICE_LABELS) {
            devices.add(placePart(helper, deviceLabel, ForgeDirection.UP, terminal()));
        }

        helper.startSequence()
                .thenWaitUntil("wait for glass cable to allocate eight channels and reject the ninth", 50, () -> {
                    int activeDevices = countActive(devices);
                    helper.assertEquals(
                            8,
                            activeDevices,
                            "Only eight devices should receive channels on a glass cable");
                    helper.assertEquals(
                            1,
                            devices.size() - activeDevices,
                            "One device should overflow without a channel");
                }).thenSucceed();
    }

    // A toggle bus gates the downstream cable only while redstone is applied.
    @GameTest(template = "network_core", timeoutTicks = 140)
    public static void toggleBusGatesNetworkOnRedstone(GameTestHelper helper) {
        TileController controller = getController(helper);
        TileDrive drive = getDrive(helper);
        installCableLine(helper, UPSTREAM_CABLE_LINE);
        placeCable(helper, TOGGLE_BUS_LABEL);
        placePart(helper, TOGGLE_BUS_LABEL, directionBetween(helper, TOGGLE_BUS_LABEL, DEVICE_B_LABEL), toggleBus());
        installCableLine(helper, DOWNSTREAM_CABLE_LINE);
        IPart upstreamDevice = placePart(helper, DEVICE_A_LABEL, ForgeDirection.UP, terminal());
        IPart downstreamDevice = placePart(helper, DEVICE_B_LABEL, ForgeDirection.UP, terminal());
        ContinuousInvariant unpoweredToggleBusGatesDownstream = continuousInvariant(
                helper,
                "unpowered toggle bus must keep only the upstream network active",
                () -> {
                    assertActive(helper, controller.getProxy(), "Controller side should remain active");
                    assertActive(helper, upstreamDevice, "Upstream device should remain active");
                    assertInactive(helper, drive.getProxy(), "Drive should remain gated");
                    assertInactive(helper, downstreamDevice, "Downstream device should remain gated");
                });

        helper.startSequence().thenWaitUntil("wait for initial unpowered toggle-bus state", 40, () -> {
            assertActive(helper, controller.getProxy(), "Controller side should boot without redstone");
            assertActive(helper, upstreamDevice, "Upstream device should stay active without redstone");
            assertInactive(helper, drive.getProxy(), "Drive should be gated while toggle bus is unpowered");
            assertInactive(helper, downstreamDevice, "Downstream device should be gated while toggle bus is unpowered");
        }).thenExecute("begin unpowered gating invariant", unpoweredToggleBusGatesDownstream::enable).thenIdle(5)
                .thenExecute("power toggle bus", () -> {
                    unpoweredToggleBusGatesDownstream.disable();
                    setRedstoneInput(helper, 15);
                }).thenWaitUntil("wait for powered downstream activation", 40, () -> {
                    assertActive(helper, controller.getProxy(), "Controller side should stay active with redstone");
                    assertActive(helper, upstreamDevice, "Upstream device should stay active with redstone");
                    assertActive(helper, drive.getProxy(), "Drive should become active when the toggle bus is powered");
                    assertActive(
                            helper,
                            downstreamDevice,
                            "Downstream device should become active when the toggle bus is powered");
                }).thenExecute("remove toggle-bus power", () -> setRedstoneInput(helper, 0))
                .thenWaitUntil("wait for downstream network to become gated again", 40, () -> {
                    assertActive(
                            helper,
                            controller.getProxy(),
                            "Controller side should stay active after redstone is removed");
                    assertActive(
                            helper,
                            upstreamDevice,
                            "Upstream device should stay active after redstone is removed");
                    assertInactive(helper, drive.getProxy(), "Drive should be gated again after redstone is removed");
                    assertInactive(
                            helper,
                            downstreamDevice,
                            "Downstream device should be gated again after redstone is removed");
                }).thenExecute("begin restored gating invariant", unpoweredToggleBusGatesDownstream::enable).thenIdle(5)
                .thenExecute("finish restored gating observation", unpoweredToggleBusGatesDownstream::disable)
                .thenSucceed();
    }

    private static TileController getController(GameTestHelper helper) {
        return tile(helper, TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return tile(helper, TileDrive.class, DRIVE_LABEL);
    }

    private static void installCableLine(GameTestHelper helper, String... cableRoles) {
        for (String cableRole : cableRoles) {
            placeCable(helper, cableRole);
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
        TileEntity tile = helper.assertTileEntityPresent(pos.x(), pos.y(), pos.z());
        helper.assertTrue(tile instanceof IPartHost, "Labelled cable position should contain an AE part host");
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
