package appeng.helpers;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.slot.SlotFake;
import appeng.items.misc.ItemTunnelPattern;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public final class TunnelPatternRenaming {

    public static final int MAX_NAME_LENGTH = 64;

    private TunnelPatternRenaming() {}

    public static ItemStack renamedCopy(final ItemStack pattern, final String name) {
        final ItemStack renamed = pattern.copy();
        final String filteredName = ChatAllowedCharacters.filerAllowedCharacters(name);
        final String newName = filteredName.substring(0, Math.min(MAX_NAME_LENGTH, filteredName.length()));
        if (newName.isEmpty()) {
            renamed.func_135074_t();
        } else {
            renamed.setStackDisplayName(newName);
        }
        return renamed;
    }

    public static boolean renameSlot(final Slot slot, final IAEItemStack expected, final String name,
            final EntityPlayer player) {
        if (slot instanceof SlotFake || !slot.canTakeStack(player) || expected == null) return false;
        final ItemStack current = slot.getStack();
        if (ItemTunnelPattern.getTunnelUuid(current) == null || !expected.isSameType(current)
                || current.stackSize != expected.getStackSize())
            return false;

        slot.putStack(renamedCopy(current, name));
        return true;
    }

    public static boolean renameSlot(final Iterable<Slot> slots, final IInventory sourceInventory, final int sourceSlot,
            final IAEItemStack expected, final String name, final EntityPlayer player) {
        // Container slot numbers can move when reopening a GUI with a different toolbox layout.
        for (final Slot slot : slots) {
            if (slot.inventory == sourceInventory && slot.getSlotIndex() == sourceSlot
                    && !(slot instanceof SlotFake)
                    && slot.canTakeStack(player))
                return renameSlot(slot, expected, name, player);
        }
        return false;
    }

    /**
     * Rename one precisely matching stored pattern. A rejected replacement restores the original item; if storage also
     * rejects that rollback, the caller receives the original item to return to the player.
     */
    public static boolean renameStored(final IEnergySource power, final IMEInventory<IAEItemStack> inventory,
            final IAEItemStack expected, final String name, final BaseActionSource source,
            final Consumer<IAEItemStack> returnToPlayer) {
        if (power == null || inventory == null
                || expected == null
                || ItemTunnelPattern.getTunnelUuid(expected.getItemStack()) == null)
            return false;

        final IAEItemStack request = expected.copy().setStackSize(1);
        final IAEItemStack renamed = AEItemStack.create(renamedCopy(request.getItemStack(), name));
        if (request.isSameType(renamed)) return true;

        // Extraction and insertion each cost one AE. Check both before removing the original item.
        if (power.extractAEPower(2, Actionable.SIMULATE, PowerMultiplier.CONFIG) < 2) return false;
        final IAEItemStack extracted = Platform.poweredExtraction(power, inventory, request, source);
        if (extracted == null) return false;

        final IAEItemStack remainder = Platform.poweredInsert(power, inventory, renamed, source);
        if (remainder == null) return true;

        final IAEItemStack restoreRemainder = inventory.injectItems(extracted, Actionable.MODULATE, source);
        if (restoreRemainder != null) returnToPlayer.accept(restoreRemainder);
        return false;
    }
}
