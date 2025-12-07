package appeng.core.sync.packets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.PlayerInventoryUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPickBlock extends AppEngPacket {

    private final int blockX;
    private final int blockY;
    private final int blockZ;

    // Reflection
    public PacketPickBlock(final ByteBuf stream) {
        this.blockX = stream.readInt();
        this.blockY = stream.readInt();
        this.blockZ = stream.readInt();
    }

    // Public API
    public PacketPickBlock(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(this.blockX);
        data.writeInt(this.blockY);
        data.writeInt(this.blockZ);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;

        // Ensure the player has a wireless terminal with access to an AE2 network.
        ItemStack wirelessTerminal = PlayerInventoryUtil.getFirstWirelessTerminal(sender);
        if (wirelessTerminal == null) {
            sender.addChatMessage(new ChatComponentText("Could not access AE2 Network."));
            return;
        }

        var wirelessInventory = getWirelessItemInventory(wirelessTerminal);
        if (wirelessInventory == null) {
            sender.addChatMessage(new ChatComponentText("Could not access AE2 Network."));
            return;
        }

        // Get the target block
        World world = player.worldObj;
        Block targetBlock = world.getBlock(this.blockX, this.blockY, this.blockZ);
        if (targetBlock == Blocks.air) {
            return; // Don't try to withdraw air
        }

        Item targetItem = Item.getItemFromBlock(targetBlock);
        if (targetItem == null) {
            return;
        }

        // Check player inventory for existing stacks to determine how much to withdraw.
        ItemStack itemToFind = new ItemStack(
                targetItem,
                1,
                world.getBlockMetadata(this.blockX, this.blockY, this.blockZ));

        // 1. Scan through the player's main inventory to categorize existing stacks of the target block:
        // - If a full stack (stackSize >= maxStackSize) is found, record its slot and stop searching.
        // This indicates the player already has the maximum possible stack, so no withdrawal is needed.
        // - Otherwise, collect all partial stack slots (stacks that match the item but aren't full).
        // Partial stacks will be consolidated in a later step.
        int fullStackSlot = -1;
        List<Integer> partialStackSlotsList = new ArrayList<>();
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stackInSlot = player.inventory.mainInventory[i];
            if (stackInSlot != null && stackInSlot.isItemEqual(itemToFind)
                    && ItemStack.areItemStackTagsEqual(stackInSlot, itemToFind)) {
                if (stackInSlot.stackSize >= stackInSlot.getMaxStackSize()) {
                    fullStackSlot = i;
                    break; // Found a full stack, no need to do anything.
                }
                partialStackSlotsList.add(i);
            }
        }

        // 2. If a full stack already exists, put in active slot and return.
        if (fullStackSlot >= 0) {
            PlayerInventoryUtil.setSlotAsActiveSlot(sender, fullStackSlot);
            return;
        }

        // 3. If there are no partial stacks and the player's inventory is full,
        // then return since we cannot add a retrieved stack to a full inventory
        int nextEmptySlot = PlayerInventoryUtil.getFirstEmptyStackReverse(player.inventory);
        if (partialStackSlotsList.isEmpty() && nextEmptySlot == -1) {
            return;
        }

        // 4. Consolidate all partial stacks of target block into 1 ItemStack.
        // If a full stack is obtained, set it as the active slot and return.
        ItemStack consolidatedStack = null;
        int consolidatedStackSlot = -1;
        for (Integer partialStackSlot : partialStackSlotsList) {
            if (consolidatedStack == null) {
                consolidatedStack = player.inventory.getStackInSlot(partialStackSlot);
                consolidatedStackSlot = partialStackSlot;
            } else {
                PlayerInventoryUtil.consolidateItemStacks(player.inventory, partialStackSlot, consolidatedStackSlot);
            }

            // Check if we created a full stack of items
            if (consolidatedStack.stackSize == consolidatedStack.getMaxStackSize()) {
                PlayerInventoryUtil.setSlotAsActiveSlot(sender, consolidatedStackSlot);
                return;
            }
        }

        // 5. Calculate withdrawal amount
        int amountToWithdraw = consolidatedStack == null ? itemToFind.getMaxStackSize()
                : itemToFind.getMaxStackSize() - consolidatedStack.stackSize;
        if (amountToWithdraw <= 0) {
            return;
        }

        // Create an IAEItemStack for the target block with the calculated amount
        ItemStack targetItemStack = itemToFind.copy();
        targetItemStack.stackSize = amountToWithdraw;
        IAEItemStack targetAeItemStack = AEApi.instance().storage().createItemStack(targetItemStack);
        if (targetAeItemStack == null) {
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
                // If no consolidation was done, put withdrawn items into next empty slot.
                // Otherwise, add them to the consolidated slot, and move it to the active slot.
                if (consolidatedStack == null) {
                    player.inventory.setInventorySlotContents(nextEmptySlot, itemsToGive);
                } else {
                    consolidatedStack.stackSize += itemsToGive.stackSize;
                }
            }
        }

        // If the target stack is already in the player's hotbar, set that as the active slot.
        // Otherwise, move the target stack to the active slot.
        // The slot to swap will have either been a consolidated stack of partial ItemStacks,
        // or it will have been a newly created ItemStack in the next empty slot.
        int slotToSwap = consolidatedStack == null ? nextEmptySlot : consolidatedStackSlot;
        PlayerInventoryUtil.setSlotAsActiveSlot(sender, slotToSwap);
    }

    private IMEInventoryHandler<IAEItemStack> getWirelessItemInventory(ItemStack wirelessTerminal) {
        if (wirelessTerminal == null) {
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

}
