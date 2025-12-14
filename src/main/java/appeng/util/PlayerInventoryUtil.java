package appeng.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;

import appeng.api.AEApi;
import baubles.api.BaublesApi;

/**
 * A collection of utility functions for manipulating player inventories.
 */
public class PlayerInventoryUtil {

    /**
     * Finds the first empty slot in the player's inventory, searching in reverse order (from the last slot to the
     * first). This prioritizes filling the inventory from the end, which helps keep commonly accessed hotbar slots
     * free.
     *
     * @param inventory The player's inventory to search.
     * @return The index of the first empty slot found (searching backwards), or -1 if the inventory is full.
     */
    public static int getFirstEmptyStackReverse(InventoryPlayer inventory) {
        for (int i = inventory.mainInventory.length - 1; i >= 0; --i) {
            if (inventory.mainInventory[i] == null) {
                return i;
            }
        }

        return -1;
    }

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
            swapInventorySlots(player.inventory, player.inventory.currentItem, slot);
        }
        player.inventory.markDirty();
    }

    /**
     * Moves an ItemStack from a source slot to a destination slot in the player's inventory. If the destination slot is
     * occupied, the items are swapped.
     *
     * @param inventory The player's inventory.
     * @param slot1     The index of the first ItemStack to move.
     * @param slot2     The index of the second ItemStack to move.
     */
    public static void swapInventorySlots(InventoryPlayer inventory, int slot1, int slot2) {
        if (slot1 == slot2) {
            return;
        }

        // Get the stacks from both slots
        ItemStack sourceStack = inventory.getStackInSlot(slot1);
        ItemStack destinationStack = inventory.getStackInSlot(slot2);

        // Set the destination slot with the source stack (even if it's null)
        inventory.setInventorySlotContents(slot2, sourceStack);

        // Set the source slot with the original destination stack
        inventory.setInventorySlotContents(slot1, destinationStack);

        // Mark the inventory as dirty to ensure changes are saved and synced
        inventory.markDirty();
    }

    /**
     * Consolidate ItemStacks from a source slot to a destination slot in the player's inventory.
     *
     * @param inventory       The player's inventory.
     * @param sourceSlot      The index of the slot to move the item from.
     * @param destinationSlot The index of the slot to move the item to.
     */
    public static void consolidateItemStacks(InventoryPlayer inventory, int sourceSlot, int destinationSlot) {
        ItemStack sourceStack = inventory.getStackInSlot(sourceSlot);
        ItemStack destinationStack = inventory.getStackInSlot(destinationSlot);

        if (!sourceStack.isItemEqual(destinationStack)
                || !ItemStack.areItemStackTagsEqual(sourceStack, destinationStack)) {
            return;
        }

        int missingQuantity = destinationStack.getMaxStackSize() - destinationStack.stackSize;
        if (missingQuantity >= sourceStack.stackSize) {
            destinationStack.stackSize = destinationStack.stackSize + sourceStack.stackSize;
            sourceStack = null;
        } else {
            sourceStack.stackSize -= missingQuantity;
            destinationStack.stackSize += missingQuantity;
        }

        // Update the inventory stacks
        inventory.setInventorySlotContents(destinationSlot, destinationStack);
        inventory.setInventorySlotContents(sourceSlot, sourceStack);

        // Mark the inventory as dirty to ensure changes are saved and synced
        inventory.markDirty();
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
