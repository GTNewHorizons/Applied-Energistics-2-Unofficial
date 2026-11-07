package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.assertInactive;
import static appeng.gametests.AEGameTestHelpers.assertNetworkStoredAmount;
import static appeng.gametests.AEGameTestHelpers.assertStoredAmount;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.insertItems;
import static appeng.gametests.AEGameTestHelpers.itemMonitor;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.gametests.AEGameTestHelpers.part;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableCollection;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.TickCallbackHandle;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import appeng.container.ContainerNull;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.core.AppEng;
import appeng.helpers.IInterfaceHost;
import appeng.items.contents.NetworkToolViewer;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartInterface;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;
import appeng.util.inv.MEInventoryCrafting;
import io.netty.channel.embedded.EmbeddedChannel;

@GameTestHolder(AppEng.MOD_ID)
public class InterfaceTests {

    private static final String CONTROLLER_LABEL = "controller";
    private static final String DRIVE_LABEL = "drive";
    private static final String BLOCK_INTERFACE_LABEL = "block_interface";
    private static final String BLOCK_INTERFACE_CABLE_LABEL = "block_interface_cable";
    private static final String PART_INTERFACE_HOST_LABEL = "part_interface_host";
    private static final String ADJACENT_CHEST_LABEL = "adjacent_chest";
    private static final String ASSEMBLER_LABEL = "assembler";
    private static final String MAIN_CONTROLLER_LABEL = "main_controller";
    private static final String SMART_INTERFACE_LABEL = "smart_interface";
    private static final String NORMAL_INTERFACE_LABEL = "normal_interface";
    private static final String SUBNET_CONTROLLER_LABEL = "subnet_controller";

    private static final int STOCK_AMOUNT = 32;

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesPreserveCardsAndNbt(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_transfer");
            for (long targetId = 0; targetId < 2; targetId++) {
                ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, targetId);
                IInterfaceHost target = terminalTarget(helper, container);
                ItemStack cards = capacityCards(2);
                ItemStack expectedCard = cards.copy();
                expectedCard.stackSize = 1;
                player.inventory.setItemStack(cards);
                container.slotClick(36, 0, 0, player);
                helper.assertEquals(1, player.inventory.getItemStack().stackSize, "Insertion consumes one card");
                container.slotClick(37, 0, 0, player);
                helper.assertNull(player.inventory.getItemStack(), "Both source cards should be installed");
                helper.assertEquals(2, target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY), "Two cards installed");

