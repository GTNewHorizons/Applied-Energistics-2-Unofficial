
/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.primitives.Ints;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IIfaceTermViewable;
import appeng.container.AEBaseContainer;
import appeng.core.features.registries.IfaceTermRegistry;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketIfaceTermUpdate;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.parts.AEBasePart;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.inv.ItemSlot;

public final class ContainerInterfaceTerminal extends AEBaseContainer {

    private int nextId = 0;

    private final Map<IIfaceTermViewable, InvTracker> tracked = new HashMap<>();
    private final Map<Long, InvTracker> trackedById = new HashMap<>();
    private PacketIfaceTermUpdate dirty;
    private boolean isDirty;
    private IGrid grid;
    private IActionHost anchor;
    private boolean wasOff;

    public ContainerInterfaceTerminal(final InventoryPlayer ip, final IActionHost anchor) {
        super(ip, anchor);
        assert anchor != null;
        this.anchor = anchor;
        if (Platform.isServer()) {
            this.grid = anchor.getActionableNode().getGrid();
            dirty = this.updateList();
            if (dirty != null) {
                dirty.encode();
                this.isDirty = true;
            } else {
                dirty = new PacketIfaceTermUpdate();
            }
        }
        this.bindPlayerInventory(ip, 14, 3);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) {
            return;
        }

        super.detectAndSendChanges();

        if (this.grid == null) {
            return;
        }

        final IGridNode agn = this.anchor.getActionableNode();

        if (!agn.isActive()) {
            /*
             * Should turn off the terminal. However, there's no need to remove all the entries from the client.
             * Continue tracking on the server just in case the system comes back online. Just don't send any new
             * updates. This prevents DoSing the player if their network is flickering.
             */
            if (!this.wasOff) {
                PacketIfaceTermUpdate update = new PacketIfaceTermUpdate();

                update.setDisconnect();
                this.wasOff = true;
                NetworkHandler.instance.sendTo(update, (EntityPlayerMP) this.getPlayerInv().player);
            }
            return;
        }
        this.wasOff = false;

