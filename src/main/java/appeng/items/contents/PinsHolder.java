package appeng.items.contents;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.PinsState;
import appeng.api.storage.ITerminalPins;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

public class PinsHolder implements IAEAppEngInventory {

    private final IAEAppEngInventory te;
    private final ItemStack is;

    private final HashMap<UUID, AppEngInternalAEInventory> pinsMap = new HashMap<>();
    private final HashMap<UUID, PinsState> pinsStateMap = new HashMap<>();

    private boolean initialized = false;

    public PinsHolder(final ItemStack is) {
        this.is = is;
        this.te = null;
        this.readFromNBT(Platform.openNbtData(is), "pins");
        this.initialized = true;
    }

    public PinsHolder(final ITerminalPins terminalPart) {
        if (terminalPart instanceof IAEAppEngInventory _te) {
            this.te = _te;
        } else te = null;
        is = null;
        this.initialized = true;
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagList c = new NBTTagList();

        for (Entry<UUID, AppEngInternalAEInventory> entry : this.pinsMap.entrySet()) {
            final UUID playerId = entry.getKey();
            final AppEngInternalAEInventory pins = entry.getValue();

            final NBTTagCompound itemList = new NBTTagCompound();
            itemList.setString("playerId", playerId.toString());
            int state = pinsStateMap.get(playerId) != null ? pinsStateMap.get(playerId).ordinal() : 0;
            itemList.setInteger("pinsState", state);
            for (int x = 0; x < pins.getSizeInventory(); x++) {
                final ItemStack pinStack = pins.getStackInSlot(x);
                if (pinStack != null) {
                    itemList.setTag("#" + x, pinStack.writeToNBT(new NBTTagCompound()));
                }
            }
            c.appendTag(itemList);
        }

        data.setTag(name, c);
    }

    public void readFromNBT(final NBTTagCompound data, final String name) {
        if (!data.hasKey(name)) {
            return;
        }
        final NBTTagList list = (NBTTagList) data.getTag(name);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound itemList = list.getCompoundTagAt(i);
            final String playerIdStr = itemList.getString("playerId");
            final UUID playerId = UUID.fromString(playerIdStr);

            final AppEngInternalAEInventory pins = new AppEngInternalAEInventory(this, PinsState.getPinsCount());

            for (int x = 0; x < PinsState.getPinsCount(); x++) {
                if (itemList.hasKey("#" + x)) {
                    ItemStack pinStack = ItemStack.loadItemStackFromNBT(itemList.getCompoundTag("#" + x));
                    pins.setInventorySlotContents(x, pinStack);
                }
            }

            this.pinsMap.put(playerId, pins);
            this.pinsStateMap.put(playerId, PinsState.values()[itemList.getInteger("pinsState")]);
        }
    }

    public AppEngInternalAEInventory getPinsInv(EntityPlayer player) {
        AppEngInternalAEInventory pinsInv = this.pinsMap.get(player.getPersistentID());
        if (pinsInv == null) {
            pinsInv = new AppEngInternalAEInventory(this, PinsState.getPinsCount());
            this.pinsMap.put(player.getPersistentID(), pinsInv);
        }
        return pinsInv;
    }

    public PinsState getPinsState(EntityPlayer player) {
        return this.pinsStateMap.computeIfAbsent(player.getPersistentID(), k -> PinsState.DISABLED);
    }

    public void setPinsState(EntityPlayer player, PinsState state) {
        this.pinsStateMap.put(player.getPersistentID(), state);
        markDirty();
    }

    public void markDirty() {
        if (is == null || !initialized) return;
        this.writeToNBT(Platform.openNbtData(is), "pins");
    }

    @Override
    public void saveChanges() {
        markDirty();
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
        markDirty();
    }

    public PinsHandler getHandler(EntityPlayer player) {
        return new PinsHandler(this, player);
    }
}