                container.slotClick(36, 0, 0, player);
                container.swapSlotContents(37, 27);
                helper.assertEquals(0, target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY), "Both cards removed");
                helper.assertItemEqual(expectedCard, player.inventory.getItemStack(), "Cursor removal preserves NBT");
                helper.assertItemEqual(expectedCard, player.inventory.mainInventory[0], "Hotbar removal preserves NBT");
                helper.assertEquals(
                        2,
                        player.inventory.getItemStack().stackSize + player.inventory.mainInventory[0].stackSize,
                        "Exactly the two original cards remain");
                player.inventory.setItemStack(null);
                player.inventory.mainInventory[0] = null;

                player.inventory
                        .setItemStack(AEApi.instance().definitions().materials().cardCrafting().maybeStack(1).get());
                container.slotClick(38, 0, 0, player);
                helper.assertEquals(1, target.getInstalledUpgrades(Upgrades.CRAFTING), "Crafting card accepted");
                container.slotClick(38, 0, 0, player);
                helper.assertEquals(0, target.getInstalledUpgrades(Upgrades.CRAFTING), "Crafting card removable");
                helper.assertEquals(1, player.inventory.getItemStack().stackSize, "Exactly one crafting card returned");
                player.inventory.setItemStack(null);
                assertNoTerminalDrops(helper);
            }
        });
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesRefusePatternDrops(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_pattern_guard");
            for (long targetId = 0; targetId < 2; targetId++) {
                ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, targetId);
                IInterfaceHost target = terminalTarget(helper, container);
                container.getSlot(36).putStack(capacityCards(1));
                container.getSlot(37).putStack(capacityCards(1));
                ItemStack pattern = encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1);
                target.getPatterns().setInventorySlotContents(26, pattern);
                for (int action = 0; action < 4; action++) {
                    switch (action) {
                        case 0 -> container.slotClick(36, 0, 0, player);
                        case 1 -> container.transferStackInSlot(player, 36);
                        case 2 -> container.swapSlotContents(36, 27);
                        case 3 -> container.slotClick(36, 0, 4, player);
                    }
                    helper.assertEquals(
                            2,
                            target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                            "Unsafe removal must be refused, action=" + action);
                    helper.assertNull(player.inventory.getItemStack(), "Refusal must not create a cursor card");
                    helper.assertNull(player.inventory.mainInventory[0], "Refusal must not create a hotbar card");
                    helper.assertSame(
                            pattern,
                            target.getPatterns().getStackInSlot(26),
                            "Pattern must stay in its slot");
                    assertNoTerminalDrops(helper);
                }
                target.getPatterns().setInventorySlotContents(26, null);
                target.getPatterns().setInventorySlotContents(17, pattern);
                container.slotClick(36, 0, 0, player);
                helper.assertEquals(
                        1,
                        player.inventory.getItemStack().stackSize,
                        "Unused third row allows one removal");
                player.inventory.setItemStack(null);
                container.slotClick(37, 0, 0, player);
                helper.assertNull(player.inventory.getItemStack(), "Occupied second row blocks the last removal");
                helper.assertEquals(
                        1,
                        target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                        "Last card remains installed");
                helper.assertSame(
                        pattern,
                        target.getPatterns().getStackInSlot(17),
                        "Second-row pattern stays in place");
                target.getPatterns().setInventorySlotContents(17, null);
                container.slotClick(37, 0, 0, player);
                helper.assertEquals(
                        1,
                        player.inventory.getItemStack().stackSize,
                        "Empty second row allows safe removal");
                helper.assertEquals(0, target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY), "Safe removal succeeds");
                player.inventory.setItemStack(null);
                assertNoTerminalDrops(helper);
            }
        });
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesConcurrentRemovalDoesNotDuplicateCards(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_first_viewer");
            FakePlayer other = terminalPlayer(helper, "upgrades_second_viewer");
            for (long targetId = 0; targetId < 2; targetId++) {
                ContainerInterfaceTerminal first = openTerminal(helper, player, anchor, targetId);
                ContainerInterfaceTerminal second = openTerminal(helper, other, anchor, targetId);
                IInterfaceHost target = terminalTarget(helper, first);
                ItemStack card = capacityCards(1);
                first.getSlot(36).putStack(card);
                second.slotClick(36, 0, 0, other);
                first.slotClick(36, 0, 0, player);
                helper.assertNull(player.inventory.getItemStack(), "Stale removal must not duplicate the card");
                helper.assertEquals(
                        1,
                        other.inventory.getItemStack().stackSize,
                        "First removal returns exactly one card");
                helper.assertItemEqual(card, other.inventory.getItemStack(), "Concurrent removal preserves NBT");
                helper.assertEquals(
                        0,
                        target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                        "No card remains installed");
                other.inventory.setItemStack(null);
                assertNoTerminalDrops(helper);
            }
        });
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesSwitchTargetsSynchronizesSlots(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_selection");
            ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, 0);
            IInterfaceHost original = terminalTarget(helper, container);
            ItemStack card = capacityCards(1);
            container.getSlot(36).putStack(card);
            final ItemStack[] snapshot = new ItemStack[4];
            final int[] updates = { 0 };
            final int[] fullSyncs = { 0 };
            container.addCraftingToCrafters(new ICrafting() {

                @Override
                public void sendContainerAndContentsToPlayer(Container source, List<ItemStack> stacks) {
                    fullSyncs[0]++;
                }

                @Override
                public void sendSlotContents(Container source, int slot, ItemStack stack) {
                    if (slot >= 36 && slot < 40) {
                        snapshot[slot - 36] = stack == null ? null : stack.copy();
                        updates[0]++;
                    }
                }

                @Override
                public void sendProgressBarUpdate(Container source, int key, int value) {}
            });
            helper.assertItemEqual(card, snapshot[0], "Initial snapshot includes the installed card");
            fullSyncs[0] = 0;
            container.slotClick(27, 0, 0, player);
            helper.assertEquals(0, fullSyncs[0], "Unrelated player-inventory clicks do not resend the whole window");
            final Slot fixedUpgradeSlot = container.getSlot(36);
            int slotCount = container.inventorySlots.size();
            updates[0] = 0;
            container.selectUpgrades(1);
            helper.assertEquals(1L, container.getSelectedUpgradeId(), "Other interface selected");
            helper.assertSame(fixedUpgradeSlot, container.getSlot(36), "Upgrade slot objects remain fixed");
            helper.assertNotSame(
                    original,
                    terminalTarget(helper, container),
                    "New selection delegates to the other host");
            helper.assertEquals(4, updates[0], "Selection explicitly synchronizes all four slots");
            for (ItemStack stack : snapshot) helper.assertNull(stack, "Empty target clears previous cards");
            container.selectUpgrades(0);
            helper.assertSame(fixedUpgradeSlot, container.getSlot(36), "Returning reuses the same upgrade slot");
            helper.assertSame(original, terminalTarget(helper, container), "Returning delegates to the original host");
            helper.assertItemEqual(card, snapshot[0], "Returning to a target restores its card snapshot");
            helper.assertEquals(slotCount, container.inventorySlots.size(), "Switching preserves slot IDs/count");
            container.selectUpgrades(Long.MAX_VALUE);
            for (ItemStack stack : snapshot) helper.assertNull(stack, "Refused selection clears the slot snapshot");
        });
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesInvalidSelectionPreservesCursor(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_invalid_target");
            for (long targetId = 0; targetId < 2; targetId++) {
                ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, targetId);
                IInterfaceHost target = terminalTarget(helper, container);
                container.getSlot(36).putStack(capacityCards(1));
                ItemStack held = AEApi.instance().definitions().materials().cardCrafting().maybeStack(1).get();
                player.inventory.setItemStack(held);
                container.selectUpgrades(Long.MAX_VALUE);
                container.slotClick(36, 0, 0, player);
                helper.assertEquals(-1L, container.getSelectedUpgradeId(), "Invalid target closes the panel");
                helper.assertItemEqual(held, player.inventory.getItemStack(), "Rejected click preserves the cursor");
                helper.assertEquals(1, player.inventory.getItemStack().stackSize, "Rejected click consumes no card");
                helper.assertEquals(
                        1,
                        target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                        "Original card remains installed");
                helper.assertEquals(
                        0,
                        target.getInstalledUpgrades(Upgrades.CRAFTING),
                        "Invalid selection cannot edit the old target");
                player.inventory.setItemStack(null);
                assertNoTerminalDrops(helper);
            }
        });
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesNetworkToolTransfersAndPersistsCards(GameTestHelper helper) {
        checkTerminalToolbox(helper, AEApi.instance().definitions().items().networkTool().maybeStack(1).get(), 3);
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesAdvancedNetworkToolTransfersAndPersistsCards(GameTestHelper helper) {
        checkTerminalToolbox(
                helper,
                AEApi.instance().definitions().items().advancedNetworkTool().maybeStack(1).get(),
                5);
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void terminalUpgradesMissingToolInvalidatesContainer(GameTestHelper helper) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_missing_tool");
            for (ItemStack tool : new ItemStack[] {
                    AEApi.instance().definitions().items().networkTool().maybeStack(1).get(),
                    AEApi.instance().definitions().items().advancedNetworkTool().maybeStack(1).get() }) {
                player.inventory.mainInventory[8] = tool;
                ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, 0);
                ItemStack card = capacityCards(1);
                container.getSlot(40).putStack(card);
                player.inventory.mainInventory[8] = null;
                container.slotClick(40, 0, 0, player);
                helper.assertFalse(container.isValidContainer(), "Missing tool invalidates the container");
                helper.assertNull(player.inventory.getItemStack(), "Detached tool cards cannot be extracted");
                helper.assertItemEqual(
                        card,
                        new NetworkToolViewer(tool, null, container.getToolboxSize()).getStackInSlot(0),
                        "Detached tool retains its saved card");
                assertNoTerminalDrops(helper);
            }
        });
    }

    private static void checkTerminalToolbox(GameTestHelper helper, ItemStack tool, int size) {
        withInterfaceTerminal(helper, anchor -> {
            FakePlayer player = terminalPlayer(helper, "upgrades_toolbox_" + size);
            for (long targetId = 0; targetId < 2; targetId++) {
                player.inventory.mainInventory[8] = tool.copy();
                ContainerInterfaceTerminal container = openTerminal(helper, player, anchor, targetId);
                IInterfaceHost target = terminalTarget(helper, container);
                helper.assertEquals(size, container.getToolboxSize(), "Toolbox grid has the expected size");
                container.slotClick(35, 0, 0, player);
                helper.assertNull(player.inventory.getItemStack(), "Active tool is locked in the player inventory");
                ItemStack cards = capacityCards(2);
                int lastToolSlot = 40 + size * size - 1;
                ItemStack expectedCards = cards.copy();
                container.getSlot(lastToolSlot).putStack(cards);
                container.slotClick(lastToolSlot, 0, 1, player);
                helper.assertNull(container.getSlot(lastToolSlot).getStack(), "Shift-click extracts tool cards");
                helper.assertEquals(
                        2,
                        target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                        "Both tool cards installed");
                container.slotClick(36, 0, 1, player);
                container.slotClick(37, 0, 1, player);
                helper.assertEquals(
                        0,
                        target.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY),
                        "Both interface cards removed");
                ItemStack saved = new NetworkToolViewer(player.inventory.mainInventory[8], null, size)
                        .getStackInSlot(0);
                helper.assertItemEqual(expectedCards, saved, "Returned cards preserve NBT in tool storage");
                helper.assertEquals(2, saved.stackSize, "Exactly two cards return to the saved toolbox");
                container.selectUpgrades(-1);
                container.slotClick(40, 0, 0, player);
                helper.assertEquals(
                        2,
                        player.inventory.getItemStack().stackSize,
                        "Toolbox works without a selected target");
                container.slotClick(40, 0, 0, player);
                ContainerInterfaceTerminal reopened = openTerminal(helper, player, anchor, targetId);
                helper.assertItemEqual(
                        expectedCards,
                        reopened.getSlot(40).getStack(),
                        "Reopening restores tool card NBT");
                helper.assertEquals(2, reopened.getSlot(40).getStack().stackSize, "Reopening preserves card count");
                helper.assertNull(player.inventory.getItemStack(), "No card remains on the cursor");
                assertNoTerminalDrops(helper);
            }
        });
    }

    private static void withInterfaceTerminal(GameTestHelper helper, Consumer<PartInterfaceTerminal> assertions) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, PART_INTERFACE_HOST_LABEL);
        PartInterfaceTerminal terminal = null;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (cable.getPart(side) != null || !helper.getWorld()
                    .isAirBlock(cable.xCoord + side.offsetX, cable.yCoord + side.offsetY, cable.zCoord + side.offsetZ))
                continue;
            helper.assertEquals(
                    side,
                    cable.addPart(
                            AEApi.instance().definitions().parts().interfaceTerminal().maybeStack(1).get(),
                            side,
                            null),
                    "Terminal should attach to the existing network");
            terminal = (PartInterfaceTerminal) cable.getPart(side);
            break;
        }
        helper.assertNotNull(terminal, "Fixture needs a free cable face for the terminal");
        final PartInterfaceTerminal anchor = terminal;
        helper.startSequence().thenWaitUntil("wait for interface terminal network", 100, () -> {
            assertInterfaceNetworkActive(helper, network);
            assertActive(helper, anchor.getProxy(), "Interface terminal should be online");
        }).thenExecute("exercise terminal scenario", () -> assertions.accept(anchor)).thenSucceed();
    }

    private static FakePlayer terminalPlayer(GameTestHelper helper, String name) {
        FakePlayer player = helper.spawnFakePlayer(name);
        player.capabilities.isCreativeMode = false;
        Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.setItemStack(null);
        // Shift-click invokes normal container synchronization; fake players need a packet sink.
        NetworkManager connection = new NetworkManager(false);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        new NetHandlerPlayServer(MinecraftServer.getServer(), connection, player);
        helper.afterTest(() -> {
            player.inventory.setItemStack(null);
            player.openContainer = player.inventoryContainer;
            channel.finish();
            player.playerNetServerHandler = null;
        });
        return player;
    }

    private static ContainerInterfaceTerminal openTerminal(GameTestHelper helper, FakePlayer player,
            PartInterfaceTerminal anchor, long targetId) {
        ContainerInterfaceTerminal container = new ContainerInterfaceTerminal(player.inventory, anchor);
        player.openContainer = container;
        container.selectUpgrades(targetId);
        helper.assertEquals(targetId, container.getSelectedUpgradeId(), "Tracked interface should be selectable");
        return container;
    }

    private static IInterfaceHost terminalTarget(GameTestHelper helper, ContainerInterfaceTerminal container) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        final Slot selectedSlot = container.getSlot(36);
        final ItemStack previous = selectedSlot.getStack();
        final ItemStack probe = capacityCards(1);
        selectedSlot.putStack(probe);
        final boolean blockSelected = network.blockInterface.getInterfaceDuality().getUpgrades().getStackInSlot(0)
                == probe;
        final boolean partSelected = network.partInterface.getInterfaceDuality().getUpgrades().getStackInSlot(0)
                == probe;
        selectedSlot.putStack(previous);
        helper.assertTrue(blockSelected ^ partSelected, "Selected slot must delegate to exactly one interface");
        IInterfaceHost target = blockSelected ? network.blockInterface : network.partInterface;
        return target;
    }

    private static ItemStack capacityCards(int count) {
        ItemStack cards = AEApi.instance().definitions().materials().cardPatternCapacity().maybeStack(count).get();
        cards.setStackDisplayName("terminal test cards");
        return cards;
    }

    private static void assertNoTerminalDrops(GameTestHelper helper) {
        helper.assertTrue(
                helper.getEntities(EntityItem.class, -1, -1, -1, 16, 8, 16).isEmpty(),
                "No card or pattern should be dropped by terminal actions");
    }

    @GameTest(template = "interface_network", timeoutTicks = 20)
    public static void coloredBlockInterfaceRejectsMismatchedCable(GameTestHelper helper) {
        TileInterface blockInterface = helper.assertTileEntityPresent(TileInterface.class, BLOCK_INTERFACE_LABEL);
        TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, BLOCK_INTERFACE_CABLE_LABEL);
        FakePlayer player = helper.spawnFakePlayer("interface_colors");

        helper.assertTrue(
                blockInterface.recolourBlock(ForgeDirection.NORTH, AEColor.Red, player),
                "Interface should accept paint");
        helper.assertTrue(cable.recolourBlock(ForgeDirection.SOUTH, AEColor.Blue, player), "Cable should accept paint");
        helper.assertEquals(AEColor.Red, blockInterface.getColor(), "Interface should retain paint");
        helper.assertEquals(
                AEColor.Red,
                blockInterface.getProxy().getColor(),
                "Interface proxy should use its paint color");
        helper.onEachTick("different colors stay disconnected", () -> {
            assertInactive(helper, blockInterface.getProxy(), "Colored interface should remain disconnected");
            helper.assertNotSame(
                    blockInterface.getProxy().getNode().getGrid(),
                    cable.getGridNode(ForgeDirection.UNKNOWN).getGrid(),
                    "Different-colored interface and cable should stay on separate grids");
        });
        helper.succeedAtTimeout();
    }

    // Configured interface stock should be pulled from ME storage into the interface inventory.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfaceStocksConfiguredItem(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for interface stocking network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute(
                        "configure block interface to stock 32 cobblestone",
                        () -> configureStock(network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT))
                .thenWaitUntil("wait for interface to stock 32 cobblestone from the drive", 80, () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 64 - STOCK_AMOUNT);
                }).thenSucceed();
    }

    // Blocking mode should reject a pattern push while the target inventory contains non-ignored items.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void blockingModeWaitsForEmptyInventory(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for blocking-interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("enable blocking mode, install pattern, and occupy target with dirt", () -> {
                    network.partInterface.getConfigManager().putSetting(Settings.BLOCK, YesNo.YES);
                    InventoryHelper.setSlot(
                            network.partInterface.getInterfaceDuality().getPatterns(),
                            0,
                            encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1));
                    helper.setSlot(ADJACENT_CHEST_LABEL, 0, new ItemStack(Blocks.dirt));
                })
                .thenWaitUntil(
                        "wait for blocking pattern advertisement",
                        40,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Blocking interface should advertise the processing pattern"))
                .thenExecute("attempt pattern push into occupied target", () -> {
                    ICraftingPatternDetails details = firstPattern(helper, network.controller, Blocks.stone);
                    boolean pushed = network.partInterface.pushPattern(details, craftingTable(Blocks.cobblestone, 1));

                    helper.assertFalse(pushed, "Blocking interface should not push into a non-empty target");
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.dirt), 1);
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 0);
                }).thenExecute("clear blocking target inventory", () -> helper.clearSlot(ADJACENT_CHEST_LABEL, 0))
                .thenExecute("attempt pattern push into empty target", () -> {
                    ICraftingPatternDetails details = firstPattern(helper, network.controller, Blocks.stone);
                    boolean pushed = network.partInterface.pushPattern(details, craftingTable(Blocks.cobblestone, 1));

                    helper.assertTrue(pushed, "Blocking interface should push once the target is empty");
                    helper.assertInventoryCount(ADJACENT_CHEST_LABEL, new ItemStack(Blocks.cobblestone), 1);
                }).thenSucceed();
    }

    // A push from any interface must revoke another interface's smart-blocking recipe allowance.
    @GameTest(template = "interface_smart_blocking_subnet", timeoutTicks = 240)
    public static void normalInterfacePushInvalidatesSmartBlockingAllowance(GameTestHelper helper) {
        SmartBlockingSubnet network = getSmartBlockingSubnet(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for both interface networks to activate",
                        120,
                        () -> assertSmartBlockingSubnetActive(helper, network))
                .thenWaitUntil("wait for both processing patterns to be advertised", 60, () -> {
                    helper.assertFalse(
                            craftingOptionsFor(network.mainController, Blocks.stone).isEmpty(),
                            "Smart interface pattern should be advertised");
                    helper.assertFalse(
                            craftingOptionsFor(network.mainController, Blocks.gravel).isEmpty(),
                            "Normal interface pattern should be advertised");
                }).thenExecute("prime the smart interface recipe allowance", () -> {
                    ICraftingPatternDetails smartPattern = firstPattern(helper, network.mainController, Blocks.stone);
                    boolean pushed = network.smartInterface
                            .pushPattern(smartPattern, craftingTable(Blocks.cobblestone, 1));

                    helper.assertTrue(pushed, "Smart interface should push its first pattern into an empty subnet");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 1);
                }).thenExecute("empty the subnet without changing the smart interface allowance", () -> {
                    IAEItemStack extracted = itemMonitor(network.subnetController).extractItems(
                            itemStack(Blocks.cobblestone, 1),
                            Actionable.MODULATE,
                            new BaseActionSource());

                    helper.assertNotNull(extracted, "Priming input should be extractable from the subnet");
                    helper.assertEquals(1L, extracted.getStackSize(), "Exactly one priming input should be extracted");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 0);
                }).thenExecute("push a pattern from the normal blocking interface", () -> {
                    ICraftingPatternDetails normalPattern = firstPattern(helper, network.mainController, Blocks.gravel);
                    boolean pushed = network.normalInterface.pushPattern(normalPattern, craftingTable(Blocks.dirt, 1));

                    helper.assertTrue(pushed, "Normal blocking interface should push into the empty subnet");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.dirt, 1);
                }).thenExecute("reject the stale smart-blocking recipe allowance", () -> {
                    ICraftingPatternDetails smartPattern = firstPattern(helper, network.mainController, Blocks.stone);
                    boolean pushed = network.smartInterface
                            .pushPattern(smartPattern, craftingTable(Blocks.cobblestone, 1));

                    helper.assertFalse(
                            pushed,
                            "Smart interface should block after the normal interface starts a new subnet batch");
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.dirt, 1);
                    assertNetworkStoredAmount(helper, network.subnetController, Blocks.cobblestone, 0);
                }).thenSucceed();
    }

    // Encoded patterns in interface pattern slots should be advertised by the network crafting cache.
    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void interfacePatternSlotsAdvertiseCraftableOutput(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for pattern-advertisement network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute(
                        "install cobblestone-to-stone processing pattern",
                        () -> InventoryHelper.setSlot(
                                network.blockInterface.getInterfaceDuality().getPatterns(),
                                0,
                                encodedProcessingPattern(Blocks.cobblestone, 1, Blocks.stone, 1)))
                .thenWaitUntil(
                        "wait for interface pattern output to become craftable",
                        80,
                        () -> helper.assertFalse(
                                craftingOptionsFor(network.controller, Blocks.stone).isEmpty(),
                                "Interface pattern output should be craftable"))
                .thenSucceed();
    }

    // Block and part interfaces should both maintain configured stock in their internal inventories.
    @GameTest(template = "interface_network", timeoutTicks = 180)
    public static void partAndBlockInterfacesExposeSameStockBehavior(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        insertItems(helper, driveCell, Blocks.dirt, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for block-and-part interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("configure block and part interface stock targets", () -> {
                    configureStock(network.blockInterface, Blocks.cobblestone, 16);
                    configureStock(network.partInterface, Blocks.dirt, 16);
                }).thenWaitUntil("wait for both interfaces to reach their 16-item stock targets", 100, () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, 16);
                    assertInterfaceStoredAmount(helper, network.partInterface, Blocks.dirt, 16);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 48);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.dirt, 48);
                }).thenSucceed();
    }

    // If the interface storage already satisfies the configured stock amount, ME storage should not be drained.
    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void interfaceDoesNotOverstock(GameTestHelper helper) {
        InterfaceNetwork network = getInterfaceNetwork(helper);
        ItemStack driveCell = cell1k();
        insertItems(helper, driveCell, Blocks.cobblestone, 64);
        helper.setSlot(DRIVE_LABEL, 0, driveCell);
        TickCallbackHandle configuredStockDoesNotDrainNetwork = helper
                .onEachTickDisabled("configured stock does not drain network", () -> {
                    assertInterfaceStoredAmount(helper, network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    assertStoredAmount(helper, network.drive.getStackInSlot(0), Blocks.cobblestone, 64);
                });

        helper.startSequence()
                .thenWaitUntil(
                        "wait for no-overstock interface network to activate",
                        80,
                        () -> assertInterfaceNetworkActive(helper, network))
                .thenExecute("pre-stock interface and enable no-overstock invariant", () -> {
                    configureStock(network.blockInterface, Blocks.cobblestone, STOCK_AMOUNT);
                    InventoryHelper.setSlot(
                            network.blockInterface.getInterfaceDuality().getStorage(),
                            0,
                            new ItemStack(Blocks.cobblestone, STOCK_AMOUNT));
                    configuredStockDoesNotDrainNetwork.enable();
                }).thenIdle(30)
                .thenExecute("finish no-overstock observation window", configuredStockDoesNotDrainNetwork::disable)
                .thenSucceed();
    }

    private static InterfaceNetwork getInterfaceNetwork(GameTestHelper helper) {
        TileController controller = helper.assertTileEntityPresent(TileController.class, CONTROLLER_LABEL);
        TileDrive drive = helper.assertTileEntityPresent(TileDrive.class, DRIVE_LABEL);
        TileInterface blockInterface = helper.assertTileEntityPresent(TileInterface.class, BLOCK_INTERFACE_LABEL);
        PartInterface partInterface = part(helper, PART_INTERFACE_HOST_LABEL, PartInterface.class);
        helper.assertTileEntityPresent(TileEntityChest.class, ADJACENT_CHEST_LABEL);
        helper.assertTileEntityPresent(TileMolecularAssembler.class, ASSEMBLER_LABEL);

        return new InterfaceNetwork(controller, drive, blockInterface, partInterface);
    }

    private static SmartBlockingSubnet getSmartBlockingSubnet(GameTestHelper helper) {
        return new SmartBlockingSubnet(
                helper.assertTileEntityPresent(TileController.class, MAIN_CONTROLLER_LABEL),
                helper.assertTileEntityPresent(TileController.class, SUBNET_CONTROLLER_LABEL),
                helper.assertTileEntityPresent(TileInterface.class, SMART_INTERFACE_LABEL),
                helper.assertTileEntityPresent(TileInterface.class, NORMAL_INTERFACE_LABEL));
    }

    private static void assertSmartBlockingSubnetActive(GameTestHelper helper, SmartBlockingSubnet network) {
        assertActive(helper, network.mainController.getProxy(), "Main controller should become active");
        assertActive(helper, network.subnetController.getProxy(), "Subnet controller should become active");
        assertActive(
                helper,
                network.smartInterface.getProxy(),
                "Smart interface should receive a main-network channel");
        assertActive(
                helper,
                network.normalInterface.getProxy(),
                "Normal interface should receive a main-network channel");
    }

    private static void assertInterfaceNetworkActive(GameTestHelper helper, InterfaceNetwork network) {
        assertActive(helper, network.controller.getProxy(), "Controller grid proxy should become active");
        assertActive(helper, network.drive.getProxy(), "Drive grid proxy should become active");
        assertActive(helper, network.blockInterface.getProxy(), "Block interface should receive a channel");
        assertActive(helper, network.partInterface, "Part interface should receive a channel");
    }

    private static void configureStock(IInterfaceHost interfaceHost, Block block, int amount) {
        InventoryHelper.setSlot(interfaceHost.getInterfaceDuality().getConfig(), 0, new ItemStack(block, amount));
    }

    private static void assertInterfaceStoredAmount(GameTestHelper helper, IInterfaceHost interfaceHost, Block block,
            long expectedAmount) {
        Object blockId = Block.blockRegistry.getNameForObject(block);
        helper.assertEquals(
                expectedAmount,
                interfaceStoredAmount(interfaceHost, block),
                "Interface storage for " + (blockId == null ? block.getUnlocalizedName() : blockId)
                        + " should match; host="
                        + interfaceHost.getClass().getSimpleName());
    }

    private static long interfaceStoredAmount(IInterfaceHost interfaceHost, Block block) {
        return InventoryHelper.count(interfaceHost.getInterfaceDuality().getStorage(), new ItemStack(block));
    }

    private static ImmutableCollection<ICraftingPatternDetails> craftingOptionsFor(TileController controller,
            Block output) {
        try {
            ICraftingGrid crafting = controller.getProxy().getCrafting();
            return crafting.getCraftingFor(itemStack(output, 1), null, -1, controller.getWorldObj());
        } catch (GridAccessException e) {
            throw new AssertionError("Network crafting cache should be accessible", e);
        }
    }

    private static ICraftingPatternDetails firstPattern(GameTestHelper helper, TileController controller,
            Block output) {
        ImmutableCollection<ICraftingPatternDetails> patterns = craftingOptionsFor(controller, output);
        helper.assertFalse(patterns.isEmpty(), "Network crafting cache should advertise the encoded pattern");
        return patterns.iterator().next();
    }

    private static MEInventoryCrafting craftingTable(Block block, int amount) {
        MEInventoryCrafting table = new MEInventoryCrafting(new ContainerNull(), 1, 1);
        table.setInventorySlotContents(0, itemStack(block, amount));
        return table;
    }

    private static ItemStack encodedProcessingPattern(Block input, int inputAmount, Block output, int outputAmount) {
        ItemStack encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).get();
        NBTTagCompound patternTags = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        NBTTagList outputs = new NBTTagList();

        patternTags.setBoolean("crafting", false);
        patternTags.setBoolean("substitute", false);
        patternTags.setBoolean("beSubstitute", false);
        inputs.appendTag(itemTag(input, inputAmount));
        outputs.appendTag(itemTag(output, outputAmount));
        patternTags.setTag("in", inputs);
        patternTags.setTag("out", outputs);
        encodedPattern.setTagCompound(patternTags);

        return encodedPattern;
    }

    private static NBTTagCompound itemTag(Block block, int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        Platform.writeItemStackToNBT(new ItemStack(block, amount), tag);
        return tag;
    }

    @Desugar
    private record InterfaceNetwork(TileController controller, TileDrive drive, TileInterface blockInterface,
            PartInterface partInterface) {}

    @Desugar
    private record SmartBlockingSubnet(TileController mainController, TileController subnetController,
            TileInterface smartInterface, TileInterface normalInterface) {}
}
