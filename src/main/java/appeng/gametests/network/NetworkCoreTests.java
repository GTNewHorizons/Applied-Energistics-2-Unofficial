package appeng.gametests.network;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.continuousInvariant;
import static appeng.gametests.AEGameTestHelpers.fluidStack;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;

import com.google.common.collect.ImmutableCollection;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.TestPos;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEColor;
import appeng.core.AppEng;
import appeng.gametests.AEGameTestHelpers.ContinuousInvariant;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.GridAccessException;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.misc.PartPatternRepeater;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;

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
    private static final String[] CHANNEL_LIMIT_CABLE_LINE = { "cable_1", "cable_2", "cable_3", "cable_4", "cable_5",
            "cable_6", "cable_7", "cable_8", "cable_9" };
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
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

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
        TileController controller = getController(helper);
        installCableLine(helper, CHANNEL_LIMIT_CABLE_LINE);
        List<IPart> devices = new ArrayList<>();
        for (String deviceLabel : CHANNEL_DEVICE_LABELS) {
            devices.add(placePart(helper, deviceLabel, ForgeDirection.UP, terminal()));
        }

        helper.startSequence()
                .thenWaitUntil("wait for glass cable to allocate eight channels and reject the ninth", 50, () -> {
                    assertActive(helper, controller.getProxy(), "Controller grid proxy should become active");
                    int activeDevices = countActiveOnGrid(helper, controller.getProxy().getNode(), devices);
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

    // A Pattern Repeater should make remote Tunnel Pattern definitions available to local processing patterns.
    @GameTest(template = "network_core", timeoutTicks = 200)
    public static void patternRepeaterSharesTunnelPatternDefinitions(GameTestHelper helper) {
        TileController sourceController = getController(helper);
        TileController targetController = replaceDriveWithController(helper);
        placeCable(helper, "cable_1", AEColor.Red);
        TileInterface sourceInterface = placeInterface(helper, "cable_2");

        UUID tunnelUuid = UUID.randomUUID();
        ItemStack tunnelPattern = encodedTunnelPattern(tunnelUuid, fluidStack(FluidRegistry.WATER, 1_000));
        ItemStack tunnelReference = tunnelPattern.copy();
        tunnelReference.stackSize = 2;
        ItemStack referencingPattern = encodedProcessingPattern(tunnelReference, Blocks.stone, 1);

        TileCableBus accessorHost = placeCable(helper, "cable_3", AEColor.Red);
        TileCableBus providerHost = placeCable(helper, "cable_4", AEColor.Blue);
        for (int cable = 5; cable <= 9; cable++) {
            placeCable(helper, "cable_" + cable, AEColor.Blue);
        }
        TileInterface targetInterface = placeInterface(helper, "cable_10");

        PartPatternRepeater accessor = (PartPatternRepeater) placePart(
                helper,
                accessorHost,
                ForgeDirection.EAST,
                patternRepeater());
        PartPatternRepeater provider = (PartPatternRepeater) placePart(
                helper,
                providerHost,
                ForgeDirection.WEST,
                patternRepeater());
        setProvider(provider);

        helper.startSequence().thenWaitUntil("wait for isolated Pattern Repeater networks to activate", 80, () -> {
            assertActive(helper, sourceController.getProxy(), "Source controller grid should become active");
            assertActive(helper, targetController.getProxy(), "Target controller grid should become active");
            assertActive(helper, sourceInterface.getProxy(), "Source interface should receive a channel");
            assertActive(helper, targetInterface.getProxy(), "Target interface should receive a channel");
            assertActive(helper, accessor, "Pattern Repeater accessor should receive a channel");
            assertActive(helper, provider, "Pattern Repeater provider should receive a channel");
            helper.assertTrue(provider.isProvider(), "Target-side Pattern Repeater should be in provider mode");
            helper.assertSame(provider, accessor.getPair(), "Pattern Repeater accessor should find its provider");
            helper.assertTrue(
                    sourceController.getProxy().getNode().getGrid() != targetController.getProxy().getNode().getGrid(),
                    "Pattern Repeaters should bridge two separate networks");
        }).thenExecute("install patterns after the repeater's initial snapshot", () -> {
            // Reproduce the load-order window where the repeater has not registered its change listener yet.
            craftingCache(sourceController).removePostPatternChangeListeners(accessor);
            InventoryHelper.setSlot(sourceInterface.getInterfaceDuality().getPatterns(), 0, tunnelPattern);
            InventoryHelper.setSlot(targetInterface.getInterfaceDuality().getPatterns(), 0, referencingPattern);
            helper.assertNotNull(
                    craftingCache(targetController).getInputOnlyPattern(tunnelUuid),
                    "Target network should receive the Tunnel Pattern definition");
            ICraftingPatternDetails details = firstPattern(helper, targetController, Blocks.stone);
            assertFluidInputs(helper, details.getAEInputs());
            assertFluidInputs(helper, details.getCondensedAEInputs());
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
        TickCallbackHandle unpoweredToggleBusGatesDownstream = helper
                .onEachTickDisabled("unpowered toggle bus gates downstream", () -> {
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
        return helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
    }

    private static TileDrive getDrive(GameTestHelper helper) {
        return helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
    }

    private static TileController replaceDriveWithController(GameTestHelper helper) {
        Block controller = AEApi.instance().definitions().blocks().creativeEnergyController().maybeBlock().get();
        helper.setBlock(DRIVE_LABEL, controller);
        helper.assertBlockPresent(controller, DRIVE_LABEL);
        return helper.assertTileEntityPresent(TileController.class, DRIVE_LABEL);
    }

    private static TileInterface placeInterface(GameTestHelper helper, String label) {
        Block blockInterface = AEApi.instance().definitions().blocks().iface().maybeBlock().get();
        helper.setBlock(label, blockInterface);
        helper.assertBlockPresent(blockInterface, label);
        return helper.assertTileEntityPresent(TileInterface.class, label);
    }

    private static void installCableLine(GameTestHelper helper, String... cableRoles) {
        for (String cableRole : cableRoles) {
            placeCable(helper, cableRole);
        }
    }

    private static TileCableBus placeCable(GameTestHelper helper, String label) {
        return placeCable(helper, label, AEColor.Transparent);
    }

    private static TileCableBus placeCable(GameTestHelper helper, String label, AEColor color) {
        Block cableBusBlock = cableBusBlock();
        helper.setBlock(label, cableBusBlock);
        helper.assertBlockPresent(cableBusBlock, label);
        TileCableBus cableBus = helper.assertTileEntityPresent(TileCableBus.class, label);
        addPart(helper, cableBus, cableStack(color), ForgeDirection.UNKNOWN);
        return cableBus;
    }

    private static IPart placePart(GameTestHelper helper, String label, ForgeDirection side, ItemStack stack) {
        TileEntity tile = helper.assertTileEntityPresent(label);
        helper.assertTrue(tile instanceof IPartHost, "Labelled cable position should contain an AE part host");
        return placePart(helper, (IPartHost) tile, side, stack);
    }

    private static IPart placePart(GameTestHelper helper, IPartHost host, ForgeDirection side, ItemStack stack) {
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
        helper.destroyBlock(label);
    }

    private static void setRedstoneInput(GameTestHelper helper, int strength) {
        helper.setRedstoneInput(REDSTONE_LABEL, strength);
    }

    private static ForgeDirection directionBetween(GameTestHelper helper, String fromRole, String toRole) {
        TestPos from = helper.pos(fromRole);
        TestPos to = helper.pos(toRole);
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

    private static int countActiveOnGrid(GameTestHelper helper, IGridNode expectedGridNode, List<IPart> parts) {
        int count = 0;
        for (IPart part : parts) {
            IGridNode node = part.getGridNode();
            helper.assertNotNull(node, "Every channel-limited device should have a grid node");
            helper.assertSame(
                    expectedGridNode.getGrid(),
                    node.getGrid(),
                    "Every channel-limited device should join the controller grid");
            if (node.isActive()) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack cableStack() {
        return cableStack(AEColor.Transparent);
    }

    private static ItemStack cableStack(AEColor color) {
        return AEApi.instance().definitions().parts().cableGlass().stack(color, 1);
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

    private static ItemStack patternRepeater() {
        return AEApi.instance().definitions().parts().patternRepeater().maybeStack(1).get();
    }

    private static void setProvider(PartPatternRepeater repeater) {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("waitingStacks", new NBTTagList());
        data.setBoolean("provider", true);
        repeater.readFromNBT(data);
        repeater.gridChanged();
    }

    private static CraftingGridCache craftingCache(TileController controller) {
        try {
            ICraftingGrid crafting = controller.getProxy().getCrafting();
            if (crafting instanceof CraftingGridCache cache) {
                return cache;
            }
            throw new AssertionError("Network crafting cache should use CraftingGridCache");
        } catch (GridAccessException e) {
            throw new AssertionError("Network crafting cache should be accessible", e);
        }
    }

    private static ICraftingPatternDetails firstPattern(GameTestHelper helper, TileController controller,
            Block output) {
        ImmutableCollection<ICraftingPatternDetails> patterns = craftingCache(controller)
                .getCraftingFor(itemStack(output, 1), null, -1, controller.getWorldObj());
        helper.assertFalse(patterns.isEmpty(), "Target network should advertise the local processing pattern");
        return patterns.iterator().next();
    }

    private static void assertFluidInputs(GameTestHelper helper, IAEStack<?>[] inputs) {
        helper.assertEquals(1, inputs.length, "Tunnel Pattern should expand to one fluid input");
        helper.assertTrue(inputs[0] instanceof IAEFluidStack, "Tunnel Pattern should not remain as a raw item input");
        IAEFluidStack fluid = (IAEFluidStack) inputs[0];
        helper.assertSame(FluidRegistry.WATER, fluid.getFluid(), "Tunnel Pattern should resolve to water");
        helper.assertEquals(2_000L, fluid.getStackSize(), "Tunnel Pattern should preserve its input multiplier");
    }

    private static ItemStack encodedProcessingPattern(ItemStack input, Block output, int outputAmount) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(input));
        outputs.appendTag(itemTag(new ItemStack(output, outputAmount)));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);
        return encodedPattern;
    }

    private static ItemStack encodedTunnelPattern(UUID uuid, IAEStack<?> input) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        ItemTunnelPattern.writeTunnelUuid(patternTags, uuid);
        inputs.appendTag(input.toNBTGeneric());
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", new NBTTagList());
        encodedPattern.setTagCompound(patternTags);
        return encodedPattern;
    }

    private static NBTTagCompound itemTag(ItemStack item) {
        NBTTagCompound tag = new NBTTagCompound();
        Platform.writeItemStackToNBT(item, tag);
        return tag;
    }

    private static Block cableBusBlock() {
        return AEApi.instance().definitions().blocks().multiPart().maybeBlock().get();
    }

}
