package appeng.test.helpers;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.container.TunnelPatternRenamerHost;
import appeng.container.slot.SlotFake;
import appeng.core.sync.GuiBridge;
import appeng.helpers.TunnelPatternRenaming;
import appeng.items.misc.ItemTunnelPattern;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;

public class TunnelPatternRenamingFunctionalTest {

    private static final BaseActionSource SOURCE = new BaseActionSource();

    @Test
    void renamesOnePreciseItemWithoutChangingSameUuidVariants() {
        final UUID uuid = UUID.randomUUID();
        final IAEItemStack original = pattern(uuid, "Original", "one");
        final IAEItemStack otherName = pattern(uuid, "Other", "one");
        final IAEItemStack otherContents = pattern(uuid, "Original", "two");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(original.copy().setStackSize(4));
        inventory.items.add(otherName.copy().setStackSize(2));
        inventory.items.add(otherContents.copy().setStackSize(3));
        final TestPower power = new TestPower(20);
        final List<IAEItemStack> returned = new ArrayList<>();

        assertTrue(rename(power, inventory, original, "Renamed", returned));

        assertEquals(3, inventory.count(original));
        assertEquals(1, inventory.count(renamed(original, "Renamed")));
        assertEquals(2, inventory.count(otherName));
        assertEquals(3, inventory.count(otherContents));
        assertEquals(9, inventory.total());
        assertEquals(18, power.remaining);
        assertTrue(returned.isEmpty());
        assertEquals("Original", original.getItemStack().getDisplayName());
    }

