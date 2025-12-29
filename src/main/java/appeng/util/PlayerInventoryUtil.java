package appeng.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.network.play.server.S2FPacketSetSlot;

import appeng.api.AEApi;
import baubles.api.BaublesApi;

/**
 * A collection of utility functions for manipulating player inventories.
 */
public class PlayerInventoryUtil {

    /**
     * Sets the specified inventory slot as the player's active (held) slot. If the target slot is in the hotbar (slots
     * 0-8), that slot is set as the active slot. If the target slot is outside the hotbar, it swaps the contents of
     * that slot with the current active slot.
     *
     * @param player The player whose active slot should be changed.
     * @param slot   The inventory slot index to make active (0-8 for hotbar, 9+ for main inventory).
     */
    public static void setSlotAsActiveSlot(EntityPlayerMP player, int slot) {
        if (slot >= 0 && slot <= 8) {
            player.inventory.currentItem = slot;
            player.playerNetServerHandler.sendPacket(new S09PacketHeldItemChange(slot));
        } else {
            swapInventorySlots(player, player.inventory.currentItem, slot);
        }
        player.inventory.markDirty();
    }

    /**
     * Moves an ItemStack from a source slot to a destination slot in the player's inventory. If the destination slot is
     * occupied, the items are swapped.
     *
     * @param player The player whose inventory is to be modified.
     * @param slot1  The index of the first ItemStack to move.
     * @param slot2  The index of the second ItemStack to move.
     */
    public static void swapInventorySlots(EntityPlayerMP player, int slot1, int slot2) {
        if (slot1 == slot2) {
            return;
        }

        // Get the stacks from both slots
        ItemStack sourceStack = player.inventory.getStackInSlot(slot1);
        ItemStack destinationStack = player.inventory.getStackInSlot(slot2);

        // Copy the item stacks to prevents any potential shared reference issue.
        if (sourceStack != null) {
            sourceStack = sourceStack.copy();
        }
        if (destinationStack != null) {
            destinationStack = destinationStack.copy();
        }

        // Set the destination slot with the source stack (even if it's null)
        player.inventory.setInventorySlotContents(slot2, sourceStack);

        // Set the source slot with the original destination stack
        player.inventory.setInventorySlotContents(slot1, destinationStack);

        // Mark the inventory as dirty to ensure changes are saved and synced
        player.inventory.markDirty();
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-2, slot2, sourceStack));
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-2, slot1, destinationStack));
    }

    /**
     * Consolidate ItemStacks from a source slot to a destination slot in the player's inventory.
     *
     * @param player          The player whose inventory is to be modified.
     * @param sourceSlot      The index of the slot to move the item from.
     * @param destinationSlot The index of the slot to move the item to.
     * @return the consolidated item stack
     */
    public static ItemStack consolidateItemStacks(EntityPlayerMP player, int sourceSlot, int destinationSlot) {
        ItemStack sourceStack = player.inventory.getStackInSlot(sourceSlot);
        ItemStack destinationStack = player.inventory.getStackInSlot(destinationSlot);

        // We can only consolidate if both the source and destination slots have item stacks.
        if (sourceStack == null || destinationStack == null) {
            return destinationStack;
        }

        if (!sourceStack.isItemEqual(destinationStack)
                || !ItemStack.areItemStackTagsEqual(sourceStack, destinationStack)) {
            return destinationStack;
        }

        // Copy the item stacks to prevents any potential shared reference issue.
        sourceStack = sourceStack.copy();
        destinationStack = destinationStack.copy();

        int missingQuantity = destinationStack.getMaxStackSize() - destinationStack.stackSize;
        if (missingQuantity >= sourceStack.stackSize) {
            destinationStack.stackSize = destinationStack.stackSize + sourceStack.stackSize;
            sourceStack = null;
        } else {
            sourceStack.stackSize -= missingQuantity;
            destinationStack.stackSize += missingQuantity;
        }

        // Update the inventory stacks
        player.inventory.setInventorySlotContents(destinationSlot, destinationStack);
        player.inventory.setInventorySlotContents(sourceSlot, sourceStack);

        // Mark the inventory as dirty to ensure changes are saved and synced
        player.inventory.markDirty();
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-2, destinationSlot, destinationStack));
        player.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(-2, sourceSlot, sourceStack));

        return player.inventory.getStackInSlot(destinationSlot);
    }

    /**
     * Finds the first wireless terminal in the player's inventory.
     *
     * @param player the player to check
     * @return the wireless terminal ItemStack, or null if not found
     */
    public static ItemStack getFirstWirelessTerminal(EntityPlayer player) {
        // Check bauble slots
        if (Platform.isBaublesLoaded) {
            ItemStack terminal = getWirelessTerminalFromBaubles(player);
            if (terminal != null) {
                return terminal;
            }
        }

        // Check hotbar and main inventory (slots 0-35)
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && AEApi.instance().registries().wireless().isWirelessTerminal(stack)) {
                return stack;
            }
        }

        return null;
    }

    /**
     * Finds the first empty hotbar slot.
     *
     * @param player the player whose inventory to check
     * @return the slot number of the first empty hotbar slot, otherwise -1
     */
    public static int getFirstEmptyHotbarSlot(EntityPlayer player) {
        for (int i = 0; i <= 8; ++i) {
            if (player.inventory.mainInventory[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first wireless terminal in the player's bauble slots.
     *
     * @param player the player to check
     * @return the wireless terminal ItemStack, or null if not found
     */
    @cpw.mods.fml.common.Optional.Method(modid = "Baubles|Expanded")
    public static ItemStack getWirelessTerminalFromBaubles(EntityPlayer player) {
        IInventory baubles = BaublesApi.getBaubles(player);
        if (baubles != null) {
            for (int i = 0; i < baubles.getSizeInventory(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack != null && AEApi.instance().registries().wireless().isWirelessTerminal(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }
}
