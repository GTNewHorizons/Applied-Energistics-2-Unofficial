package appeng.gametests.compatibility.appliedenergistics2_remoteio;

import net.minecraft.init.Blocks;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.core.AppEng;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "RIO")
public class RemoteIOChannelCompatibilityTests {

    private static final int CHANNEL_DEVICE_COUNT = 33;

    // Wool-only fixture scaffolds. Replace these assertions with real channel checks after exporting the wired
    // fixtures.
    @GameTest(template = "compatibility/remoteio/dense_cable", timeoutTicks = 20)
    public static void denseCableScaffoldIsComplete(GameTestHelper helper) {
        assertPlaceholder(helper, "controller");
        assertPlaceholder(helper, "controller_face");
        assertPlaceholder(helper, "remote_interface");
        assertPlaceholders(helper, "dense_cable_", 9);
        assertPlaceholders(helper, "channel_device_", CHANNEL_DEVICE_COUNT);
        helper.succeed();
    }

    @GameTest(template = "compatibility/remoteio/same_controller_face", timeoutTicks = 20)
    public static void sameControllerFaceScaffoldIsComplete(GameTestHelper helper) {
        assertDualInterfaceFixture(helper);
        helper.succeed();
    }

    @GameTest(template = "compatibility/remoteio/different_controller_faces", timeoutTicks = 20)
    public static void differentControllerFacesScaffoldIsComplete(GameTestHelper helper) {
        assertDualInterfaceFixture(helper);
        helper.succeed();
    }

    private static void assertDualInterfaceFixture(GameTestHelper helper) {
        assertPlaceholder(helper, "controller");
        assertPlaceholder(helper, "controller_face_a");
        assertPlaceholder(helper, "controller_face_b");
        assertPlaceholder(helper, "remote_interface_a");
        assertPlaceholder(helper, "remote_interface_b");
        assertPlaceholders(helper, "dense_cable_a_", 4);
        assertPlaceholders(helper, "dense_cable_b_", 4);
        assertPlaceholders(helper, "channel_device_a_", 16);
        assertPlaceholders(helper, "channel_device_b_", 17);
    }

    private static void assertPlaceholders(GameTestHelper helper, String labelPrefix, int count) {
        for (int i = 1; i <= count; i++) {
            assertPlaceholder(helper, labelPrefix + i);
        }
    }

    private static void assertPlaceholder(GameTestHelper helper, String label) {
        helper.assertBlockPresent(Blocks.wool, label);
    }
}
