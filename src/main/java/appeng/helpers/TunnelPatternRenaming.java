package appeng.helpers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.slot.SlotFake;
import appeng.items.misc.ItemTunnelPattern;

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
}
