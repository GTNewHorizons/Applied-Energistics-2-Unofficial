package appeng.gametests.network.p2p;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.part;
import static appeng.gametests.AEGameTestHelpers.setChestSlot;
import static appeng.gametests.AEGameTestHelpers.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
import appeng.me.GridAccessException;
import appeng.parts.p2p.PartP2PItems;
import appeng.parts.p2p.PartP2PRedstone;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.parts.p2p.PartP2PTunnelME;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;

@GameTestHolder(AppEng.MOD_ID)
public class P2PTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String INPUT_TUNNEL_HOST_LABEL = "input_tunnel_host";
    private static final String OUTPUT_TUNNEL_HOST_LABEL = "output_tunnel_host";
    private static final String SOURCE_CHEST_LABEL = "source_chest";
    private static final String SOURCE_INSERTER_LABEL = "source_inserter";
    private static final String DESTINATION_CHEST_LABEL = "destination_chest";
    private static final String REDSTONE_SOURCE_LABEL = "redstone_source";
    private static final String REDSTONE_PROBE_LABEL = "redstone_probe";
    private static final String REMOTE_STORAGE_LABEL = "remote_storage";

    private static final long ITEM_FREQUENCY = 101L;
    private static final long REDSTONE_FREQUENCY = 202L;
    private static final long ME_FREQUENCY = 303L;

    // P1: a real hopper supplies the input tunnel, and conservation is checked on every observed tick.
    @GameTest(template = "p2p_tunnels", timeoutTicks = 160)
    public static void itemP2PMovesItemsWithoutDuplication(GameTestHelper helper) {
        P2PFixture fixture = getFixture(helper);
        PartP2PItems input = inputTunnel(helper, PartP2PItems.class);
        PartP2PItems output = outputTunnel(helper, PartP2PItems.class);
        setChestSlot(fixture.sourceChest, 0, Blocks.cobblestone, 1);
        ContinuousInvariant itemConservation = itemConservationInvariant(helper, fixture, 1);
        itemConservation.enable();

        helper.startSequence().thenWaitUntil("wait for the item P2P pair to become active and linked", 60, () -> {
            assertCarrierActive(helper, fixture.controller);
            assertLinkedPair(helper, input, output, ITEM_FREQUENCY);
        }).thenWaitUntil("wait for the supplied item to reach only the destination inventory", 80, () -> {
            assertInventoryAmount(helper, fixture.sourceChest, Blocks.cobblestone, 0, SOURCE_CHEST_LABEL);
            assertInventoryAmount(helper, fixture.sourceInserter, Blocks.cobblestone, 0, SOURCE_INSERTER_LABEL);
            assertInventoryAmount(helper, fixture.destinationChest, Blocks.cobblestone, 1, DESTINATION_CHEST_LABEL);
        }).thenExecute("finish item-conservation observation", itemConservation::disable).thenSucceed();
    }

    // P1: the outer ME connection must expose storage that is physically present only behind the output tunnel.
    @GameTest(template = "p2p_tunnels", timeoutTicks = 180)
    public static void meP2PCarriesRemoteStorageChannel(GameTestHelper helper) {
        P2PFixture fixture = getFixture(helper);
        PartP2PTunnelME input = inputTunnel(helper, PartP2PTunnelME.class);
        PartP2PTunnelME output = outputTunnel(helper, PartP2PTunnelME.class);
        TileDrive remoteDrive = tile(helper, TileDrive.class, REMOTE_STORAGE_LABEL);
        ItemStack remoteCell = cell1k();
        insertItems(helper, remoteCell, Blocks.cobblestone, 64);
        remoteDrive.setInventorySlotContents(0, remoteCell);

        helper.startSequence()
                .thenWaitUntil("wait for the ME P2P pair to create its outer grid connection", 100, () -> {
                    assertCarrierActive(helper, fixture.controller);
                    assertLinkedPair(helper, input, output, ME_FREQUENCY);
                    IGridNode inputOuter = input.getExternalFacingNode();
                    IGridNode outputOuter = output.getExternalFacingNode();
                    helper.assertNotNull(inputOuter, "Input ME tunnel should expose an outer grid node");
                    helper.assertNotNull(outputOuter, "Output ME tunnel should expose an outer grid node");
                    helper.assertTrue(inputOuter.isActive(), "Input-side main network should be powered and active");
                    helper.assertTrue(
                            outputOuter.isActive(),
                            "Remote storage side should be powered through the ME tunnel");
                    helper.assertTrue(
                            hasConnection(inputOuter, outputOuter),
                            "ME P2P outer nodes should have the tunnel-created connection; "
                                    + describePair(input, output));
                    assertNetworkStoredAmount(helper, inputOuter, Blocks.cobblestone, 64);
                }).thenSucceed();
    }

    // P1: all three links are authored in the exported cable-bus NBT; the test performs no binding setup.
    @GameTest(template = "p2p_tunnels", timeoutTicks = 100)
    public static void frequencyPersistsThroughTemplateNbt(GameTestHelper helper) {
        P2PFixture fixture = getFixture(helper);
        PartP2PItems itemInput = inputTunnel(helper, PartP2PItems.class);
        PartP2PItems itemOutput = outputTunnel(helper, PartP2PItems.class);
        PartP2PRedstone redstoneInput = inputTunnel(helper, PartP2PRedstone.class);
        PartP2PRedstone redstoneOutput = outputTunnel(helper, PartP2PRedstone.class);
        PartP2PTunnelME meInput = inputTunnel(helper, PartP2PTunnelME.class);
        PartP2PTunnelME meOutput = outputTunnel(helper, PartP2PTunnelME.class);

        helper.startSequence().thenWaitUntil("wait for all NBT-authored P2P frequencies to reconnect", 80, () -> {
            assertCarrierActive(helper, fixture.controller);
            assertLinkedPair(helper, itemInput, itemOutput, ITEM_FREQUENCY);
            assertLinkedPair(helper, redstoneInput, redstoneOutput, REDSTONE_FREQUENCY);
            assertLinkedPair(helper, meInput, meOutput, ME_FREQUENCY);
        }).thenSucceed();
    }

    // P2: the output must track both edges of the input signal, including returning to zero.
    @GameTest(template = "p2p_tunnels", timeoutTicks = 160)
    public static void redstoneP2PMirrorsSignal(GameTestHelper helper) {
        P2PFixture fixture = getFixture(helper);
        PartP2PRedstone input = inputTunnel(helper, PartP2PRedstone.class);
        PartP2PRedstone output = outputTunnel(helper, PartP2PRedstone.class);
        ContinuousInvariant unpoweredOutputStaysLow = continuousInvariant(
                helper,
                "an unpowered redstone P2P input must not produce output power",
                () -> {
                    assertCarrierActive(helper, fixture.controller);
                    assertLinkedPair(helper, input, output, REDSTONE_FREQUENCY);
                    assertRedstonePower(helper, 0);
                });

        helper.startSequence().thenWaitUntil("wait for the linked redstone P2P pair to settle low", 60, () -> {
            assertCarrierActive(helper, fixture.controller);
            assertLinkedPair(helper, input, output, REDSTONE_FREQUENCY);
            assertRedstonePower(helper, 0);
        }).thenExecute("begin unpowered-output invariant", unpoweredOutputStaysLow::enable).thenIdle(5)
                .thenExecute("power the redstone P2P input", () -> {
                    unpoweredOutputStaysLow.disable();
                    setRedstoneInput(helper, 15);
                }).thenWaitUntil("wait for the redstone P2P output to become powered", 40, () -> {
                    assertLinkedPair(helper, input, output, REDSTONE_FREQUENCY);
                    assertRedstonePower(helper, 15);
                }).thenExecute("remove power from the redstone P2P input", () -> setRedstoneInput(helper, 0))
                .thenWaitUntil("wait for the redstone P2P output to return to zero", 40, () -> {
                    assertCarrierActive(helper, fixture.controller);
                    assertLinkedPair(helper, input, output, REDSTONE_FREQUENCY);
                    assertRedstonePower(helper, 0);
                }).thenExecute("begin restored-low invariant", unpoweredOutputStaysLow::enable).thenIdle(5)
                .thenExecute("finish restored-low observation", unpoweredOutputStaysLow::disable).thenSucceed();
    }

    // P2: after both exported tunnels are explicitly unbound, no destination mutation is allowed for the window.
    @GameTest(template = "p2p_tunnels", timeoutTicks = 120)
    public static void unboundTunnelDoesNotTransfer(GameTestHelper helper) {
        P2PFixture fixture = getFixture(helper);
        PartP2PItems input = (PartP2PItems) inputTunnel(helper, PartP2PItems.class).unbind(null);
        PartP2PItems output = (PartP2PItems) outputTunnel(helper, PartP2PItems.class).unbind(null);
        setChestSlot(fixture.sourceChest, 0, Blocks.cobblestone, 1);
        ContinuousInvariant noUnboundTransfer = continuousInvariant(
                helper,
                "unbound item tunnels must neither transfer nor duplicate the supplied item",
                () -> {
                    assertUnboundPairOnCarrier(helper, fixture.controller, input, output);
                    assertInventoryAmount(
                            helper,
                            fixture.destinationChest,
                            Blocks.cobblestone,
                            0,
                            DESTINATION_CHEST_LABEL);
                    assertInventoryTotal(helper, fixture, Blocks.cobblestone, 1);
                });

        helper.startSequence()
                .thenWaitUntil(
                        "wait for both item tunnels to be active and unbound",
                        60,
                        () -> assertUnboundPairOnCarrier(helper, fixture.controller, input, output))
                .thenExecute("begin unbound-transfer invariant", noUnboundTransfer::enable).thenIdle(40)
                .thenExecute("finish unbound-transfer observation", noUnboundTransfer::disable)
                .thenExecute("assert destination remained untouched for the full observation window", () -> {
                    assertInventoryAmount(
                            helper,
                            fixture.destinationChest,
                            Blocks.cobblestone,
                            0,
                            DESTINATION_CHEST_LABEL);
                    assertInventoryTotal(helper, fixture, Blocks.cobblestone, 1);
                }).thenSucceed();
    }

    private static P2PFixture getFixture(GameTestHelper helper) {
        return new P2PFixture(
                tile(helper, TileController.class, CONTROLLER_LABEL),
                tile(helper, TileEntityChest.class, SOURCE_CHEST_LABEL),
                tile(helper, TileEntityHopper.class, SOURCE_INSERTER_LABEL),
                tile(helper, TileEntityChest.class, DESTINATION_CHEST_LABEL));
    }

    private static <T extends PartP2PTunnel> T inputTunnel(GameTestHelper helper, Class<T> type) {
        return part(helper, INPUT_TUNNEL_HOST_LABEL, type);
    }

    private static <T extends PartP2PTunnel> T outputTunnel(GameTestHelper helper, Class<T> type) {
        return part(helper, OUTPUT_TUNNEL_HOST_LABEL, type);
    }

    private static void assertCarrierActive(GameTestHelper helper, TileController controller) {
        assertActive(helper, controller.getProxy(), "P2P carrier controller should be active");
    }

    private static void assertUnboundPairOnCarrier(GameTestHelper helper, TileController controller, PartP2PItems input,
            PartP2PItems output) {
        assertCarrierActive(helper, controller);
        assertUnbound(helper, input, "input");
        assertUnbound(helper, output, "output");
        assertOnCarrierGrid(helper, controller, input, output);
    }

    private static void assertOnCarrierGrid(GameTestHelper helper, TileController controller, PartP2PTunnel<?> input,
            PartP2PTunnel<?> output) {
        IGridNode carrierNode = controller.getProxy().getNode();
        helper.assertSame(
                carrierNode.getGrid(),
                input.getGridNode().getGrid(),
                "Unbound P2P input should remain on the controller's carrier grid");
        helper.assertSame(
                carrierNode.getGrid(),
                output.getGridNode().getGrid(),
                "Unbound P2P output should remain on the controller's carrier grid");
    }

    private static <T extends PartP2PTunnel> void assertLinkedPair(GameTestHelper helper, T input, T output,
            long expectedFrequency) {
        assertActive(helper, input, "P2P input should be active; " + describePair(input, output));
        assertActive(helper, output, "P2P output should be active; " + describePair(input, output));
        helper.assertFalse(
                input.isOutput(),
                "NBT-authored input should remain an input; " + describePair(input, output));
        helper.assertTrue(
                output.isOutput(),
                "NBT-authored output should remain an output; " + describePair(input, output));
        helper.assertEquals(
                expectedFrequency,
                input.getFrequency(),
                "Input frequency should match exported template NBT; " + describePair(input, output));
        helper.assertEquals(
                expectedFrequency,
                output.getFrequency(),
                "Output frequency should match exported template NBT; " + describePair(input, output));
        helper.assertSame(
                input,
                output.getInput(),
                "Output should resolve its exported input; " + describePair(input, output));

        try {
            boolean foundOutput = false;
            for (Object candidate : input.getOutputs()) {
                if (candidate == output) {
                    foundOutput = true;
                    break;
                }
            }
            helper.assertTrue(
                    foundOutput,
                    "Input should enumerate its exported output; " + describePair(input, output));
        } catch (GridAccessException e) {
            throw new AssertionError("P2P cache should be accessible; " + describePair(input, output), e);
        }
    }

    private static void assertUnbound(GameTestHelper helper, PartP2PItems tunnel, String role) {
        assertActive(helper, tunnel, "Unbound item tunnel should remain active on the carrier network; role=" + role);
        helper.assertEquals(0L, tunnel.getFrequency(), "Unbound item tunnel frequency should be zero; role=" + role);
        helper.assertFalse(tunnel.isOutput(), "Unbound tunnel should be reset to input mode; role=" + role);
        helper.assertNull(tunnel.getInput(), "Unbound tunnel should not resolve an input; role=" + role);
    }

    private static ContinuousInvariant itemConservationInvariant(GameTestHelper helper, P2PFixture fixture,
            long expectedTotal) {
        return continuousInvariant(
                helper,
                "item P2P transport must conserve the supplied stack on every tick",
                () -> assertInventoryTotal(helper, fixture, Blocks.cobblestone, expectedTotal));
    }

    private static void assertInventoryTotal(GameTestHelper helper, P2PFixture fixture, Block block,
            long expectedTotal) {
        long source = inventoryAmount(fixture.sourceChest, block);
        long inserter = inventoryAmount(fixture.sourceInserter, block);
        long destination = inventoryAmount(fixture.destinationChest, block);
        helper.assertEquals(
                expectedTotal,
                source + inserter + destination,
                "Item total across source_chest, source_inserter, and destination_chest should be conserved"
                        + "; source="
                        + source
                        + ", inserter="
                        + inserter
                        + ", destination="
                        + destination);
        helper.assertTrue(
                destination <= expectedTotal,
                "Destination must never contain duplicated items; destination=" + destination
                        + ", supplied="
                        + expectedTotal);
    }

    private static void assertInventoryAmount(GameTestHelper helper, IInventory inventory, Block block, long expected,
            String role) {
        long actual = inventoryAmount(inventory, block);
        helper.assertEquals(
                expected,
                actual,
                "Inventory role '" + role + "' should contain the expected " + blockName(block) + " amount");
    }

    private static long inventoryAmount(IInventory inventory, Block block) {
        long amount = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Item.getItemFromBlock(block)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    private static boolean hasConnection(IGridNode from, IGridNode to) {
        for (IGridConnection connection : from.getConnections()) {
            if (connection.getOtherSide(from) == to) {
                return true;
            }
        }
        return false;
    }

    private static void setRedstoneInput(GameTestHelper helper, int strength) {
        TestPos source = helper.pos(REDSTONE_SOURCE_LABEL);
        helper.setRedstoneInput(source.x(), source.y(), source.z(), strength);
    }

    private static void assertRedstonePower(GameTestHelper helper, int expectedPower) {
        TestPos probe = helper.pos(REDSTONE_PROBE_LABEL);
        int actualPower = helper.getWorld().getStrongestIndirectPower(
                helper.absolute(probe.x(), probe.y(), probe.z()).x(),
                helper.absolute(probe.x(), probe.y(), probe.z()).y(),
                helper.absolute(probe.x(), probe.y(), probe.z()).z());
        helper.assertEquals(
                expectedPower,
                actualPower,
                "Redstone probe should mirror the P2P input; source=" + REDSTONE_SOURCE_LABEL
                        + ", probe="
                        + REDSTONE_PROBE_LABEL);
    }

    private static String describePair(PartP2PTunnel input, PartP2PTunnel output) {
        return "input[type=" + input.getClass().getSimpleName()
                + ", frequency="
                + input.getFrequency()
                + ", output="
                + input.isOutput()
                + ", active="
                + isActive(input)
                + "], output[type="
                + output.getClass().getSimpleName()
                + ", frequency="
                + output.getFrequency()
                + ", output="
                + output.isOutput()
                + ", active="
                + isActive(output)
                + ']';
    }

    private static boolean isActive(IPart part) {
        return part.getGridNode() != null && part.getGridNode().isActive();
    }

    private static String blockName(Block block) {
        Object name = Block.blockRegistry.getNameForObject(block);
        return name == null ? block.getLocalizedName() : name.toString();
    }

    private static final class P2PFixture {

        private final TileController controller;
        private final TileEntityChest sourceChest;
        private final TileEntityHopper sourceInserter;
        private final TileEntityChest destinationChest;

        private P2PFixture(TileController controller, TileEntityChest sourceChest, TileEntityHopper sourceInserter,
                TileEntityChest destinationChest) {
            this.controller = controller;
            this.sourceChest = sourceChest;
            this.sourceInserter = sourceInserter;
            this.destinationChest = destinationChest;
        }
    }
}
