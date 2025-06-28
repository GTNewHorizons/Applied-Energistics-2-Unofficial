package appeng.items.contents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.tile.inventory.AppEngInternalAEInventory;

public class PinsHandler {

    private final PinsHolder holder;
    private final AppEngInternalAEInventory pinsInv;
    private PinsState pinsState;
    private final EntityPlayer player;

    private boolean needUpdate = true;

    IAEItemStack[] cache = new IAEItemStack[0];

    public PinsHandler(PinsHolder holder, EntityPlayer player) {
        this.holder = holder;
        this.pinsInv = this.holder.getPinsInv(player);
        this.player = player;
        setPinsState(this.holder.getPinsState(player));
    }

    public void setPin(int idx, ItemStack stack) {
        if (stack != null) {
            stack = stack.copy();
            stack.stackSize = 0;
            for (int i = 0; i < pinsInv.getSizeInventory(); i++) {
                if (pinsInv.getAEStackInSlot(i) != null && pinsInv.getAEStackInSlot(i).isSameType(stack)) {
                    pinsInv.setInventorySlotContents(i, pinsInv.getStackInSlot(idx)); // swap the pin
                    break;
                }
            }
        }

        pinsInv.setInventorySlotContents(idx, stack);
        needUpdate = true;
        holder.markDirty();
    }

    public ItemStack getPin(int idx) {
        return pinsInv.getStackInSlot(idx);
    }

    public void addItemsToPins(Iterable<IAEItemStack> pinsList) {
        Iterator<IAEItemStack> it = pinsList.iterator();

        final ArrayList<IAEItemStack> checkCache = new ArrayList<>();
        for (int i = 0; i < pinsInv.getSizeInventory(); i++) {
            IAEItemStack ais = pinsInv.getAEStackInSlot(i);
            if (ais != null) checkCache.add(ais);
        }

        ItemStack itemStack = null;
        for (int i = 0; i < pinsInv.getSizeInventory(); i++) {
            IAEItemStack AEis;
            while (itemStack == null && it.hasNext()) {
                AEis = it.next();
                if (AEis != null && !checkCache.contains(AEis)) {
                    itemStack = AEis.getItemStack();
                    itemStack.stackSize = 0;
                    break;
                }
            }

            if (itemStack == null) break; // no more items to add
            if (pinsInv.getAEStackInSlot(i) != null) continue; // skip if slot already has a item
            pinsInv.setInventorySlotContents(i, itemStack);
            itemStack = null;
        }
        needUpdate = true;
        holder.markDirty();
    }

    public void setPinsState(PinsState state) {
        if (pinsState == state) return;
        pinsState = state;
        holder.setPinsState(player, state);
        update(false);
    }

    public PinsState getPinsState() {
        return pinsState;
    }

    /** return an array of enabled pins, according to the current state */
    public IAEItemStack[] getEnabledPins() {
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
        final IAEItemStack[] newPins = new IAEItemStack[pinsState.ordinal() * 9];
        // fetch lines according to the setting
        for (int i = 0; i < pinsState.ordinal() * 9; i++) {
            newPins[i] = pinsInv.getAEStackInSlot(i);
        }
        if (!forceSendPacket && Arrays.equals(cache, newPins)) return;
        cache = newPins;

        if (player instanceof EntityPlayerMP mp) {
            try {
                NetworkHandler.instance.sendTo(new PacketPinsUpdate(newPins, pinsState), mp);
            } catch (IOException e) {
                AELog.debug(e);
            }
        }
    }
}
