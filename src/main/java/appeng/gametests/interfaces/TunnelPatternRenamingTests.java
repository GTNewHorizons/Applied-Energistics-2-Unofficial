package appeng.gametests.interfaces;

import static appeng.gametests.AEGameTestHelpers.assertActive;
import static appeng.gametests.AEGameTestHelpers.cell1k;
import static appeng.gametests.AEGameTestHelpers.itemMonitor;
import static appeng.gametests.AEGameTestHelpers.itemStack;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
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

import com.google.common.base.Strings;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.InventoryHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.mojang.authlib.GameProfile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.items.IBiometricCard;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerInterface;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.implementations.ContainerTunnelPatternRenamer;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodec;
import appeng.container.sync.StreamCodecs;
import appeng.container.sync.SyncEndpoint;
import appeng.container.sync.SyncMode;
import appeng.core.AppEng;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.me.cache.SecurityCache;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.misc.TileInterface;
import appeng.tile.misc.TileSecurity;
import appeng.tile.networking.TileCableBus;
import appeng.tile.networking.TileController;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

@GameTestHolder(AppEng.MOD_ID)
public class TunnelPatternRenamingTests {

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void normalAndExtendedOutputSlotsRenameThroughActions(final GameTestHelper helper) {
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper, cable);
        final PartPatternTerminal normal = addTerminal(cable, player, ForgeDirection.UP, false);
        final PartPatternTerminal extended = addTerminal(cable, player, ForgeDirection.DOWN, true);

