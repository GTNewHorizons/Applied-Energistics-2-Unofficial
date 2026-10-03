package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.itemMonitor;
import static appeng.gametests.AEGameTestHelpers.itemStack;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.implementations.ContainerTunnelPatternRenamer;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketMonitorableAction;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.storage.MEInventoryWrapper;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

@GameTestHolder(AppEng.MOD_ID)
public class TunnelPatternRenamingTests {

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void storedRenameFailuresPreserveExactlyOnePattern(final GameTestHelper helper) {
        final TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        helper.setSlot("drive", 0, cell1k());
        final IAEItemStack pattern = AEItemStack.create(tunnelPattern());
        final IAEItemStack renamed = AEItemStack
                .create(TunnelPatternRenaming.renamedCopy(pattern.getItemStack(), "Renamed"));
        final FakePlayer player = guiPlayer(helper);

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the storage network",
                        80,
                        () -> assertActive(helper, controller.getProxy(), "Controller should become active"))
                .thenExecute("exercise power, stale-definition and rollback failures on network storage", () -> {
                    final BaseActionSource source = new BaseActionSource();
                    final IMEInventory<IAEItemStack> inventory = itemMonitor(controller);
                    final IEnergySource power = controller.getProxy().getNode().getGrid().getCache(IEnergyGrid.class);
                    final List<IAEItemStack> returned = new ArrayList<>();
                    helper.assertNull(
                            inventory.injectItems(pattern.copy(), Actionable.MODULATE, source),
                            "Original pattern should fit in the drive");

                    helper.assertFalse(
                            TunnelPatternRenaming.renameStored(
                                    (amount, mode, multiplier) -> 0,
                                    inventory,
                                    pattern,
                                    "Renamed",
                                    source,
                                    returned::add),
                            "No power should prevent the transaction");
                    assertStoredPattern(helper, inventory, pattern, source);

                    final IMEInventory<IAEItemStack> rejectRename = rejectInsertions(
                            inventory,
                            input -> !input.isSameType(pattern));
                    helper.assertFalse(
                            TunnelPatternRenaming
                                    .renameStored(power, rejectRename, pattern, "Renamed", source, returned::add),
                            "Rejected replacement should trigger rollback");
                    assertStoredPattern(helper, inventory, pattern, source);
                    helper.assertTrue(returned.isEmpty(), "Successful rollback should leave nothing to return");

                    final ItemStack updatedItem = pattern.getItemStack();
                    updatedItem.getTagCompound().setString("testContents", "Updated definition");
                    final IAEItemStack updated = AEItemStack.create(updatedItem);
                    inventory.extractItems(pattern, Actionable.MODULATE, source);
                    inventory.injectItems(updated, Actionable.MODULATE, source);
                    helper.assertFalse(
                            TunnelPatternRenaming
                                    .renameStored(power, inventory, pattern, "Renamed", source, returned::add),
                            "A same-UUID replacement should not satisfy the stale exact item request");
                    assertStoredPattern(helper, inventory, updated, source);
                    inventory.extractItems(updated, Actionable.MODULATE, source);
                    inventory.injectItems(pattern.copy(), Actionable.MODULATE, source);

                    final IMEInventory<IAEItemStack> rejectAll = rejectInsertions(inventory, input -> true);
                    helper.assertFalse(
                            TunnelPatternRenaming
                                    .renameStored(power, rejectAll, pattern, "Renamed", source, remainder -> {
                                        returned.add(remainder);
                                        Platform.addToPlayerInvOrDrop(player, remainder.getItemStack());
                                    }),
                            "Rejected rollback should return the original pattern to the player");
                    helper.assertEquals(1, returned.size(), "Only one original pattern should be returned");
                    helper.assertTrue(
                            returned.get(0).isSameType(pattern),
                            "Returned item should retain its original NBT");
                    helper.assertEquals(
                            1L,
                            InventoryHelper.count(player.inventory, pattern.getItemStack()),
                            "Player inventory should receive the original pattern exactly once");
                    helper.assertNull(
                            inventory.extractItems(pattern, Actionable.SIMULATE, source),
                            "Returned original should no longer be in the network");
                    helper.assertNull(
                            inventory.extractItems(renamed, Actionable.SIMULATE, source),
                            "Rejected replacement should not be duplicated in the network");
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void interfaceAndPlayerSlotsRenameThroughPackets(final GameTestHelper helper) {
        final TileInterface tile = helper.assertTileEntityPresent(TileInterface.class, "block_interface");
        final FakePlayer player = guiPlayer(helper);
        player.setPosition(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
        final ItemStack pattern = tunnelPattern();
        InventoryHelper.setSlot(tile.getInterfaceDuality().getPatterns(), 0, pattern.copy());

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the interface network",
                        80,
                        () -> assertActive(helper, tile.getProxy(), "Interface should become active"))
                .thenExecute("rename the installed pattern through its container packets", () -> {
                    Platform.openGUI(player, tile, ForgeDirection.UNKNOWN, GuiBridge.GUI_INTERFACE);
                    helper.assertTrue(player.openContainer instanceof ContainerInterface, "Interface GUI should open");
                    final AEBaseContainer container = (AEBaseContainer) player.openContainer;
                    final int slot = findSlot(container, tile.getInterfaceDuality().getPatterns(), 0);
                    new PacketInventoryAction(InventoryAction.RENAME_TUNNEL_PATTERN, slot, 0)
                            .serverPacketData(null, null, player);
                    submitName(helper, player, "Interface renamed");
                    final ItemStack renamed = tile.getInterfaceDuality().getPatterns().getStackInSlot(0);
                    assertRenamed(helper, pattern, renamed, "Interface renamed");
                }).thenExecute("rename a full player-inventory stack from the interface GUI", () -> {
                    final ItemStack inventoryPattern = pattern.copy();
                    inventoryPattern.stackSize = 8;
                    player.inventory.setInventorySlotContents(0, inventoryPattern);
                    final AEBaseContainer container = (AEBaseContainer) player.openContainer;
                    final int slot = findSlot(container, player.inventory, 0);
                    new PacketInventoryAction(InventoryAction.RENAME_TUNNEL_PATTERN, slot, 0)
                            .serverPacketData(null, null, player);
                    submitName(helper, player, "Inventory renamed");
                    final ItemStack renamed = player.inventory.getStackInSlot(0);
                    helper.assertEquals(8, renamed.stackSize, "Rename should preserve the full player stack");
                    assertRenamed(helper, inventoryPattern, renamed, "Inventory renamed");
                    player.openContainer = player.inventoryContainer;
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void changedToolboxLayoutRenamesOnlyTheOriginalPlayerSlot(final GameTestHelper helper) {
        final TileInterface tile = helper.assertTileEntityPresent(TileInterface.class, "block_interface");
        final FakePlayer player = guiPlayer(helper);
        player.setPosition(tile.xCoord + 0.5, tile.yCoord + 0.5, tile.zCoord + 0.5);
        final ItemStack pattern = tunnelPattern();
        player.inventory.setInventorySlotContents(9, pattern.copy());
        player.inventory.setInventorySlotContents(18, pattern.copy());

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the interface network",
                        80,
                        () -> assertActive(helper, tile.getProxy(), "Interface should become active"))
                .thenExecute("change the toolbox layout while renaming one of two identical patterns", () -> {
                    Platform.openGUI(player, tile, ForgeDirection.UNKNOWN, GuiBridge.GUI_INTERFACE);
                    final ContainerInterface container = (ContainerInterface) player.openContainer;
                    helper.assertFalse(container.hasToolbox(), "Original GUI should not have a toolbox");
                    final int oldSlotNumber = findSlot(container, player.inventory, 18);
                    new PacketInventoryAction(InventoryAction.RENAME_TUNNEL_PATTERN, oldSlotNumber, 0)
                            .serverPacketData(null, null, player);
                    player.inventory.setInventorySlotContents(
                            0,
                            AEApi.instance().definitions().items().networkTool().maybeStack(1).get());
                    submitName(helper, player, "Original slot renamed");

                    final ContainerInterface reopened = (ContainerInterface) player.openContainer;
                    helper.assertTrue(reopened.hasToolbox(), "Reopened GUI should include the new toolbox");
                    helper.assertEquals(
                            9,
                            reopened.getSlot(oldSlotNumber).getSlotIndex(),
                            "Old container slot number should now refer to the identical pattern in slot 9");
                    helper.assertTrue(
                            AEItemStack.create(pattern).isSameType(player.inventory.getStackInSlot(9)),
                            "Identical pattern in the shifted slot should remain unchanged");
                    assertRenamed(helper, pattern, player.inventory.getStackInSlot(18), "Original slot renamed");
                    helper.assertEquals(
                            2,
                            player.inventory.getStackInSlot(9).stackSize
                                    + player.inventory.getStackInSlot(18).stackSize,
                            "Layout change should preserve both patterns exactly once");
                    player.openContainer = player.inventoryContainer;
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 140)
    public static void storedPatternRenamesThroughTerminalPackets(final GameTestHelper helper) {
        final TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper);
        player.setPosition(cable.xCoord + 0.5, cable.yCoord + 0.5, cable.zCoord + 0.5);
        cable.addPart(
                AEApi.instance().definitions().parts().patternTerminal().maybeStack(1).get(),
                ForgeDirection.UP,
                player);
        final PartPatternTerminal terminal = (PartPatternTerminal) cable.getPart(ForgeDirection.UP);
        helper.setSlot("drive", 0, cell1k());
        final IAEItemStack pattern = AEItemStack.create(tunnelPattern());

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the terminal network",
                        80,
                        () -> assertActive(helper, terminal, "Pattern Terminal should become active"))
                .thenExecute("rename one network-stored pattern through terminal packets", () -> {
                    final BaseActionSource source = new BaseActionSource();
                    helper.assertNull(
                            itemMonitor(controller).injectItems(pattern.copy(), Actionable.MODULATE, source),
                            "Stored pattern should fit in the drive");
                    Platform.openGUI(player, cable, ForgeDirection.UP, GuiBridge.GUI_PATTERN_TERMINAL);
                    helper.assertTrue(player.openContainer instanceof ContainerMEMonitorable, "Terminal should open");
                    final ContainerMEMonitorable container = (ContainerMEMonitorable) player.openContainer;
                    container.setTargetStack(pattern.copy());
                    new PacketMonitorableAction(MonitorableAction.RENAME_TUNNEL_PATTERN, -1)
                            .serverPacketData(null, null, player);
                    submitName(helper, player, "Network renamed");
                    final IAEItemStack renamed = AEItemStack
                            .create(TunnelPatternRenaming.renamedCopy(pattern.getItemStack(), "Network renamed"));
                    helper.assertNull(
                            itemMonitor(controller).extractItems(pattern, Actionable.SIMULATE, source),
                            "Old pattern name should no longer be stored");
                    final IAEItemStack stored = itemMonitor(controller)
                            .extractItems(renamed, Actionable.SIMULATE, source);
                    helper.assertNotNull(stored, "Renamed pattern should be stored in the terminal network");
                    helper.assertEquals(
                            1L,
                            stored.getStackSize(),
                            "The transaction should preserve exactly one pattern");
                    assertRenamed(helper, pattern.getItemStack(), stored.getItemStack(), "Network renamed");
                    player.openContainer = player.inventoryContainer;
                }).thenSucceed();
    }

    private static void submitName(final GameTestHelper helper, final EntityPlayer player, final String name) {
        helper.assertTrue(
                player.openContainer instanceof ContainerTunnelPatternRenamer,
                "Tunnel Pattern renamer should open");
        try {
            new PacketValueConfig("TunnelPattern.Rename", name).serverPacketData(null, null, player);
        } catch (IOException e) {
            throw new AssertionError("Rename packet should be encodable", e);
        }
        helper.assertTrue(player.openContainer instanceof AEBaseContainer, "Saving should return to the primary GUI");
    }

    private static IMEInventory<IAEItemStack> rejectInsertions(final IMEInventory<IAEItemStack> inventory,
            final Predicate<IAEItemStack> reject) {
        return new MEInventoryWrapper<IAEItemStack>(inventory, ITEM_STACK_TYPE) {

            @Override
            public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode,
                    final BaseActionSource source) {
                return reject.test(input) ? input : super.injectItems(input, mode, source);
            }
        };
    }

    private static void assertStoredPattern(final GameTestHelper helper, final IMEInventory<IAEItemStack> inventory,
            final IAEItemStack pattern, final BaseActionSource source) {
        final IAEItemStack stored = inventory.extractItems(pattern, Actionable.SIMULATE, source);
        helper.assertNotNull(stored, "Original or updated pattern should remain stored after the failed transaction");
        helper.assertEquals(1L, stored.getStackSize(), "Failed transaction should preserve exactly one stored pattern");
    }

    private static int findSlot(final AEBaseContainer container, final IInventory inventory, final int index) {
        for (final Slot slot : container.inventorySlots) {
            if (slot.inventory == inventory && slot.getSlotIndex() == index) return slot.slotNumber;
        }
        throw new AssertionError("Expected inventory slot should be present in the GUI");
    }

    private static void assertRenamed(final GameTestHelper helper, final ItemStack original, final ItemStack renamed,
            final String name) {
        helper.assertTrue(
                AEItemStack.create(TunnelPatternRenaming.renamedCopy(original, name)).isSameType(renamed),
                "Only the pattern's custom name should change");
        helper.assertEquals(
                ItemTunnelPattern.getTunnelUuid(original),
                ItemTunnelPattern.getTunnelUuid(renamed),
                "Tunnel UUID should be preserved");
    }

    private static ItemStack tunnelPattern() {
        final ItemStack pattern = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagList inputs = new NBTTagList();
        inputs.appendTag(itemStack(Blocks.cobblestone, 4).toNBTGeneric());
        tag.setTag("in", inputs);
        tag.setTag("out", new NBTTagList());
        ItemTunnelPattern.writeTunnelUuid(tag, UUID.randomUUID());
        pattern.setTagCompound(tag);
        pattern.setStackDisplayName("Original");
        return pattern;
    }

    // Forge FakePlayer.openGui is a no-op. Construct server GUIs through the real bridge without a client connection.
    private static FakePlayer guiPlayer(final GameTestHelper helper) {
        final FakePlayer player = new FakePlayer(
                (WorldServer) helper.getWorld(),
                new GameProfile(UUID.randomUUID(), "tunnel_rename")) {

            @Override
            public void openGui(final Object mod, final int guiId, final World world, final int x, final int y,
                    final int z) {
                this.openContainer = (Container) GuiBridge.GUI_Handler.getServerGuiElement(guiId, this, world, x, y, z);
            }
        };
        final NetworkManager network = new NetworkManager(false);
        final EmbeddedChannel channel = new EmbeddedChannel(network);
        new NetHandlerPlayServer(MinecraftServer.getServer(), network, player);
        network.setConnectionState(EnumConnectionState.PLAY);
        // Forge supports FakePlayers with a channel but no FML dispatcher: there is no client to send GUI sync to.
        helper.afterTest(() -> {
            player.openContainer = player.inventoryContainer;
            channel.finish();
            Object message;
            while ((message = channel.readOutbound()) != null) ReferenceCountUtil.release(message);
        });
        return player;
    }
}
