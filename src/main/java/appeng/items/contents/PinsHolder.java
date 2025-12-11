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
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class PinsHolder implements IAEAppEngInventory {

    private final ItemStack holder;

    private final HashMap<UUID, PinList> pinsMap = new HashMap<>();
    private final HashMap<UUID, PinsState> pinsStateMap = new HashMap<>();

    private boolean initialized = false;

    public PinsHolder(final ItemStack holder) {
        this.holder = holder;
        this.readFromNBT(Platform.openNbtData(holder), "pins");
        this.initialized = true;
    }

    public PinsHolder(final ITerminalPins terminalPart) {
        holder = null;
        this.initialized = true;
    }

    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagList c = new NBTTagList();

        for (Entry<UUID, PinList> entry : this.pinsMap.entrySet()) {
            final UUID playerId = entry.getKey();
            final PinList pins = entry.getValue();

            final NBTTagCompound itemList = new NBTTagCompound();
            itemList.setString("playerId", playerId.toString());
            int state = pinsStateMap.get(playerId) != null ? pinsStateMap.get(playerId).ordinal() : 0;
            itemList.setInteger("pinsState", state);
            for (int x = 0; x < pins.size(); x++) {
                // final ItemStack pinStack = pins.getStackInSlot(x);
                final IAEStack<?> pinStack = pins.getPin(x);
                if (pinStack != null) {
                    // itemList.setTag("#" + x, pinStack.writeToNBT(new NBTTagCompound()));
                    itemList.setTag("#" + x, Platform.writeStackNBT(pinStack, new NBTTagCompound(), true));
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
        final NBTTagList list = data.getTagList(name, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound itemList = list.getCompoundTagAt(i);
            final String playerIdStr = itemList.getString("playerId");
            final UUID playerId = UUID.fromString(playerIdStr);

            final PinList pins = new PinList();

            for (int x = 0; x < pins.size(); x++) {
                if (itemList.hasKey("#" + x)) {
                    NBTTagCompound tag = itemList.getCompoundTag("#" + x);
                    if (tag.hasKey("StackType")) {
                        IAEStack<?> stack = Platform.readStackNBT(tag);
                        // pins.setInventorySlotContents();
                        pins.setPin(x, stack);
                    } else {
                        ItemStack pinStack = ItemStack.loadItemStackFromNBT(itemList.getCompoundTag("#" + x));
                        // pins.setInventorySlotContents(x, pinStack);
                        pins.setPin(x, AEItemStack.create(pinStack));
                    }
                }
            }

            this.pinsMap.put(playerId, pins);
            this.pinsStateMap.put(playerId, PinsState.values()[itemList.getInteger("pinsState")]);
        }
    }

    public PinList getPinsInv(EntityPlayer player) {
        PinList pinsInv = this.pinsMap.get(player.getPersistentID());
        if (pinsInv == null) {
            pinsInv = new PinList();
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
        if (holder == null || !initialized) return;
        this.writeToNBT(Platform.openNbtData(holder), "pins");
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
