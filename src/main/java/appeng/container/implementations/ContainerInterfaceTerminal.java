
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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.primitives.Ints;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.items.INetworkToolItem;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IInterfaceTerminal;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.client.gui.IGuiSub;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InterfaceUpgradeRemoval;
import appeng.helpers.InventoryAction;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.misc.ItemEncodedPattern;
import appeng.parts.AEBasePart;
import appeng.parts.misc.PartPatternRepeater;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.PatternMultiplierHelper;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.inv.ItemSlot;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class ContainerInterfaceTerminal extends AEBaseContainer implements IContainerSubGui {

    private static final int UPGRADE_SLOTS = 4;
    private final AppEngInternalInventory clientUpgrades = new AppEngInternalInventory(null, UPGRADE_SLOTS, 1);
    private final ActionHandler<Long> upgradeSelection;
    private final int firstUpgradeSlot;
    private long selectedUpgradeId = -1;
    private boolean upgradeSelectionPending;
    private NetworkToolViewer toolbox;
    private int toolboxSlot;

    @SideOnly(Side.CLIENT)
    private IGuiSub guiLink;

    private int nextId = 0;
    private boolean forceNextUpdate = false;

    private final Map<IInterfaceViewable, InvTracker> tracked = new HashMap<>();
    private final Map<Long, InvTracker> trackedById = new HashMap<>();
    private PacketInterfaceTerminalUpdate dirty;
    private boolean isDirty;
    private IGrid grid;
    private final IInterfaceTerminal anchor;
    private boolean wasOff;

    public ContainerInterfaceTerminal(final InventoryPlayer ip, final IInterfaceTerminal anchor) {
        super(ip, anchor);
        assert anchor != null;
        this.anchor = anchor;
        if (Platform.isServer()) {
            this.grid = anchor.getActionableNode().getGrid();
            dirty = this.updateList();
            if (dirty != null) {
                this.isDirty = true;
            } else {
                dirty = new PacketInterfaceTerminalUpdate();
            }
        }
        for (int slot = 0; slot < ip.getSizeInventory(); slot++) {
            final ItemStack stack = ip.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof INetworkToolItem tool) {
                toolbox = new NetworkToolViewer(stack, null, tool.getInventorySize());
                toolboxSlot = slot;
                lockPlayerInventorySlot(slot);
                break;
            }
        }
        this.bindPlayerInventory(ip, 14, 3);
        this.firstUpgradeSlot = this.inventorySlots.size();
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            this.addSlotToContainer(new InterfaceUpgradeSlot(clientUpgrades, slot));
        }
        if (toolbox != null) {
            final int size = toolbox.getSize();
            for (int slot = 0; slot < toolbox.getSizeInventory(); slot++) {
                addSlotToContainer(
                        new SlotRestrictedInput(
                                SlotRestrictedInput.PlacableItemType.UPGRADES,
                                toolbox,
                                slot,
                                219 + slot % size * 18,
                                92 + slot / size * 18,
                                ip).setPlayerSide());
            }
        }
        this.upgradeSelection = this.syncRegistrar().action("interfaceUpgrades", StreamCodecs.longValue())
                .onServerAction(this::selectUpgrades).onClientAction(id -> {
                    selectedUpgradeId = id;
                    upgradeSelectionPending = false;
                });
    }

    public long getSelectedUpgradeId() {
        return selectedUpgradeId;
    }

    public int getToolboxSize() {
        return toolbox == null ? 0 : toolbox.getSize();
    }

    public boolean isToolboxSlot(Slot slot) {
        return toolbox != null && slot.inventory == toolbox;
    }

    private void checkToolbox() {
        if (toolbox == null || Platform.isClient()) return;
        final ItemStack current = getPlayerInv().getStackInSlot(toolboxSlot);
        if (current == toolbox.getItemStack()) return;
        if (ItemStack.areItemStacksEqual(current, toolbox.getItemStack())) {
            getPlayerInv().setInventorySlotContents(toolboxSlot, toolbox.getItemStack());
        } else {
            setValidContainer(false);
        }
    }

    public boolean isUpgradeSlot(Slot slot) {
        return slot instanceof InterfaceUpgradeSlot;
    }

    public boolean isUpgradeSelectionPending() {
        return upgradeSelectionPending;
    }

    public void requestUpgradeSelection(long id) {
        if (upgradeSelectionPending) return;
        upgradeSelectionPending = true;
        upgradeSelection.send(id);
    }

    public void selectUpgrades(long id) {
        selectedUpgradeId = canModifyUpgrades(id) ? id : -1;
        final InvTracker entry = trackedById.get(selectedUpgradeId);
        final IInventory upgrades = entry != null && entry.machine instanceof IInterfaceHost host
                ? host.getInterfaceDuality().getUpgrades()
                : clientUpgrades;
        // Slot IDs and count never change. Only the server rebinds these four slots; the client keeps its mirror.
        // While a request is pending the GUI blocks clicks. Send the acknowledgement before the slot snapshot so
        // vanilla can apply even empty/unchanged contents to the newly selected panel in the same ordered stream.
        for (int index = 0; index < UPGRADE_SLOTS; index++) {
            final InterfaceUpgradeSlot slot = new InterfaceUpgradeSlot(upgrades, index);
            slot.slotNumber = firstUpgradeSlot + index;
            slot.setContainer(this);
            inventorySlots.set(slot.slotNumber, slot);
        }
        // Acknowledge even refused/unchanged selections before sending the vanilla slot contents.
        upgradeSelection.send(selectedUpgradeId);
        for (int index = 0; index < UPGRADE_SLOTS; index++) {
            final Slot slot = getSlot(firstUpgradeSlot + index);
            for (Object listener : crafters) {
                ((ICrafting) listener).sendSlotContents(this, slot.slotNumber, slot.getStack());
            }
        }
    }

    private boolean canModifyUpgrades(long id) {
        final InvTracker entry = trackedById.get(id);
        if (entry == null || !(entry.machine instanceof IInterfaceHost host)
                || host.getInterfaceDuality().getUpgrades().getSizeInventory() != UPGRADE_SLOTS
                || getPlayerInv().player.openContainer != this
                || !isValidContainer()
                || !hasAccess(SecurityPermissions.BUILD, true))
            return false;
        final IGridNode anchorNode = anchor.getActionableNode();
        if (anchorNode == null || !anchorNode.isActive() || anchorNode.getGrid() != grid) return false;
        final TileEntity tile = entry.machine.getTileEntity();
        if (tile == null || tile.isInvalid()
                || !entry.world.blockExists(entry.x, entry.y, entry.z)
                || entry.world.getTileEntity(entry.x, entry.y, entry.z) != tile)
            return false;
        if (entry.side == ForgeDirection.UNKNOWN ? tile != entry.machine
                : Platform.getPartFromTE(tile, entry.side) != entry.machine)
            return false;
        final IGridNode node = entry.machine.getGridNode(ForgeDirection.UNKNOWN);
        if (node == null || !node.isActive() || !collectReachableGrids().contains(node.getGrid())) return false;
        final ISecurityGrid security = node.getGrid().getCache(ISecurityGrid.class);
        return security.hasPermission(getPlayerInv().player, SecurityPermissions.BUILD);
    }

    @Override
    public boolean isValidSrcSlotForTransfer(AppEngSlot slot) {
        checkToolbox();
        return isValidContainer() && super.isValidSrcSlotForTransfer(slot)
                && (!isUpgradeSlot(slot) || slot.canTakeStack(getPlayerInv().player));
    }

    @Override
    public List<AppEngSlot> getValidDestinationSlots(boolean playerSide, ItemStack stack) {
        final List<AppEngSlot> slots = super.getValidDestinationSlots(playerSide, stack);
        // Like local interfaces, return removed cards to the toolbox before the loose player inventory.
        if (!playerSide && toolbox != null) slots.sort(java.util.Comparator.comparing(slot -> !isToolboxSlot(slot)));
        return slots;
    }

    @Override
    public ItemStack slotClick(int slot, int button, int mode, EntityPlayer player) {
        checkToolbox();
        if (!isValidContainer()) return null;
        final boolean hadPanel = selectedUpgradeId >= 0;
        if (Platform.isServer() && hadPanel && !canModifyUpgrades(selectedUpgradeId)) selectUpgrades(-1);
        final ItemStack result = super.slotClick(slot, button, mode, player);
        if (Platform.isServer() && hadPanel) syncUpgradeWindow();
        return result;
    }

    @Override
    public void swapSlotContents(int first, int second) {
        checkToolbox();
        if (!isValidContainer()) return;
        final boolean hadPanel = selectedUpgradeId >= 0;
        if (hadPanel && !canModifyUpgrades(selectedUpgradeId)) selectUpgrades(-1);
        super.swapSlotContents(first, second);
        if (hadPanel) syncUpgradeWindow();
    }

    private void syncUpgradeWindow() {
        // Rejected server-only limits can leave vanilla's predicted return stack unchanged. Resync the cursor too.
        // ponytail: full window resync for infrequent upgrade edits; use targeted slot/cursor updates if this gets hot.
        if (getPlayerInv().player instanceof EntityPlayerMP player && player.playerNetServerHandler != null) {
            player.sendContainerToPlayer(this);
        }
    }

    private class InterfaceUpgradeSlot extends SlotRestrictedInput {

        InterfaceUpgradeSlot(IInventory inventory, int index) {
            super(PlacableItemType.UPGRADES, inventory, index, 217 + index * 18, 50, getPlayerInv());
            setStackLimit(1);
        }

        @Override
        public boolean isEnabled() {
            return selectedUpgradeId >= 0 && !upgradeSelectionPending;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return (Platform.isClient() || canModifyUpgrades(selectedUpgradeId)) && super.isItemValid(stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            if (!isEnabled()) return false;
            if (Platform.isClient()) return true;
            if (!canModifyUpgrades(selectedUpgradeId)) return false;
            final InvTracker entry = trackedById.get(selectedUpgradeId);
            final ItemStack stack = getStack();
            if (stack != null && stack.getItem() instanceof IUpgradeModule card
                    && card.getType(stack) == Upgrades.PATTERN_CAPACITY
                    && entry.machine instanceof IInterfaceHost host
                    && !InterfaceUpgradeRemoval.canRemoveCapacityCard(
                            host.getPatterns(),
                            host.getInstalledUpgrades(Upgrades.PATTERN_CAPACITY) - 1,
                            host.rowSize())) {
                player.addChatComponentMessage(PlayerMessages.InterfaceUpgradeRemovalBlocked.toChat());
                return false;
            }
            return true;
        }

        @Override
        public void putStack(ItemStack stack) {
            if (Platform.isClient()) {
                // Slot synchronization must also clear the mirror while the panel is disabled.
                inventory.setInventorySlotContents(getSlotIndex(), stack);
            } else {
                super.putStack(stack);
            }
        }

        @Override
        public void onSlotChanged() {
            super.onSlotChanged();
            scheduleUpdate();
            if (Platform.isServer()) {
                final InvTracker selected = trackedById.get(selectedUpgradeId);
                if (selected == null) return;
                for (Object onlinePlayer : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
                    if (onlinePlayer instanceof EntityPlayerMP player
                            && player.openContainer instanceof ContainerInterfaceTerminal terminal
                            && terminal.tracked.containsKey(selected.machine)) {
                        terminal.scheduleUpdate();
                    }
                }
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) {
            return;
        }

        checkToolbox();
        if (selectedUpgradeId >= 0 && !canModifyUpgrades(selectedUpgradeId)) selectUpgrades(-1);

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
                PacketInterfaceTerminalUpdate update = new PacketInterfaceTerminalUpdate();

                update.setDisconnect();
                update.encode();
                this.wasOff = true;
                NetworkHandler.instance.sendTo(update, (EntityPlayerMP) this.getPlayerInv().player);
            }
            return;
        }
        this.wasOff = false;

        if (isDirty) {
            this.dirty.encode();
            NetworkHandler.instance.sendTo(this.dirty, (EntityPlayerMP) this.getPlayerInv().player);
            this.dirty = new PacketInterfaceTerminalUpdate();
            this.isDirty = false;
        } else if (anchor.needsUpdate() || forceNextUpdate) {
            forceNextUpdate = false;
            PacketInterfaceTerminalUpdate update = this.updateList();
            if (update != null) {
                update.encode();
                NetworkHandler.instance.sendTo(update, (EntityPlayerMP) this.getPlayerInv().player);
            }
        }
    }

    public void scheduleUpdate() {
        this.forceNextUpdate = true;
    }

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        final InvTracker inv = this.trackedById.get(id);
        if (inv != null) {
            if (slot < 0 || slot >= inv.numSlots || slot >= inv.patterns.getSizeInventory()) return;

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
                        if (!inv.patterns.isItemValidForSlot(slot, handStack)) {
                            return;
                        }
                        inv.patterns.setInventorySlotContents(slot, playerHand.removeItems(1, null, null));
                    } else {
                        /* Exchange? */
                        if (handStack != null && handStack.stackSize > 1) {
                            /* Exchange is impossible, abort */
                            return;
                        }
                        // if exchanging, make sure the item that we're inserting is valid
                        if (handStack != null && !inv.patterns.isItemValidForSlot(slot, handStack)) {
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
                case MULTIPLY_PATTERN -> modifyPatternInSlot(inv, id, slot, slotStack, 1);
                case DIVIDE_PATTERN -> modifyPatternInSlot(inv, id, slot, slotStack, -1);
                case CREATIVE_DUPLICATE -> {
                    if (player.capabilities.isCreativeMode && handStack == null && slotStack != null) {
                        final ItemStack duplicate = slotStack.copy();
                        duplicate.stackSize = duplicate.getMaxStackSize();
                        player.inventory.setItemStack(duplicate);
                    }
                }
                default -> {
                    return;
                }
            }

            this.updateHeld(player);
        }
    }

    private void modifyPatternInSlot(final InvTracker inv, final long id, final int slot, final ItemStack pattern,
            final int multiplier) {
        if (pattern == null || !(pattern.getItem() instanceof ICraftingPatternItem patternItem)) return;

        final ICraftingPatternDetails details = patternItem.getPatternForItem(pattern, inv.world);
        if (details == null || details.isCraftable()) return;

        final int max = multiplier < 0 ? PatternMultiplierHelper.getMaxBitDivider(details)
                : PatternMultiplierHelper.getMaxBitMultiplier(details);
        if (max == 0) return;

        final ItemStack modifiedPattern = pattern.copy();
        PatternMultiplierHelper.applyModification(modifiedPattern, multiplier);
        inv.patterns.setInventorySlotContents(slot, modifiedPattern);
        syncIfaceSlot(inv, id, slot, modifiedPattern);
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

    private void fletchRepeaters(final IGrid grid, final Set<IGrid> gridSet) {
        for (IGridNode node : grid.getMachines(PartPatternRepeater.class)) {
            final PartPatternRepeater rep = (PartPatternRepeater) node.getMachine();
            if (!rep.isProvider() || rep.getPair() == null || !node.isActive() || rep.getPair().isProvider()) continue;
            final IGridNode n = rep.getPair().getGridNode();
            if (n == null || !n.isActive()) continue;
            final IGrid currentGrid = n.getGrid();
            if (!gridSet.contains(currentGrid)) {
                gridSet.add(currentGrid);
                this.fletchRepeaters(currentGrid, gridSet);
            }
        }
    }

    private Set<IGrid> collectReachableGrids() {
        final Set<IGrid> gridSet = new HashSet<>();
        gridSet.add(this.grid);
        this.fletchRepeaters(this.grid, gridSet);
        return gridSet;
    }

    /**
     * Finds out whether any updates are needed, and if so, incrementally updates the list.
     */
    private PacketInterfaceTerminalUpdate updateList() {
        PacketInterfaceTerminalUpdate update = null;
        var supported = AEApi.instance().registries().interfaceTerminal().getSupportedClasses();
        Set<IInterfaceViewable> visited = new HashSet<>();

        for (final IGrid currentGrid : this.collectReachableGrids()) {
            for (Class<? extends IInterfaceViewable> c : supported) {
                for (IGridNode node : currentGrid.getMachines(c)) {
                    IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                    /* First check if we are already tracking this node */
                    if (tracked.containsKey(machine)) {
                        /* Check for updates */
                        InvTracker known = tracked.get(machine);

                        /* Name changed? */
                        String rawName = machine.getRawName();
                        String suffix = serializeSuffix(machine.getNameSuffix());

                        if (!Objects.equals(known.name, rawName) || !Objects.equals(known.suffix, suffix)) {
                            // The icon is only fetched here, it is as expensive as the name itself and a machine that
                            // changed its icon changed its name too.
                            ItemStack dispRep = machine.getDisplayRep();
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            update.addRenamedEntry(known.id, rawName, suffix, dispRep);
                            known.name = rawName;
                            known.suffix = suffix;
                        }

                        /* Status changed? */
                        boolean isActive = node.isActive();

                        if (!known.online && isActive) {
                            /* Node offline -> online */
                            known.online = true;
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            known.updateNBT();
                            update.addOverwriteEntry(known.id).setOnline(true).setItems(new int[0], known.invNbt);
                        } else if (known.online && !isActive) {
                            /* Node online -> offline */
                            known.online = false;
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            update.addOverwriteEntry(known.id).setOnline(false);
                        }

                        // visibility changed?
                        final boolean machineShouldDisplay = getTerminalVisibility(machine);
                        if (known.shouldDisplay != machineShouldDisplay) {
                            known.shouldDisplay = machineShouldDisplay;
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            update.addOverwriteEntry(known.id).setTerminalVisible(machineShouldDisplay);
                        }

                        // If the size changed, we need to do a full update of inventory
                        if (known.rows != machine.rows() || known.rowSize != machine.rowSize()
                                || known.numSlots != machine.numSlots()) {
                            known.rows = machine.rows();
                            known.rowSize = machine.rowSize();
                            known.numSlots = machine.numSlots();
                            known.updateNBT();
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            update.addOverwriteEntry(known.id).setItems(new int[0], known.invNbt)
                                    .setSize(known.rows, known.rowSize, known.numSlots);
                        }

                        int priority = machine.getPriority();
                        if (known.priority != priority) {
                            known.priority = priority;
                            if (update == null) update = new PacketInterfaceTerminalUpdate();
                            update.addOverwriteEntry(known.id).setPriority(priority);
                        }

                        visited.add(machine);
                    } else {
                        /* Add a new entry (always, including hidden ones) */
                        if (update == null) update = new PacketInterfaceTerminalUpdate();
                        InvTracker entry = new InvTracker(nextId++, machine, node.isActive());
                        update.addNewEntry(entry.id, entry.name, entry.online).setSuffix(entry.suffix)
                                .setLoc(entry.x, entry.y, entry.z, entry.dim, entry.side.ordinal())
                                .setItems(entry.rows, entry.rowSize, entry.numSlots, entry.invNbt)
                                .setReps(machine.getSelfRep(), machine.getDisplayRep())
                                .setP2POutput(machine instanceof PartP2PTunnel<?>p2pTunnel && p2pTunnel.isOutput())
                                .setSupportedStackTypes(entry.supportedStackTypes).setPriority(entry.priority)
                                .setTerminalVisible(entry.shouldDisplay)
                                .setIsCraftingPatternProvider(entry.isCraftingPatternProvider)
                                .setHasUpgrades(machine instanceof IInterfaceHost);
                        // Ensure the client applies the correct visibility even if PacketAdd state gets corrupted
                        // client-side. PacketOverwrite handling is known to work reliably.
                        update.addOverwriteEntry(entry.id).setTerminalVisible(entry.shouldDisplay);
                        tracked.put(machine, entry);
                        trackedById.put(entry.id, entry);
                        visited.add(machine);
                    }
                }
            }
        }

        /* Now find any entries that we need to remove */
        Iterator<Entry<IInterfaceViewable, InvTracker>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (visited.contains(entry.getKey())) {
                continue;
            }

            if (update == null) update = new PacketInterfaceTerminalUpdate();

            trackedById.remove(entry.getValue().id);
            it.remove();
            update.addRemovalEntry(entry.getValue().id);
        }
        return update;
    }

    private static boolean getTerminalVisibility(IInterfaceViewable machine) {
        if (machine instanceof IInterfaceHost interfaceHost) {
            return interfaceHost.getInterfaceDuality().getConfigManager().getSetting(Settings.INTERFACE_TERMINAL)
                    == YesNo.YES;
        }
        return machine.shouldDisplay();
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

    /**
     * The suffix travels as a string, so components are serialized here and turned back into text on the client.
     */
    private static String serializeSuffix(final IChatComponent suffix) {
        return suffix == null ? null : IChatComponent.Serializer.func_150696_a(suffix);
    }

    private static class InvTracker {

        private final long id;
        private final IInterfaceViewable machine;
        private boolean shouldDisplay;
        private String name;
        private String suffix;
        private final IInventory patterns;
        private final World world;
        private int rows;
        private int rowSize;
        private int numSlots;
        private final int x;
        private final int y;
        private final int z;
        private final int dim;
        private int priority;
        private final ForgeDirection side;
        private boolean online;
        private final IAEStackType<?>[] supportedStackTypes;
        private NBTTagList invNbt;
        private boolean isCraftingPatternProvider;

        InvTracker(long id, IInterfaceViewable machine, boolean online) {
            DimensionalCoord location = machine.getLocation();

            this.id = id;
            this.machine = machine;
            this.shouldDisplay = getTerminalVisibility(machine);
            this.name = machine.getRawName();
            this.suffix = serializeSuffix(machine.getNameSuffix());
            this.patterns = machine.getPatterns();
            this.world = machine.getTileEntity().getWorldObj();
            this.rowSize = machine.rowSize();
            this.rows = machine.rows();
            this.numSlots = machine.numSlots();
            this.x = location.x;
            this.y = location.y;
            this.z = location.z;
            this.dim = location.getDimension();
            this.side = machine instanceof AEBasePart hasSide ? hasSide.getSide() : ForgeDirection.UNKNOWN;
            this.online = online;
            this.supportedStackTypes = machine.getSupportedStackTypes();
            this.priority = machine.getPriority();
            this.invNbt = new NBTTagList();
            this.isCraftingPatternProvider = machine.isCraftingPatternProvider();
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
            for (int slot = 0; slot < this.numSlots; ++slot) {
                ItemStack stack = this.patterns.getStackInSlot(slot);

                if (stack != null) {
                    this.invNbt.appendTag(stack.writeToNBT(new NBTTagCompound()));
                } else {
                    this.invNbt.appendTag(new NBTTagCompound());
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack getPrimaryGuiIcon() {
        return getPrimaryGui() != null ? getPrimaryGui().getIcon() : null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setGuiLink(final IGuiSub gs) {
        this.guiLink = gs;
    }
}