    @Test
    void rejectedReplacementRestoresTheOriginalExactlyOnce() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(original);
        inventory.reject = input -> !input.isSameType(original);
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(new TestPower(20), inventory, original, "Renamed", returned));

        assertEquals(1, inventory.count(original));
        assertEquals(0, inventory.count(renamed(original, "Renamed")));
        assertEquals(1, inventory.total());
        assertTrue(returned.isEmpty());
    }

    @Test
    void rejectedRollbackReturnsTheUnmodifiedItemToThePlayer() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(original);
        inventory.reject = input -> true;
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(new TestPower(20), inventory, original, "Renamed", returned));

        assertEquals(0, inventory.total());
        assertEquals(1, returned.size());
        assertTrue(returned.get(0).isSameType(original));
        assertEquals(1, returned.get(0).getStackSize());
    }

    @Test
    void insufficientPowerDoesNotExtractOrModifyAnItem() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(original);
        final TestPower power = new TestPower(1);
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(power, inventory, original, "Renamed", returned));

        assertEquals(1, inventory.count(original));
        assertEquals(0, inventory.extractions);
        assertEquals(1, power.remaining);
        assertTrue(returned.isEmpty());
    }

    @Test
    void powerLostAfterExtractionRollsBackWithoutLosingTheItem() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(original);
        final TestPower power = new TestPower(2);
        inventory.afterExtraction = () -> power.remaining = 0;
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(power, inventory, original, "Renamed", returned));

        assertEquals(1, inventory.count(original));
        assertEquals(1, inventory.total());
        assertTrue(returned.isEmpty());
    }

    @Test
    void removalWhileTheRenameScreenIsOpenDoesNotCreateAnItem() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final TestInventory inventory = new TestInventory();
        final TestPower power = new TestPower(2);
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(power, inventory, original, "Renamed", returned));

        assertEquals(0, inventory.total());
        assertEquals(2, power.remaining);
        assertTrue(returned.isEmpty());
    }

    @Test
    void changingTheStoredDefinitionDoesNotRenameItsSameUuidReplacement() {
        final UUID uuid = UUID.randomUUID();
        final IAEItemStack original = pattern(uuid, "Original", "old contents");
        final IAEItemStack replacement = pattern(uuid, "Original", "new contents");
        final TestInventory inventory = new TestInventory();
        inventory.items.add(replacement);
        final List<IAEItemStack> returned = new ArrayList<>();

        assertFalse(rename(new TestPower(2), inventory, original, "Renamed", returned));

        assertEquals(1, inventory.count(replacement));
        assertEquals(1, inventory.total());
        assertTrue(returned.isEmpty());
    }

    @Test
    void localSlotRenamePreservesTheEntireStackAndItsEncodedData() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents").setStackSize(8);
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        inventory.setInventorySlotContents(0, original.getItemStack());
        final Slot slot = new Slot(inventory, 0, 0, 0);

        assertTrue(TunnelPatternRenaming.renameSlot(slot, original, "Renamed", null));

        assertEquals(8, slot.getStack().stackSize);
        assertTrue(renamed(original, "Renamed").isSameType(slot.getStack()));
        assertEquals("Original", original.getItemStack().getDisplayName());
    }

    @Test
    void localSlotRenameRejectsAChangedSameUuidDefinition() {
        final UUID uuid = UUID.randomUUID();
        final IAEItemStack original = pattern(uuid, "Original", "old contents");
        final IAEItemStack replacement = pattern(uuid, "Original", "new contents");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 1);
        inventory.setInventorySlotContents(0, replacement.getItemStack());

        assertFalse(TunnelPatternRenaming.renameSlot(new Slot(inventory, 0, 0, 0), original, "Renamed", null));
        assertTrue(replacement.isSameType(inventory.getStackInSlot(0)));
    }

    @Test
    void changedContainerLayoutRenamesOnlyTheOriginalInventorySlot() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final AppEngInternalInventory inventory = new AppEngInternalInventory(null, 27);
        final AppEngInternalInventory otherInventory = new AppEngInternalInventory(null, 27);
        inventory.setInventorySlotContents(9, original.getItemStack());
        inventory.setInventorySlotContents(18, original.getItemStack());
        otherInventory.setInventorySlotContents(18, original.getItemStack());
        final List<Slot> reopenedSlots = Arrays.asList(
                new Slot(otherInventory, 18, 0, 0),
                new Slot(inventory, 9, 0, 0),
                new Slot(inventory, 18, 0, 0));

        assertTrue(TunnelPatternRenaming.renameSlot(reopenedSlots, inventory, 18, original, "Renamed", null));

        assertTrue(original.isSameType(inventory.getStackInSlot(9)));
        assertTrue(original.isSameType(otherInventory.getStackInSlot(18)));
        assertTrue(renamed(original, "Renamed").isSameType(inventory.getStackInSlot(18)));
        assertEquals(
                3,
                inventory.getStackInSlot(9).stackSize + inventory.getStackInSlot(18).stackSize
                        + otherInventory.getStackInSlot(18).stackSize);
    }

    @Test
    void missingSourceSlotDoesNotRenameAnIdenticalPatternElsewhere() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final AppEngInternalInventory source = new AppEngInternalInventory(null, 27);
        final AppEngInternalInventory replacement = new AppEngInternalInventory(null, 27);
        replacement.setInventorySlotContents(18, original.getItemStack());

        assertFalse(
                TunnelPatternRenaming.renameSlot(
                        Arrays.asList(new Slot(replacement, 18, 0, 0)),
                        source,
                        18,
                        original,
                        "Renamed",
                        null));

        assertTrue(original.isSameType(replacement.getStackInSlot(18)));
        assertEquals(1, replacement.getStackInSlot(18).stackSize);
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
    void namesAreBoundedOnTheServerAndAnEmptyNameClearsTheCustomName() {
        final IAEItemStack original = pattern(UUID.randomUUID(), "Original", "contents");
        final ItemStack renamed = TunnelPatternRenaming
                .renamedCopy(original.getItemStack(), new String(new char[100]).replace('\0', 'A'));
        assertEquals(TunnelPatternRenaming.MAX_NAME_LENGTH, renamed.getDisplayName().length());
        final ItemStack cleared = TunnelPatternRenaming.renamedCopy(original.getItemStack(), "");
        assertFalse(cleared.hasDisplayName());
        assertEquals(
                ItemTunnelPattern.getTunnelUuid(original.getItemStack()),
                ItemTunnelPattern.getTunnelUuid(cleared));
        assertEquals("contents", cleared.getTagCompound().getString("testContents"));
    }

    @Test
    void renamerKeepsTheLegacyTerminalContractAndRejectsUnrelatedHosts() {
        assertFalse(GuiBridge.GUI_PATTERN_ITEM_RENAMER.CorrectTileOrPart(new Object()));
        assertFalse(GuiBridge.GUI_TUNNEL_PATTERN_RENAMER.CorrectTileOrPart(new Object()));
        assertFalse(TunnelPatternRenamerHost.supports(null));
        assertThrows(IllegalArgumentException.class, () -> TunnelPatternRenamerHost.from(new Object()));
        final TileEntity tile = new TileEntity();
        assertSame(tile, TunnelPatternRenamerHost.from(tile).getAnchor());
    }

    private static boolean rename(final TestPower power, final TestInventory inventory, final IAEItemStack original,
            final String name, final List<IAEItemStack> returned) {
        return TunnelPatternRenaming.renameStored(power, inventory, original, name, SOURCE, returned::add);
    }

    private static IAEItemStack renamed(final IAEItemStack original, final String name) {
        return AEItemStack.create(TunnelPatternRenaming.renamedCopy(original.getItemStack(), name));
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

    private static final class TestPower implements IEnergySource {

        private double remaining;

        private TestPower(final double remaining) {
            this.remaining = remaining;
        }

        @Override
        public double extractAEPower(final double amount, final Actionable mode, final PowerMultiplier multiplier) {
            final double extracted = Math.min(amount, this.remaining);
            if (mode == Actionable.MODULATE) this.remaining -= extracted;
            return extracted;
        }
    }

    private static final class TestInventory implements IMEInventory<IAEItemStack> {

        private final ItemList items = new ItemList();
        private Predicate<IAEItemStack> reject = input -> false;
        private Runnable afterExtraction = () -> {};
        private int extractions;

        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode,
                final BaseActionSource source) {
            if (this.reject.test(input)) return input;
            if (mode == Actionable.MODULATE) this.items.add(input);
            return null;
        }

        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode,
                final BaseActionSource source) {
            final IAEItemStack available = this.items.findPrecise(request);
            if (available == null || available.getStackSize() == 0) return null;
            final IAEItemStack extracted = request.copy()
                    .setStackSize(Math.min(request.getStackSize(), available.getStackSize()));
            if (mode == Actionable.MODULATE) {
                available.decStackSize(extracted.getStackSize());
                this.extractions++;
                this.afterExtraction.run();
            }
            return extracted;
        }

        @Override
        public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out, final int iteration) {
            this.items.forEach(out::add);
            return out;
        }

        @Override
        public IAEStackType<?> getStackType() {
            return ITEM_STACK_TYPE;
        }

        private long count(final IAEItemStack stack) {
            final IAEItemStack stored = this.items.findPrecise(stack);
            return stored == null ? 0 : stored.getStackSize();
        }

        private long total() {
            long total = 0;
            for (final IAEItemStack item : this.items) total += item.getStackSize();
            return total;
        }
    }
}