        if (anchor instanceof PartInterfaceTerminal terminal && terminal.needsUpdate()) {
            PacketIfaceTermUpdate update = this.updateList();
            if (update != null) {
                update.encode();
                NetworkHandler.instance.sendTo(update, (EntityPlayerMP) this.getPlayerInv().player);
            }
        } else if (isDirty) {
            this.dirty.encode();
            NetworkHandler.instance.sendTo(this.dirty, (EntityPlayerMP) this.getPlayerInv().player);
            this.dirty = new PacketIfaceTermUpdate();
            this.isDirty = false;
        }
    }

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        final InvTracker inv = this.trackedById.get(id);
        if (inv != null) {
            final ItemStack handStack = player.inventory.getItemStack();

            if (handStack != null && !(handStack.getItem() instanceof ItemEncodedPattern)) {
                // Why even bother if we're not dealing with an encoded pattern in hand
                return;
            }

            final ItemStack slotStack = inv.patterns.getStackInSlot(slot);
            final InventoryAdaptor playerHand = new AdaptorPlayerHand(player);

            switch (action) {
                /* Set down/pickup. This is the same as SPLIT_OR_PLACE_SINGLE as our max stack sizes are 1 in slots. */
                case PICKUP_OR_SET_DOWN -> {
                    if (handStack != null) {
                        for (int s = 0; s < inv.patterns.getSizeInventory(); s++) {
                            /* Is there a duplicate pattern here? */
                            if (Platform.isSameItemPrecise(inv.patterns.getStackInSlot(s), handStack)) {
                                /* We're done here - dupe found. */
                                return;
                            }
                        }
                    }

                    if (slotStack == null) {
                        /* Insert to container, if valid */
                        if (handStack == null) {
                            /* Nothing happens */
                            return;
                        }
                        inv.patterns.setInventorySlotContents(slot, playerHand.removeItems(1, null, null));
                    } else {
                        /* Exchange? */
                        if (handStack != null && handStack.stackSize > 1) {
                            /* Exchange is impossible, abort */
                            return;
                        }
                        inv.patterns.setInventorySlotContents(slot, playerHand.removeItems(1, null, null));
                        playerHand.addItems(slotStack.copy());
                    }
                    syncIfaceSlot(inv, id, slot, inv.patterns.getStackInSlot(slot));
                }
                /* Shift click from slot -> player. Player -> slot is not supported. */
                case SHIFT_CLICK -> {
                    InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(player.inventory, ForgeDirection.UNKNOWN);
                    ItemStack leftOver = mergeToPlayerInventory(playerInv, slotStack);

                    if (leftOver == null) {
                        inv.patterns.setInventorySlotContents(slot, null);
                        syncIfaceSlot(inv, id, slot, null);
                    }
                }
                /* Move all blank patterns -> player */
                case MOVE_REGION -> {
                    final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    List<Integer> valid = new ArrayList<>();

                    for (int i = 0; i < inv.patterns.getSizeInventory(); i++) {
                        ItemStack toExtract = inv.patterns.getStackInSlot(i);

                        if (toExtract == null) {
                            continue;
                        }

                        ItemStack leftOver = mergeToPlayerInventory(playerInv, toExtract);

                        if (leftOver != null) {
                            break;
                        } else {
                            inv.patterns.setInventorySlotContents(i, null);
                        }
                        valid.add(i);
                    }
                    if (valid.size() > 0) {
                        int[] validIndices = Ints.toArray(valid);
                        NBTTagList tag = new NBTTagList();
                        for (int i = 0; i < valid.size(); ++i) {
                            tag.appendTag(new NBTTagCompound());
                        }
                        dirty.addOverwriteEntry(id).setItems(validIndices, tag);
                        isDirty = true;
                    }
                }
                case CREATIVE_DUPLICATE -> {
                    if (player.capabilities.isCreativeMode) {
                        playerHand.addItems(handStack);
                    }
                }
                default -> {
                    return;
                }
            }

            this.updateHeld(player);
        }
    }

    /**
     * Since we are not using "slots" like MC and instead taking the Thaumic Energistics route, this is used to
     * eventually send a packet to the client to indicate that a slot has changed. <br/>
     * Why weren't slots used? Because at first I was more concerned about performance issues, and by the time I
     * realized I was perhaps making a huge mistake in not using the notion of a "slot", I had already finished
     * implementing my own version of a "slot". Because of this everything in this GUI is basically hand written. Also,
     * the previous implementation was seemingly allocating slots. <br/>
     * // rant over
     */
    private void syncIfaceSlot(InvTracker inv, long id, int slot, ItemStack stack) {
        int[] validIndices = { slot };
        NBTTagList list = new NBTTagList();
        NBTTagCompound item = new NBTTagCompound();

        if (stack != null) {
            stack.writeToNBT(item);
        }
        list.appendTag(item);
        inv.updateNBT();
        this.dirty.addOverwriteEntry(id).setItems(validIndices, list);
        this.isDirty = true;
    }

    /**
     * Merge from slot -> player inv. Returns the items not added.
     */
    private ItemStack mergeToPlayerInventory(InventoryAdaptor playerInv, ItemStack stack) {
        if (stack == null) return null;
        for (ItemSlot slot : playerInv) {
            if (Platform.isSameItemPrecise(slot.getItemStack(), stack)) {
                if (slot.getItemStack().stackSize < slot.getItemStack().getMaxStackSize()) {
                    ++slot.getItemStack().stackSize;
                    return null;
                }
            }
        }
        return playerInv.addItems(stack);
    }

    /**
     * Finds out whether any updates are needed, and if so, incrementally updates the list.
     */
    private PacketIfaceTermUpdate updateList() {
        PacketIfaceTermUpdate update = null;
        var supported = IfaceTermRegistry.instance().getSupportedClasses();
        Set<IIfaceTermViewable> visited = new HashSet<>();

        for (Class<? extends IIfaceTermViewable> c : supported) {
            for (IGridNode node : grid.getMachines(c)) {
                IIfaceTermViewable machine = (IIfaceTermViewable) node.getMachine();
                /* First check if we are already tracking this node */
                if (tracked.containsKey(machine)) {
                    /* Check for updates */
                    InvTracker known = tracked.get(machine);

                    /* Name changed? */
                    String name = machine.getName();

                    if (!Objects.equals(known.name, name)) {
                        if (update == null) update = new PacketIfaceTermUpdate();
                        update.addRenamedEntry(known.id, name);
                        known.name = name;
                    }

                    /* Status changed? */
                    boolean isActive = node.isActive() || machine.shouldDisplay();

                    if (!known.online && isActive) {
                        /* Node offline -> online */
                        known.online = true;
                        if (update == null) update = new PacketIfaceTermUpdate();
                        known.updateNBT();
                        update.addOverwriteEntry(known.id).setOnline(true).setItems(new int[0], known.invNbt);
                    } else if (known.online && !isActive) {
                        /* Node online -> offline */
                        known.online = false;
                        if (update == null) update = new PacketIfaceTermUpdate();
                        update.addOverwriteEntry(known.id).setOnline(false);
                    }
                } else {
                    /* Add a new entry */
                    if (update == null) update = new PacketIfaceTermUpdate();
                    InvTracker entry = new InvTracker(nextId++, machine, node.isActive());
                    update.addNewEntry(entry.id, entry.name, entry.online)
                            .setLoc(entry.x, entry.y, entry.z, entry.dim, entry.side.ordinal())
                            .setItems(entry.rows, entry.rowSize, entry.invNbt)
                            .setReps(machine.getSelfRep(), machine.getDisplayRep());
                    tracked.put(machine, entry);
                    trackedById.put(entry.id, entry);
                }
                visited.add(machine);
            }
        }

        /* Now find any entries that we need to remove */
        Iterator<Entry<IIfaceTermViewable, InvTracker>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (visited.contains(entry.getKey())) {
                continue;
            }

            if (update == null) update = new PacketIfaceTermUpdate();

            trackedById.remove(entry.getValue().id);
            it.remove();
            update.addRemovalEntry(entry.getValue().id);
        }
        return update;
    }

    private boolean isDifferent(final ItemStack a, final ItemStack b) {
        if (a == null && b == null) {
            return false;
        }

        if (a == null || b == null) {
            return true;
        }

        return !ItemStack.areItemStacksEqual(a, b);
    }

    private static class InvTracker {

        private final long id;
        private String name;
        private final IInventory patterns;
        private final int rowSize;
        private final int rows;
        private final int x;
        private final int y;
        private final int z;
        private final int dim;
        private final ForgeDirection side;
        private boolean online;
        private NBTTagList invNbt;

        InvTracker(long id, IIfaceTermViewable machine, boolean online) {
            DimensionalCoord location = machine.getLocation();

            this.id = id;
            this.name = machine.getName();
            this.patterns = machine.getPatterns();
            this.rowSize = machine.rowSize();
            this.rows = machine.rows();
            this.x = location.x;
            this.y = location.y;
            this.z = location.z;
            this.dim = location.getDimension();
            this.side = machine instanceof AEBasePart hasSide ? hasSide.getSide() : ForgeDirection.UNKNOWN;
            this.online = online;
            this.invNbt = new NBTTagList();
            updateNBT();
        }

        /**
         * Refresh nbt items in the row, item idx.
         */
        private void updateNBT(int row, int idx) {
            ItemStack stack = this.patterns.getStackInSlot(row * this.rowSize + idx);

            if (stack != null) {
                NBTTagCompound itemNbt = this.invNbt.getCompoundTagAt(idx);
                stack.writeToNBT(itemNbt);
            } else {
                // replace
                this.invNbt.func_150304_a(idx, new NBTTagCompound());
            }
        }

        /**
         * Refreshes all nbt tags.
         */
        private void updateNBT() {
            this.invNbt = new NBTTagList();
            for (int i = 0; i < this.rows; ++i) {
                for (int j = 0; j < this.rowSize; ++j) {
                    final int offset = this.rowSize * i;
                    ItemStack stack = this.patterns.getStackInSlot(offset + j);

                    if (stack != null) {
                        this.invNbt.appendTag(stack.writeToNBT(new NBTTagCompound()));
                    } else {
                        this.invNbt.appendTag(new NBTTagCompound());
                    }
                }
            }
        }
    }
}
