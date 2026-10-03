package appeng.test.helpers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.slot.SlotFake;
import appeng.core.sync.GuiBridge;
import appeng.helpers.InventoryAction;
import appeng.helpers.MonitorableAction;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.item.AEItemStack;

public class TunnelPatternRenamingFunctionalTest {

    @Test
    void renameChangesOnlyTheNameOfTheMatchingStack() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents").setStackSize(4);
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 2);
        inventory.setInventorySlotContents(0, original.getItemStack());
        inventory.setInventorySlotContents(1, original.getItemStack());

        assertTrue(TunnelPatternRenaming.renameSlot(new Slot(inventory, 0, 0, 0), original, "Renamed", null));

        final ItemStack renamed = inventory.getStackInSlot(0);
        assertTrue(
                AEItemStack.create(TunnelPatternRenaming.renamedCopy(original.getItemStack(), "Renamed"))
                        .isSameType(renamed));
        assertEquals(4, renamed.stackSize);
        assertEquals(
                ItemTunnelPattern.getTunnelUuid(original.getItemStack()),
                ItemTunnelPattern.getTunnelUuid(renamed));
        assertTrue(original.isSameType(inventory.getStackInSlot(1)));
        assertEquals("Original", original.getItemStack().getDisplayName());
    }

    @Test
    void sameUuidWithDifferentContentsOrNameIsRejected() {
        final UUID uuid = UUID.randomUUID();
        final IAEItemStack original = pattern(uuid, "Original", "contents");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        for (final IAEItemStack replacement : Arrays
                .asList(pattern(uuid, "Original", "changed contents"), pattern(uuid, "Changed name", "contents"))) {
            inventory.setInventorySlotContents(0, replacement.getItemStack());

            assertFalse(TunnelPatternRenaming.renameSlot(new Slot(inventory, 0, 0, 0), original, "Renamed", null));
            assertTrue(replacement.isSameType(inventory.getStackInSlot(0)));
        }
    }

    @Test
    void changedCountOrRemovedPatternIsRejected() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        inventory.setInventorySlotContents(0, original.copy().setStackSize(2).getItemStack());

        assertFalse(TunnelPatternRenaming.renameSlot(new Slot(inventory, 0, 0, 0), original, "Renamed", null));
        assertEquals(2, inventory.getStackInSlot(0).stackSize);
        inventory.setInventorySlotContents(0, null);
        assertFalse(TunnelPatternRenaming.renameSlot(new Slot(inventory, 0, 0, 0), original, "Renamed", null));
        assertNull(inventory.getStackInSlot(0));
    }

    @Test
    void fakeAndReadOnlySlotsCannotBeRenamed() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        inventory.setInventorySlotContents(0, original.getItemStack());
        final Slot readOnly = new Slot(inventory, 0, 0, 0) {

            @Override
            public boolean canTakeStack(final EntityPlayer player) {
                return false;
            }
        };

        assertFalse(TunnelPatternRenaming.renameSlot(readOnly, original, "Renamed", null));
        assertFalse(TunnelPatternRenaming.renameSlot(new SlotFake(inventory, 0, 0, 0), original, "Renamed", null));
        assertTrue(original.isSameType(inventory.getStackInSlot(0)));
    }

    @Test
    void invalidTunnelIdentityIsRejected() {
        final ItemStack invalid = pattern(UUID.randomUUID(), "Original", "contents").getItemStack();
        invalid.getTagCompound().setString(ItemTunnelPattern.TAG_TUNNEL_UUID, "invalid");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        inventory.setInventorySlotContents(0, invalid);

        assertFalse(
                TunnelPatternRenaming
                        .renameSlot(new Slot(inventory, 0, 0, 0), AEItemStack.create(invalid), "Renamed", null));
        assertEquals("Original", inventory.getStackInSlot(0).getDisplayName());
    }

    @Test
    void namesAreFilteredBoundedAndEmptyNamesClearTheCustomName() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final ItemStack filtered = TunnelPatternRenaming.renamedCopy(original.getItemStack(), "A\u00a7\n\u0000B");
        assertEquals("AB", filtered.getDisplayName());
        final ItemStack renamed = TunnelPatternRenaming.renamedCopy(original.getItemStack(), Strings.repeat("A", 100));
        assertEquals(TunnelPatternRenaming.MAX_NAME_LENGTH, renamed.getDisplayName().length());
        final ItemStack cleared = TunnelPatternRenaming.renamedCopy(original.getItemStack(), "");
        assertFalse(cleared.hasDisplayName());
        assertEquals(
                ItemTunnelPattern.getTunnelUuid(original.getItemStack()),
                ItemTunnelPattern.getTunnelUuid(cleared));
        assertEquals("contents", cleared.getTagCompound().getString("testContents"));
        assertEquals("Original", original.getItemStack().getDisplayName());
    }

    @Test
    void unrelatedHostsAndGeneralInventoryActionsDoNotExposeRenaming() {
        assertFalse(GuiBridge.GUI_PATTERN_ITEM_RENAMER.CorrectTileOrPart(new Object()));
        assertFalse(GuiBridge.GUI_TUNNEL_PATTERN_RENAMER.CorrectTileOrPart(new Object()));
        assertFalse(GuiBridge.GUI_TUNNEL_PATTERN_RENAMER.CorrectTileOrPart(new TileEntity()));
        assertFalse(
                Arrays.stream(InventoryAction.values())
                        .anyMatch(action -> action.name().equals("RENAME_TUNNEL_PATTERN")));
        assertFalse(
                Arrays.stream(MonitorableAction.values())
                        .anyMatch(action -> action.name().equals("RENAME_TUNNEL_PATTERN")));
    }

    private static IAEItemStack pattern(final UUID uuid, final String name, final String contents) {
        final ItemStack pattern = AEApi.instance().definitions().items().encodedTunnelPattern().maybeStack(1).get();
        final NBTTagCompound tag = new NBTTagCompound();
        ItemTunnelPattern.writeTunnelUuid(tag, uuid);
        tag.setString("testContents", contents);
        pattern.setTagCompound(tag);
        pattern.setStackDisplayName(name);
        return AEItemStack.create(pattern);
    }
}
