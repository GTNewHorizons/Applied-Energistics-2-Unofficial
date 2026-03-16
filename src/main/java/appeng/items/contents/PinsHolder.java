package appeng.items.contents;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.CraftingPinsRows;
import appeng.api.config.PinsState;
import appeng.api.config.PlayerPinsRows;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class PinsHolder implements IAEAppEngInventory {

    private final ItemStack holder;

    private final HashMap<UUID, PinList> pinsMap = new HashMap<>();
    private final HashMap<UUID, CraftingPinsRows> craftingPinsRowsMap = new HashMap<>();
    private final HashMap<UUID, PlayerPinsRows> playerPinsRowsMap = new HashMap<>();

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
            CraftingPinsRows cr = craftingPinsRowsMap.get(playerId);
            PlayerPinsRows pr = playerPinsRowsMap.get(playerId);
            itemList.setInteger("craftingPinsRows", cr != null ? cr.ordinal() : 0);
            itemList.setInteger("playerPinsRows", pr != null ? pr.ordinal() : 0);
            for (int x = 0; x < pins.size(); x++) {
                final IAEStack<?> pinStack = pins.getPin(x);
                if (pinStack != null) {
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

            final boolean hasNewFormat = itemList.hasKey("craftingPinsRows");
            int craftingOrdinal = hasNewFormat ? itemList.getInteger("craftingPinsRows") : 0;
            int playerOrdinal;
            if (hasNewFormat) {
                playerOrdinal = Math.min(itemList.getInteger("playerPinsRows"), PlayerPinsRows.values().length - 1);
            } else {
                int oldState = itemList.getInteger("pinsState");
                int oldOrdinal = Math.min(Math.max(oldState, 0), PinsState.values().length - 1);
                playerOrdinal = oldOrdinal;
                for (int x = 0; x < 36; x++) {
                    if (itemList.hasKey("#" + x)) {
                        NBTTagCompound tag = itemList.getCompoundTag("#" + x);
                        int destIdx = PinList.PLAYER_OFFSET + x;
                        if (tag.hasKey("StackType")) {
                            pins.setPin(destIdx, Platform.readStackNBT(tag));
                        } else {
                            ItemStack pinStack = ItemStack.loadItemStackFromNBT(itemList.getCompoundTag("#" + x));
                            pins.setPin(destIdx, AEItemStack.create(pinStack));
                        }
                    }
                }
            }

            if (!hasNewFormat) {
                craftingOrdinal = Math.min(craftingOrdinal, CraftingPinsRows.values().length - 1);
            }

            for (int x = 0; x < pins.size(); x++) {
                if (hasNewFormat && itemList.hasKey("#" + x)) {
                    NBTTagCompound tag = itemList.getCompoundTag("#" + x);
                    if (tag.hasKey("StackType")) {
                        IAEStack<?> stack = Platform.readStackNBT(tag);
                        pins.setPin(x, stack);
                    } else {
                        ItemStack pinStack = ItemStack.loadItemStackFromNBT(itemList.getCompoundTag("#" + x));
                        pins.setPin(x, AEItemStack.create(pinStack));
                    }
                }
            }

            this.pinsMap.put(playerId, pins);
            this.craftingPinsRowsMap.put(
                    playerId,
                    CraftingPinsRows.fromOrdinal(Math.min(craftingOrdinal, CraftingPinsRows.values().length - 1)));
            this.playerPinsRowsMap.put(
                    playerId,
                    PlayerPinsRows.fromOrdinal(Math.min(playerOrdinal, PlayerPinsRows.values().length - 1)));
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

    public CraftingPinsRows getCraftingPinsRows(EntityPlayer player) {
        return this.craftingPinsRowsMap.computeIfAbsent(player.getPersistentID(), k -> CraftingPinsRows.DISABLED);
    }

    public void setCraftingPinsRows(EntityPlayer player, CraftingPinsRows rows) {
        this.craftingPinsRowsMap.put(player.getPersistentID(), rows);
        markDirty();
    }

    public PlayerPinsRows getPlayerPinsRows(EntityPlayer player) {
        return this.playerPinsRowsMap.computeIfAbsent(player.getPersistentID(), k -> PlayerPinsRows.DISABLED);
    }

    public void setPlayerPinsRows(EntityPlayer player, PlayerPinsRows rows) {
        this.playerPinsRowsMap.put(player.getPersistentID(), rows);
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
