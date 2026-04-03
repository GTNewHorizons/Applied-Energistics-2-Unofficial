package appeng.items.contents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPinsUpdate;

public class PinsHandler {

    private final PinsHolder holder;
    private final PinList pinsInv;
    private PinsRows craftingPinsRows;
    private PinsRows playerPinsRows;
    private final EntityPlayer player;

    private boolean needUpdate = true;

    IAEStack<?>[] cache = new IAEStack<?>[0];

    public PinsHandler(PinsHolder holder, EntityPlayer player) {
        this.holder = holder;
        this.pinsInv = this.holder.getPinsInv(player);
        this.player = player;
        this.craftingPinsRows = this.holder.getCraftingPinsRows(player);
        this.playerPinsRows = this.holder.getPlayerPinsRows(player);
    }

    public void setPin(int idx, IAEStack<?> stack) {
        if (stack != null) {
            stack = stack.copy();
            stack.setStackSize(0);
            for (int i = 0; i < pinsInv.size(); i++) {
                if (pinsInv.getPin(i) != null && pinsInv.getPin(i).isSameType(stack)) {
                    // pinsInv.setInventorySlotContents(i, pinsInv.getStackInSlot(idx)); // swap the pin
                    pinsInv.setPin(i, pinsInv.getPin(idx));
                    break;
                }
            }
        }
        // pinsInv.setInventorySlotContents(idx, stack); // set the pin
        pinsInv.setPin(idx, stack);
        needUpdate = true;
        holder.markDirty();
    }

    public IAEStack<?> getPin(int idx) {
        return pinsInv.getPin(idx);
    }

    // Adds crafted items only to the crafting pin section (indices 0 to craftingRows*9-1).
    public void addItemsToPins(Iterable<IAEStack<?>> pinsList) {
        int maxCraftingSlots = craftingPinsRows.getSlotCount();
        if (maxCraftingSlots <= 0) return;

        Iterator<IAEStack<?>> it = pinsList.iterator();

        final ArrayList<IAEStack<?>> checkCache = new ArrayList<>();
        for (int i = 0; i < maxCraftingSlots; i++) {
            IAEStack<?> ais = pinsInv.getPin(i);
            if (ais != null) checkCache.add(ais);
        }

        IAEStack<?> itemStack = null;
        for (int i = 0; i < maxCraftingSlots; i++) {
            IAEStack<?> AEis;
            while (itemStack == null && it.hasNext()) {
                AEis = it.next();
                if (AEis != null && !checkCache.contains(AEis)) {
                    itemStack = AEis.copy();
                    itemStack.setStackSize(0);
                    break;
                }
            }

            if (itemStack == null) break;
            if (pinsInv.getPin(i) != null) continue;
            pinsInv.setPin(i, itemStack);
            itemStack = null;
        }
        needUpdate = true;
        holder.markDirty();
    }

    public void setCraftingPinsRows(PinsRows rows) {
        if (craftingPinsRows == rows) return;
        craftingPinsRows = rows;
        holder.setCraftingPinsRows(player, rows);
        update(false);
    }

    public void setPlayerPinsRows(PinsRows rows) {
        if (playerPinsRows == rows) return;
        playerPinsRows = rows;
        holder.setPlayerPinsRows(player, rows);
        update(false);
    }

    public PinsRows getCraftingPinsRows() {
        return craftingPinsRows;
    }

    public PinsRows getPlayerPinsRows() {
        return playerPinsRows;
    }

    /** Returns the full pins array (TOTAL_SLOTS) for the client. */
    public IAEStack<?>[] getEnabledPins() {
        if (needUpdate) update();
        return cache;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public void update() {
        update(false);
    }

    public void update(boolean forceSendPacket) {
        needUpdate = false;
        final IAEStack<?>[] newPins = new IAEStack<?>[PinList.TOTAL_SLOTS];
        for (int i = 0; i < PinList.TOTAL_SLOTS; i++) {
            newPins[i] = pinsInv.getPin(i);
        }

        if (!forceSendPacket && Arrays.equals(cache, newPins)) return;
        cache = newPins;

        if (player instanceof EntityPlayerMP mp) {
            try {
                NetworkHandler.instance.sendTo(new PacketPinsUpdate(newPins, craftingPinsRows, playerPinsRows), mp);
            } catch (IOException e) {
                AELog.debug(e);
            }
        }
    }
}
