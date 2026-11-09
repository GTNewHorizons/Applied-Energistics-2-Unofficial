package appeng.gametests.network.p2p;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.part;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.core.AppEng;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartLevelEmitter;
import appeng.parts.p2p.PartP2PGT5Power;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileCreativeEnergyController;
import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;

@GameTestHolder(value = AppEng.MOD_ID, requiredMods = "gregtech")
public class GTEUP2PTests {

    private static final ForgeDirection[] LOAD_SIDES = { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.UP,
            ForgeDirection.DOWN };

    @GameTest(template = "gt_eu_p2p_no_channel", timeoutTicks = 120)
    public static void outputWithoutChannelDoesNotInjectEU(GameTestHelper helper) {
        TileCreativeEnergyController controller = helper
                .assertTileEntityPresent(TileCreativeEnergyController.class, "controller");
        PartP2PGT5Power input = part(helper, "input_tunnel_host", PartP2PGT5Power.class);
        PartP2PGT5Power output = part(helper, "output_tunnel_host", PartP2PGT5Power.class);
        TileEntity receiver = helper.assertTileEntityPresent(TileEntity.class, "energy_hatch");
        helper.assertTrue(receiver instanceof IBasicEnergyContainer, "Receiver should be a GT energy container");
        IBasicEnergyContainer hatch = (IBasicEnergyContainer) receiver;

        helper.startSequence().thenWaitUntil("wait for the input and channel load to become active", 80, () -> {
            assertActive(helper, controller.getProxy(), "Carrier controller should be active");
            assertActive(helper, input, "GT EU P2P input should have a channel");
            assertChannelLoad(helper, "channel_load_1");
            assertChannelLoad(helper, "channel_load_2");
            assertInactive(helper, output, "GT EU P2P output should have no channel behind eight level emitters");
            helper.assertSame(
                    input.getGridNode().getGrid(),
                    output.getGridNode().getGrid(),
                    "Input and channel-starved output should remain on the same carrier grid");
            helper.assertFalse(input.isOutput(), "GT EU P2P input should remain in input mode");
            helper.assertTrue(output.isOutput(), "GT EU P2P output should remain in output mode");
            helper.assertTrue(input.getFrequency() != 0, "GT EU P2P pair should have a nonzero frequency");
            helper.assertEquals(input.getFrequency(), output.getFrequency(), "GT EU P2P pair should stay linked");
            helper.assertEquals(ForgeDirection.EAST, output.getSide(), "GT EU P2P output should face the hatch");
            assertInputEnumeratesOutput(input, output);
            helper.assertEquals(32L, hatch.getInputVoltage(), "Receiver should accept LV voltage");
            helper.assertTrue(
                    hatch.inputEnergyFrom(ForgeDirection.WEST),
                    "Energy Hatch should accept EU from the west");
            helper.assertEquals(0L, hatch.getStoredEU(), "Energy Hatch should start empty");
        }).thenExecute("inject one LV amp into the installed GT EU P2P input", () -> {
            long acceptedAmperes = input.injectEnergyUnits(32L, 1L);
            helper.assertEquals(0L, acceptedAmperes, "Input should reject EU when its only output has no channel");
            helper.assertEquals(0L, hatch.getStoredEU(), "Channel-starved output should deliver no EU to the hatch");
        }).thenSucceed();
    }

    private static void assertChannelLoad(GameTestHelper helper, String label) {
        TileCableBus host = helper.assertTileEntityPresent(TileCableBus.class, label);
        for (ForgeDirection side : LOAD_SIDES) {
            helper.assertTrue(
                    host.getPart(side) instanceof PartLevelEmitter,
                    label + " should have a level emitter on " + side);
            assertActive(helper, host.getPart(side), label + " level emitter should consume a channel on " + side);
        }
    }

    private static void assertInputEnumeratesOutput(PartP2PGT5Power input, PartP2PGT5Power output) {
        try {
            for (PartP2PGT5Power candidate : input.getOutputs()) {
                if (candidate == output) {
                    return;
                }
            }
        } catch (GridAccessException e) {
            throw new AssertionError("GT EU P2P input should access the carrier P2P cache", e);
        }
        throw new AssertionError("GT EU P2P input should enumerate the channel-starved output");
    }
}
