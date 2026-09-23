package appeng.gametests.compatibility.appliedenergistics2_remoteio;

import static appeng.gametests.AEGameTestHelpers.assertActive;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.core.AppEng;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;
import remoteio.common.core.TransferType;
import remoteio.common.lib.DimensionalCoords;
import remoteio.common.lib.ModBlocks;
import remoteio.common.lib.ModItems;
import remoteio.common.tile.TileRemoteInterface;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "RIO")
public class RemoteIOChannelCompatibilityTests {

    private static final int CHANNEL_DEVICE_COUNT = 33;

    @GameTest(template = "compatibility/remoteio/dense_cable", timeoutTicks = 100)
    public static void denseCableCarriesThirtyTwoChannels(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        List<TileDrive> devices = channelDevices(helper, "channel_device_", CHANNEL_DEVICE_COUNT);
        connectRemoteInterface(helper, "remote_interface");

        assertActiveDevices(helper, controller, devices, 32);
    }

    @GameTest(template = "compatibility/remoteio/same_controller_face", timeoutTicks = 100)
    public static void sameControllerFaceSharesThirtyTwoChannels(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        List<TileDrive> devices = dualChannelDevices(helper);
        connectRemoteInterface(helper, "remote_interface_a");
        connectRemoteInterface(helper, "remote_interface_b");

        assertActiveDevices(helper, controller, devices, 32);
    }

    @GameTest(template = "compatibility/remoteio/different_controller_faces", timeoutTicks = 100)
    public static void differentControllerFacesHaveIndependentChannels(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        moveSecondRemoteInterfaceToNorthSide(helper);
        List<TileDrive> devices = dualChannelDevices(helper);
        connectRemoteInterface(helper, "remote_interface_a");
        connectRemoteInterface(helper, "remote_interface_b_north");

        assertActiveDevices(helper, controller, devices, 33);
    }

    private static List<TileDrive> dualChannelDevices(GameTestHelper helper) {
        List<TileDrive> devices = channelDevices(helper, "channel_device_a_", 16);
        devices.addAll(channelDevices(helper, "channel_device_b_", 17));
        return devices;
    }

    private static List<TileDrive> channelDevices(GameTestHelper helper, String labelPrefix, int count) {
        List<TileDrive> devices = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            devices.add(helper.assertTileEntityPresent(TileDrive.class, labelPrefix + i));
        }
        return devices;
    }

    private static void connectRemoteInterface(GameTestHelper helper, String label) {
        TileRemoteInterface remoteInterface = helper.assertTileEntityPresent(TileRemoteInterface.class, label);
        TestPos controller = helper.absolute("controller");
        remoteInterface.transferChips
                .setInventorySlotContents(0, new ItemStack(ModItems.transferChip, 1, TransferType.NETWORK_AE));
        remoteInterface.setRemotePosition(
                new DimensionalCoords(helper.getWorld(), controller.x(), controller.y(), controller.z()));
    }

    private static void moveSecondRemoteInterfaceToNorthSide(GameTestHelper helper) {
        helper.destroyBlock("remote_interface_b");
        helper.destroyBlock("remote_interface_b_north");
        helper.setBlock("channel_device_b_4", AEApi.instance().definitions().blocks().drive().maybeBlock().get());
        helper.setBlock("remote_interface_b_north", ModBlocks.remoteInterface);
    }

    private static void assertActiveDevices(GameTestHelper helper, TileController controller, List<TileDrive> devices,
            int expectedActive) {
        Runnable assertAllocation = () -> {
            assertActive(helper, controller.getProxy(), "Controller should become active");
            IGridNode controllerNode = controller.getProxy().getNode();
            int active = 0;
            for (TileDrive device : devices) {
                IGridNode deviceNode = device.getProxy().getNode();
                helper.assertNotNull(deviceNode, "Every ME drive should have a grid node");
                helper.assertSame(
                        controllerNode.getGrid(),
                        deviceNode.getGrid(),
                        "Every ME drive should join the controller grid through RemoteIO");
                if (deviceNode.isActive()) {
                    active++;
                }
            }
            helper.assertEquals(expectedActive, active, "Unexpected number of active devices");
        };
        TickCallbackHandle stableAllocation = helper
                .onEachTickDisabled("RemoteIO channel allocation remains stable", assertAllocation);

        helper.startSequence().thenWaitUntil("wait for RemoteIO channel allocation", 60, assertAllocation)
                .thenExecute("begin stable channel-allocation observation", stableAllocation::enable).thenIdle(20)
                .thenExecute("finish stable channel-allocation observation", stableAllocation::disable).thenSucceed();
    }

}