        helper.startSequence().thenWaitUntil("wait for both encoding terminals", 80, () -> {
            assertActive(helper, normal, "Normal terminal should become active");
            assertActive(helper, extended, "Extended terminal should become active");
        }).thenExecute("rename only the real output slot in each encoding terminal", () -> {
            for (final ForgeDirection side : new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.DOWN }) {
                final PartPatternTerminal terminal = (PartPatternTerminal) cable.getPart(side);
                final IInventory inventory = patterns(terminal);
                final ItemStack original = tunnelPattern();
                inventory.setInventorySlotContents(1, original.copy());
                final ContainerPatternTerm container = openTerminal(player, cable, side);
                helper.assertEquals(
                        side == ForgeDirection.DOWN,
                        container instanceof ContainerPatternTermEx,
                        "Normal and extended terminals should use their own containers");
                openRenamer(helper, container, player);

                final ItemStack forged = original.copy();
                forged.getTagCompound().setString("testContents", "Client supplied contents");
                new PacketPatternValueSet(AEItemStack.create(forged), StorageName.NONE, 0, true)
                        .serverPacketData(null, null, player);
                submitName(helper, player, "Renamed");
                assertRenamed(helper, original, inventory.getStackInSlot(1), "Renamed");

                openRenamer(helper, (ContainerPatternTerm) player.openContainer, player);
                submitName(helper, player, "");
                final ItemStack cleared = inventory.getStackInSlot(1);
                helper.assertFalse(cleared.hasDisplayName(), "Empty name should clear the custom name");
                assertRenamed(helper, original, cleared, "");

                openRenamer(helper, (ContainerPatternTerm) player.openContainer, player);
                submitName(helper, player, "A\u00a7\n\u0000B" + Strings.repeat("C", 100));
                assertRenamed(helper, cleared, inventory.getStackInSlot(1), "AB" + Strings.repeat("C", 62));
            }
        }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void staleOutputPatternsAreRejected(final GameTestHelper helper) {
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper, cable);
        final PartPatternTerminal terminal = addTerminal(cable, player, ForgeDirection.UP, false);
        final IInventory inventory = patterns(terminal);
        final ItemStack original = tunnelPattern();

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the encoding terminal",
                        80,
                        () -> assertActive(helper, terminal, "Terminal should become active"))
                .thenExecute("reject changed contents, names, counts and a removed output", () -> {
                    for (int change = 0; change < 4; change++) {
                        inventory.setInventorySlotContents(1, original.copy());
                        openRenamer(helper, openTerminal(player, cable, ForgeDirection.UP), player);
                        final ItemStack changed = change == 3 ? null : original.copy();
                        if (change == 0) changed.getTagCompound().setString("testContents", "Changed");
                        if (change == 1) changed.setStackDisplayName("Changed by another player");
                        if (change == 2) changed.stackSize = 2;
                        inventory.setInventorySlotContents(1, changed);
                        submitName(helper, player, "Should not apply");
                        assertUnchanged(helper, changed, inventory.getStackInSlot(1));
                    }
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void otherInventoriesAndLegacyPacketsDoNotRename(final GameTestHelper helper) {
        final TileController controller = helper.assertTileEntityPresent(TileController.class, "controller");
        final TileInterface tile = helper.assertTileEntityPresent(TileInterface.class, "block_interface");
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper, cable);
        final PartPatternTerminal terminal = addTerminal(cable, player, ForgeDirection.UP, false);
        final ItemStack original = tunnelPattern();
        patterns(terminal).setInventorySlotContents(0, original.copy());
        terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT).putAEStackInSlot(0, AEItemStack.create(original));
        player.inventory.setInventorySlotContents(0, original.copy());
        InventoryHelper.setSlot(tile.getInterfaceDuality().getPatterns(), 0, original.copy());
        helper.setSlot("drive", 0, cell1k());

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the encoding terminal",
                        80,
                        () -> assertActive(helper, terminal, "Terminal should become active"))
                .thenExecute("an empty output cannot rename items in any other inventory", () -> {
                    final BaseActionSource source = new BaseActionSource();
                    final IAEItemStack stored = AEItemStack.create(original);
                    helper.assertNull(
                            itemMonitor(controller).injectItems(stored, Actionable.MODULATE, source),
                            "Pattern should fit in network storage");
                    final ContainerPatternTerm container = openTerminal(player, cable, ForgeDirection.UP);
                    container.setTargetStack(stored.copy());
                    incoming(container.openTunnelPatternRenamerAction, StreamCodecs.empty(), null);
                    helper.assertSame(container, player.openContainer, "Empty output should not open the renamer");
                    legacyName(player);
                    assertUnchanged(helper, original, player.inventory.getStackInSlot(0));
                    assertUnchanged(helper, original, tile.getInterfaceDuality().getPatterns().getStackInSlot(0));
                    assertUnchanged(helper, original, patterns(terminal).getStackInSlot(0));
                    final IAEItemStack input = (IAEItemStack) terminal.getAEInventoryByName(StorageName.CRAFTING_INPUT)
                            .getAEStackInSlot(0);
                    assertUnchanged(helper, original, input.getItemStack());
                    final IAEItemStack stillStored = itemMonitor(controller)
                            .extractItems(stored, Actionable.SIMULATE, source);
                    helper.assertNotNull(stillStored, "Network pattern should remain stored");
                    assertUnchanged(helper, original, stillStored.getItemStack());
                    helper.assertNull(patterns(terminal).getStackInSlot(1), "Empty output should remain empty");

                    Platform.openGUI(player, tile, ForgeDirection.UNKNOWN, GuiBridge.GUI_INTERFACE);
                    helper.assertTrue(player.openContainer instanceof ContainerInterface, "Interface should open");
                    legacyName(player);
                    assertUnchanged(helper, original, tile.getInterfaceDuality().getPatterns().getStackInSlot(0));
                    helper.assertFalse(
                            GuiBridge.GUI_TUNNEL_PATTERN_RENAMER.CorrectTileOrPart(tile),
                            "The renamer should not accept an ME interface host");
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 120)
    public static void invalidContextAndChangedTerminalAreRejected(final GameTestHelper helper) {
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper, cable);
        final FakePlayer other = guiPlayer(helper, cable);
        final PartPatternTerminal terminal = addTerminal(cable, player, ForgeDirection.UP, false);
        final IInventory inventory = patterns(terminal);
        final ItemStack original = tunnelPattern();
        inventory.setInventorySlotContents(1, original.copy());

        helper.startSequence()
                .thenWaitUntil(
                        "wait for the encoding terminal",
                        80,
                        () -> assertActive(helper, terminal, "Terminal should become active"))
                .thenExecute("reject foreign players, occupied cursors and invalid containers", () -> {
                    final ContainerPatternTerm stale = openTerminal(player, cable, ForgeDirection.UP);
                    final ContainerPatternTerm container = openTerminal(player, cable, ForgeDirection.UP);
                    ContainerTunnelPatternRenamer.open(player, stale);
                    helper.assertSame(
                            container,
                            player.openContainer,
                            "An inactive terminal container should not open a rename dialog");
                    ContainerTunnelPatternRenamer.open(other, container);
                    helper.assertSame(
                            other.inventoryContainer,
                            other.openContainer,
                            "Another player's container should not open a rename dialog");
                    player.inventory.setItemStack(original.copy());
                    incoming(container.openTunnelPatternRenamerAction, StreamCodecs.empty(), null);
                    helper.assertSame(container, player.openContainer, "Occupied cursor should block renaming");
                    player.inventory.setItemStack(null);
                    container.setValidContainer(false);
                    incoming(container.openTunnelPatternRenamerAction, StreamCodecs.empty(), null);
                    helper.assertSame(
                            container,
                            player.openContainer,
                            "Invalid primary container should block renaming");

                    openRenamer(helper, openTerminal(player, cable, ForgeDirection.UP), player);
                    final ContainerTunnelPatternRenamer renamer = (ContainerTunnelPatternRenamer) player.openContainer;
                    renamer.setValidContainer(false);
                    incoming(renamer.renameAction, StreamCodecs.string(), "Should not apply");
                    assertUnchanged(helper, original, inventory.getStackInSlot(1));
                }).thenExecute("replacing the terminal must not rename an identical output in its replacement", () -> {
                    openRenamer(helper, openTerminal(player, cable, ForgeDirection.UP), player);
                    final ContainerTunnelPatternRenamer renamer = (ContainerTunnelPatternRenamer) player.openContainer;
                    cable.removePart(ForgeDirection.UP, false);
                    final PartPatternTerminal replacement = addTerminal(cable, player, ForgeDirection.UP, false);
                    patterns(replacement).setInventorySlotContents(1, original.copy());
                    incoming(renamer.renameAction, StreamCodecs.string(), "Should not apply");
                    assertUnchanged(helper, original, inventory.getStackInSlot(1));
                    assertUnchanged(helper, original, patterns(replacement).getStackInSlot(1));
                }).thenSucceed();
    }

    @GameTest(template = "interface_network", timeoutTicks = 160)
    public static void revokedCraftPermissionBlocksOpeningAndSaving(final GameTestHelper helper) {
        final TileCableBus cable = helper.assertTileEntityPresent(TileCableBus.class, "block_interface_cable");
        final FakePlayer player = guiPlayer(helper, cable);
        final FakePlayer owner = guiPlayer(helper, cable);
        final PartPatternTerminal terminal = addTerminal(cable, player, ForgeDirection.UP, false);
        helper.setBlock(0, 0, 1, AEApi.instance().definitions().blocks().security().maybeBlock().get());
        final TileSecurity security = helper.assertTileEntityPresent(TileSecurity.class, 0, 0, 1);
        security.getProxy().setOwner(owner);
        final PlayerSource ownerSource = new PlayerSource(owner, security);
        final ItemStack original = tunnelPattern();
        patterns(terminal).setInventorySlotContents(1, original.copy());

        helper.startSequence().thenWaitUntil("wait for the real security grid", 100, () -> {
            assertActive(helper, terminal, "Terminal should become active");
            assertActive(helper, security.getProxy(), "Security terminal should become active");
            helper.assertSame(
                    terminal.getGridNode().getGrid(),
                    security.getProxy().getNode().getGrid(),
                    "Security terminal should share the encoding terminal's grid");
            final ISecurityGrid permissions = terminal.getGridNode().getGrid().getCache(ISecurityGrid.class);
            helper.assertTrue(permissions.isAvailable(), "Security rules should be active");
        }).thenExecute("recheck CRAFT when opening and saving the rename dialog", () -> {
            final ISecurityGrid permissions = terminal.getGridNode().getGrid().getCache(ISecurityGrid.class);
            helper.assertFalse(SecurityCache.isPlayerOP(player.getGameProfile()), "Test player should not be OP");
            helper.assertEquals(
                    AEApi.instance().registries().players().getID(owner.getGameProfile()),
                    permissions.getOwner(),
                    "The security owner should be a different player");
            helper.assertFalse(
                    permissions.hasPermission(player, SecurityPermissions.CRAFT),
                    "Test player should initially lack CRAFT");
            final ItemStack card = AEApi.instance().definitions().items().biometricCard().maybeStack(1).get();
            final IBiometricCard biometric = (IBiometricCard) card.getItem();
            biometric.setProfile(card, player.getGameProfile());
            biometric.addPermission(card, SecurityPermissions.EXTRACT);
            biometric.addPermission(card, SecurityPermissions.CRAFT);
            IAEItemStack storedCard = AEItemStack.create(card);
            helper.assertNull(
                    security.getItemInventory().injectItems(storedCard, Actionable.MODULATE, ownerSource),
                    "Owner should be able to grant the test player's permissions");
            helper.assertTrue(permissions.hasPermission(player, SecurityPermissions.CRAFT), "CRAFT should be granted");
            final ContainerPatternTerm container = openTerminal(player, cable, ForgeDirection.UP);
            storedCard = craftPermission(helper, security, ownerSource, storedCard, false);
            helper.assertTrue(
                    permissions.hasPermission(player, SecurityPermissions.EXTRACT),
                    "Revocation should leave EXTRACT granted");
            helper.assertFalse(permissions.hasPermission(player, SecurityPermissions.CRAFT), "CRAFT should be revoked");
            incoming(container.openTunnelPatternRenamerAction, StreamCodecs.empty(), null);
            helper.assertSame(container, player.openContainer, "Revoked CRAFT should prevent opening the dialog");

            storedCard = craftPermission(helper, security, ownerSource, storedCard, true);
            openRenamer(helper, container, player);
            final ContainerTunnelPatternRenamer renamer = (ContainerTunnelPatternRenamer) player.openContainer;
            craftPermission(helper, security, ownerSource, storedCard, false);
            helper.assertFalse(
                    permissions.hasPermission(player, SecurityPermissions.CRAFT),
                    "CRAFT should be revoked again");
            incoming(renamer.renameAction, StreamCodecs.string(), "Should not apply");
            helper.assertSame(renamer, player.openContainer, "Revoked CRAFT should prevent saving");
            assertUnchanged(helper, original, patterns(terminal).getStackInSlot(1));
        }).thenSucceed();
    }

    private static IAEItemStack craftPermission(final GameTestHelper helper, final TileSecurity security,
            final PlayerSource owner, final IAEItemStack current, final boolean grant) {
        final IAEItemStack extracted = security.getItemInventory().extractItems(current, Actionable.MODULATE, owner);
        helper.assertNotNull(extracted, "Owner should be able to replace the biometric card");
        final ItemStack card = extracted.getItemStack();
        final IBiometricCard biometric = (IBiometricCard) card.getItem();
        if (grant) biometric.addPermission(card, SecurityPermissions.CRAFT);
        else biometric.removePermission(card, SecurityPermissions.CRAFT);
        final IAEItemStack replacement = AEItemStack.create(card);
        helper.assertNull(
                security.getItemInventory().injectItems(replacement, Actionable.MODULATE, owner),
                "Updated biometric card should be accepted");
        return replacement;
    }

    private static IInventory patterns(final PartPatternTerminal terminal) {
        return terminal.getInventoryByName(StorageName.CRAFTING_PATTERN.getName());
    }

    private static PartPatternTerminal addTerminal(final TileCableBus cable, final FakePlayer player,
            final ForgeDirection side, final boolean extended) {
        cable.addPart(
                (extended ? AEApi.instance().definitions().parts().patternTerminalEx()
                        : AEApi.instance().definitions().parts().patternTerminal()).maybeStack(1).get(),
                side,
                player);
        return (PartPatternTerminal) cable.getPart(side);
    }

    private static ContainerPatternTerm openTerminal(final FakePlayer player, final TileCableBus cable,
            final ForgeDirection side) {
        Platform.openGUI(
                player,
                cable,
                side,
                side == ForgeDirection.DOWN ? GuiBridge.GUI_PATTERN_TERMINAL_EX : GuiBridge.GUI_PATTERN_TERMINAL);
        if (!(player.openContainer instanceof ContainerPatternTerm terminal))
            throw new AssertionError("Pattern Encoding Terminal should open");
        return terminal;
    }

    private static void openRenamer(final GameTestHelper helper, final ContainerPatternTerm container,
            final FakePlayer player) {
        incoming(container.openTunnelPatternRenamerAction, StreamCodecs.empty(), null);
        helper.assertTrue(
                player.openContainer instanceof ContainerTunnelPatternRenamer,
                "Encoded output should open the Tunnel Pattern renamer");
    }

    private static void submitName(final GameTestHelper helper, final FakePlayer player, final String name) {
        final ContainerTunnelPatternRenamer renamer = (ContainerTunnelPatternRenamer) player.openContainer;
        incoming(renamer.renameAction, StreamCodecs.string(), name);
        helper.assertTrue(
                player.openContainer instanceof ContainerPatternTerm,
                "Saving should return to the same kind of encoding terminal");
    }

    private static <T> void incoming(final ActionHandler<T> action, final StreamCodec<T> codec, final T payload) {
        final ByteBuf buf = Unpooled.buffer();
        try {
            codec.write(buf, payload);
            action.readIncoming(SyncEndpoint.CLIENT, SyncMode.FULL, buf);
        } catch (IOException e) {
            throw new AssertionError("Container action should be encodable", e);
        } finally {
            buf.release();
        }
    }

    private static void legacyName(final FakePlayer player) {
        try {
            new PacketValueConfig("TunnelPattern.Rename", "Should not apply").serverPacketData(null, null, player);
        } catch (IOException e) {
            throw new AssertionError("Legacy packet should be encodable", e);
        }
    }

    private static void assertUnchanged(final GameTestHelper helper, final ItemStack expected, final ItemStack actual) {
        if (expected == null) {
            helper.assertNull(actual, "Removed output should remain empty");
        } else {
            helper.assertTrue(AEItemStack.create(expected).isSameType(actual), "Pattern NBT should remain unchanged");
            helper.assertEquals(expected.stackSize, actual.stackSize, "Pattern count should remain unchanged");
        }
    }

    private static void assertRenamed(final GameTestHelper helper, final ItemStack original, final ItemStack renamed,
            final String name) {
        assertUnchanged(helper, TunnelPatternRenaming.renamedCopy(original, name), renamed);
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
        tag.setString("testContents", "Original contents");
        tag.setLong("legacyTimestamp", 123456789L);
        tag.setByteArray("opaquePayload", new byte[] { 0, 1, -1 });
        ItemTunnelPattern.writeTunnelUuid(tag, UUID.randomUUID());
        pattern.setTagCompound(tag);
        pattern.setStackDisplayName("Original");
        return pattern;
    }

    // Forge FakePlayer.openGui is a no-op. Use the real server GUI bridge with a no-client connection.
    private static FakePlayer guiPlayer(final GameTestHelper helper, final TileCableBus cable) {
        final FakePlayer player = new FakePlayer(
                (WorldServer) helper.getWorld(),
                new GameProfile(UUID.randomUUID(), "tunnel_rename")) {

            @Override
            public void openGui(final Object mod, final int guiId, final World world, final int x, final int y,
                    final int z) {
                this.openContainer = (Container) GuiBridge.GUI_Handler.getServerGuiElement(guiId, this, world, x, y, z);
            }
        };
        player.setPosition(cable.xCoord + 0.5, cable.yCoord + 0.5, cable.zCoord + 0.5);
        final NetworkManager network = new NetworkManager(false);
        final EmbeddedChannel channel = new EmbeddedChannel(network);
        new NetHandlerPlayServer(MinecraftServer.getServer(), network, player);
        network.setConnectionState(EnumConnectionState.PLAY);
        // Forge permits a FakePlayer channel without an FML dispatcher; no client receives GUI synchronization.
        helper.afterTest(() -> {
            player.openContainer = player.inventoryContainer;
            channel.finish();
            Object message;
            while ((message = channel.readOutbound()) != null) ReferenceCountUtil.release(message);
        });
        return player;
    }
}
