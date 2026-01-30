package appeng.core.sync.packets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.PlayerInventoryUtil;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPickBlock extends AppEngPacket {

    private final ItemStack pickedBlock;

    // Reflection
    public PacketPickBlock(final ByteBuf stream) {
        this.pickedBlock = ByteBufUtils.readItemStack(stream);
    }

    // Public API
    public PacketPickBlock(ItemStack pickedBlock) {
        this.pickedBlock = pickedBlock;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        ByteBufUtils.writeItemStack(data, pickedBlock);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;

        if (pickedBlock == null) {
            return;
        }

        // 1. Scan through the player's main inventory to categorize existing stacks of the target block:
        // - If a full stack (stackSize >= maxStackSize) is found, record its slot and stop searching.
        // This indicates the player already has the maximum possible stack, so no withdrawal is needed.
        // - Otherwise, collect all partial stack slots (stacks that match the item but aren't full).
        // Partial stacks will be consolidated in a later step.
        int fullStackSlot = -1;
        List<Integer> partialStackSlotsList = new ArrayList<>();
        for (int i = 0; i < sender.inventory.mainInventory.length; i++) {
            ItemStack stackInSlot = sender.inventory.mainInventory[i];
            if (stackInSlot != null && stackInSlot.isItemEqual(pickedBlock)
                    && ItemStack.areItemStackTagsEqual(stackInSlot, pickedBlock)) {
                if (stackInSlot.stackSize >= stackInSlot.getMaxStackSize()) {
                    fullStackSlot = i;
                    break; // Found a full stack, no need to continue consolidating.
                }
                partialStackSlotsList.add(i);
            }
        }

        // 2. If a full stack already exists, move it into the correct slot
        if (fullStackSlot >= 0) {
            movePickBlockItemStack(sender, fullStackSlot);
            return;
        }

        // 3. If there are no partial stacks and the player's inventory is full,
        // then return since we cannot add a retrieved stack to a full inventory
        int nextEmptySlot = sender.inventory.getFirstEmptyStack();
        if (partialStackSlotsList.isEmpty() && nextEmptySlot == -1) {
            return;
        }

        // 4. Consolidate all partial stacks of target block into 1 ItemStack.
        // If a full stack is obtained, set it as the active slot and return.
        ItemStack consolidatedStack = null;
        int consolidatedStackSlot = -1;
        for (Integer partialStackSlot : partialStackSlotsList) {
            if (consolidatedStack == null) {
                consolidatedStack = sender.inventory.getStackInSlot(partialStackSlot);
                consolidatedStackSlot = partialStackSlot;
            } else {
                var newStack = PlayerInventoryUtil
                        .consolidateItemStacks(sender, partialStackSlot, consolidatedStackSlot);
                if (newStack != null) {
                    consolidatedStack = newStack;
                }
            }

            // Check if we created a full stack of items
            if (consolidatedStack.stackSize == consolidatedStack.getMaxStackSize()) {
                movePickBlockItemStack(sender, consolidatedStackSlot);
                return;
            }
        }

        // Determine which of consolidated ItemStack or nextEmptySlot to use as our pick block ItemStack
        int pickBlockSlot = consolidatedStack == null ? nextEmptySlot : consolidatedStackSlot;
        ItemStack pickBlockItemStack = consolidatedStack;

        // 5. Calculate withdrawal amount
        int amountToWithdraw = pickBlockItemStack == null ? pickedBlock.getMaxStackSize()
                : pickedBlock.getMaxStackSize() - pickBlockItemStack.stackSize;
        if (amountToWithdraw <= 0) {
            return;
        }

        // Ensure the player has a wireless terminal with access to an AE2 network.
        ItemStack wirelessTerminal = PlayerInventoryUtil.getFirstWirelessTerminal(sender);
        if (wirelessTerminal == null) {
            movePickBlockItemStack(sender, pickBlockSlot);
            sender.addChatMessage(PlayerMessages.PickBlockTerminalNotFound.toChat());
            return;
        }
        var wirelessInventory = getWirelessItemInventory(sender, wirelessTerminal);
        if (wirelessInventory == null) {
            movePickBlockItemStack(sender, pickBlockSlot);
            return;
        }

        // Create an IAEItemStack for the target block with the calculated amount
        ItemStack targetItemStack = pickedBlock.copy();
        targetItemStack.stackSize = amountToWithdraw;
        IAEItemStack targetAeItemStack = AEApi.instance().storage().createItemStack(targetItemStack);
        if (targetAeItemStack == null) {
            movePickBlockItemStack(sender, pickBlockSlot);
            return;
        }

        // 6. Extract items from the network
        PlayerSource source = new PlayerSource(player, null);
        IAEStack<IAEItemStack> extractedStack = wirelessInventory
                .extractItems(targetAeItemStack, Actionable.MODULATE, source);
        if (extractedStack instanceof IAEItemStack extractedAeItemStack && extractedStack.getStackSize() > 0) {
            ItemStack itemsToGive = extractedAeItemStack.getItemStack();
            // Update the player's inventory with the withdrawn items
            if (itemsToGive != null && itemsToGive.stackSize > 0) {
                if (pickBlockItemStack == null) {
                    pickBlockItemStack = itemsToGive;
                    sender.inventory.setInventorySlotContents(pickBlockSlot, pickBlockItemStack);
                } else {
                    pickBlockItemStack.stackSize += itemsToGive.stackSize;
                }
            }
        }

        // If the pick block item stack is still null, we had no consolidated stacks and did not withdraw any items.
        // So, we do nothing.
        if (pickBlockItemStack == null) {
            return;
        }

        // Move the target item stack to the correct slot in the player's hotbar.
        movePickBlockItemStack(sender, pickBlockSlot);
    }

    private IMEInventoryHandler<IAEItemStack> getWirelessItemInventory(EntityPlayer player,
            ItemStack wirelessTerminal) {
        if (wirelessTerminal == null) {
            return null;
        }

        if (!AEApi.instance().registries().wireless().performCheck(wirelessTerminal, player)) {
            return null;
        }

        var wirelessHandler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(wirelessTerminal);
        if (wirelessHandler == null) {
            return null;
        }

        var encryptionKey = wirelessHandler.getEncryptionKey(wirelessTerminal);
        if (encryptionKey == null) {
            return null;
        }

        var securityTerminal = (IGridHost) AEApi.instance().registries().locatable()
                .getLocatableBy(Long.parseLong(encryptionKey));
        if (securityTerminal == null) {
            return null;
        }

        var wirelessGridNode = securityTerminal.getGridNode(ForgeDirection.UNKNOWN);
        if (wirelessGridNode == null) {
            return null;
        }

        var wirelessGrid = wirelessGridNode.getGrid();
        if (wirelessGrid == null) {
            return null;
        }

        IStorageGrid wirelessGridCache = wirelessGrid.getCache(IStorageGrid.class);
        if (wirelessGridCache == null) {
            return null;
        }

        return wirelessGridCache.getItemInventory();
    }

    /**
     * Set the position of the pick block ItemStack. If it is in the hotbar, set the active slot to the pick block slot.
     * If it is not in the hotbar, move it to the first empty slot in the hotbar and set it as active slot. If there is
     * no available hotbar slot, swap it with the active slot.
     *
     * @param player                 the player whose inventory to be modified
     * @param pickBlockInventorySlot the inventory slot of the ItemStack to move
     */
    private void movePickBlockItemStack(EntityPlayerMP player, int pickBlockInventorySlot) {
        if (player.inventory.getStackInSlot(pickBlockInventorySlot) == null) {
            return;
        }
        var firstEmptyHotbarSlot = PlayerInventoryUtil.getFirstEmptyHotbarSlot(player);
        if (pickBlockInventorySlot <= 8) {
            PlayerInventoryUtil.setSlotAsActiveSlot(player, pickBlockInventorySlot);
        } else if (firstEmptyHotbarSlot >= 0) {
            player.inventory.currentItem = firstEmptyHotbarSlot;
            player.playerNetServerHandler.sendPacket(new S09PacketHeldItemChange(firstEmptyHotbarSlot));
            PlayerInventoryUtil.setSlotAsActiveSlot(player, pickBlockInventorySlot);
        } else {
            PlayerInventoryUtil.setSlotAsActiveSlot(player, pickBlockInventorySlot);
        }
    }
}
