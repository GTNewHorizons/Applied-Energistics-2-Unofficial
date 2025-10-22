package appeng.items.contents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.config.PinsState;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPinsUpdate;

public class PinsHandler {

    private final PinsHolder holder;
    private final PinList pinsInv;
    private PinsState pinsState;
    private final EntityPlayer player;

    private boolean needUpdate = true;

    IAEStack<?>[] cache = new IAEStack<?>[0];

    public PinsHandler(PinsHolder holder, EntityPlayer player) {
        this.holder = holder;
        this.pinsInv = this.holder.getPinsInv(player);
        this.player = player;
        setPinsState(this.holder.getPinsState(player));
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

        // pinsInv.setInventorySlotContents(idx, stack);
        pinsInv.setPin(idx, stack);
        needUpdate = true;
        holder.markDirty();
    }

    public IAEStack<?> getPin(int idx) {
        return pinsInv.getPin(idx);
    }

    public void addItemsToPins(Iterable<IAEStack<?>> pinsList) {
        Iterator<IAEStack<?>> it = pinsList.iterator();

        final ArrayList<IAEStack<?>> checkCache = new ArrayList<>();
        for (int i = 0; i < pinsInv.size(); i++) {
            IAEStack<?> ais = pinsInv.getPin(i);
            if (ais != null) checkCache.add(ais);
        }

        IAEStack<?> itemStack = null;
        for (int i = 0; i < pinsInv.size(); i++) {
            IAEStack<?> AEis;
            while (itemStack == null && it.hasNext()) {
                AEis = it.next();
                if (AEis != null && !checkCache.contains(AEis)) {
                    itemStack = AEis.copy();
                    itemStack.setStackSize(0);
                    break;
                }
            }

            if (itemStack == null) break; // no more items to add
            if (pinsInv.getPin(i) != null) continue; // skip if slot already has a item
            pinsInv.setPin(i, itemStack);
            // pinsInv.setInventorySlotContents(i, itemStack);
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
        final IAEStack<?>[] newPins = new IAEStack<?>[pinsState.ordinal() * 9];
        // fetch lines according to the setting
        for (int i = 0; i < pinsState.ordinal() * 9; i++) {
            newPins[i] = pinsInv.getPin(i);
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
