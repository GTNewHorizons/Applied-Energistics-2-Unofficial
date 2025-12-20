/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container;

import static appeng.util.Platform.getItemFromPlayerInventoryBySlotIndex;
import static appeng.util.Platform.isStacksIdentical;
import static appeng.util.Platform.setPlayerInventorySlotByIndex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.PlayerSource;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.ItemSearchDTO;
import appeng.container.guisync.GuiSync;
import appeng.container.guisync.SyncData;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketHighlightBlockStorage;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketPartialItem;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.materials.ItemMultiMaterial;
import appeng.me.Grid;
import appeng.me.MachineSet;
import appeng.me.NetworkList;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.automation.UpgradeInventory;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public abstract class AEBaseContainer extends Container {

    private final InventoryPlayer invPlayer;
    private final BaseActionSource mySrc;
    private final HashSet<Integer> locked = new HashSet<>();
    private final TileEntity tileEntity;
    private final IPart part;
    private final IGuiItemObject obj;
    private final List<PacketPartialItem> dataChunks = new LinkedList<>();
    private final HashMap<Integer, SyncData> syncData = new HashMap<>();
    private boolean isContainerValid = true;
    private String customName;
    private ContainerOpenContext openContext;
    private IMEInventoryHandler<IAEItemStack> cellItemInv;
    private IMEInventoryHandler<IAEFluidStack> cellFluidInv;
    private IEnergySource powerSrc;
    private boolean sentCustomName;
    private int ticksSinceCheck = 900;
    private IAEStack<?> clientRequestedTargetItem = null;
    private PrimaryGui primaryGui;
    private final int targetSlotIndex;

    @Deprecated
    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart) {
        this(ip, myTile, myPart, null);
    }

    @Deprecated
    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart,
            final IGuiItemObject gio) {
        this.invPlayer = ip;
        this.tileEntity = myTile;
        this.part = myPart;
        this.obj = gio;
        this.mySrc = new PlayerSource(ip.player, this.getActionHost());

        this.targetSlotIndex = this.tileEntity == null && getTarget() instanceof IInventorySlotAware isa
                ? isa.getInventorySlot()
                : Integer.MIN_VALUE;

        this.prepareSync();
    }

    protected IActionHost getActionHost() {
        if (this.obj instanceof IActionHost) {
            return (IActionHost) this.obj;
        }

        if (this.tileEntity instanceof IActionHost) {
            return (IActionHost) this.tileEntity;
        }

        if (this.part instanceof IActionHost) {
            return (IActionHost) this.part;
        }

        return null;
    }

    private void prepareSync() {
        walkSyncFields(0, this.getClass().getFields(), new Field[0]);
    }

    private void walkSyncFields(int offset, final Field[] fields, final Field[] currentIndirections) {
        for (final Field f : fields) {
            if (f.isAnnotationPresent(GuiSync.Recurse.class)) {
                final GuiSync.Recurse annotation = f.getAnnotation(GuiSync.Recurse.class);
                walkSyncFields(
                        offset + annotation.value(),
                        f.getType().getFields(),
                        ArrayUtils.add(currentIndirections, f));
            }
            if (f.isAnnotationPresent(GuiSync.class)) {
                final GuiSync annotation = f.getAnnotation(GuiSync.class);
                final int channel = offset + annotation.value();
                if (this.syncData.containsKey(channel)) {
                    AELog.warn("Channel already in use: " + channel + " for " + f.getName());
                } else {
                    this.syncData.put(channel, new SyncData(this, currentIndirections, f, channel));
                }
            }
        }
    }

    public AEBaseContainer(final InventoryPlayer ip, final Object anchor) {
        this.invPlayer = ip;
        this.tileEntity = anchor instanceof TileEntity ? (TileEntity) anchor : null;
        this.part = anchor instanceof IPart ? (IPart) anchor : null;
        this.obj = anchor instanceof IGuiItemObject ? (IGuiItemObject) anchor : null;

        if (this.tileEntity == null && this.part == null && this.obj == null) {
            throw new IllegalArgumentException("Must have a valid anchor, instead " + anchor + " in " + ip);
        }

        if (obj instanceof IInventorySlotAware isa) {
            final int slotIndex = isa.getInventorySlot();
            this.lockPlayerInventorySlot(slotIndex);
        }

        this.mySrc = new PlayerSource(ip.player, this.getActionHost());

        this.targetSlotIndex = this.tileEntity == null && getTarget() instanceof IInventorySlotAware isa
                ? isa.getInventorySlot()
                : Integer.MIN_VALUE;

        this.prepareSync();
    }

    public void postPartial(final PacketPartialItem packetPartialItem) {
        this.dataChunks.add(packetPartialItem);
        if (packetPartialItem.getPageCount() == this.dataChunks.size()) {
            this.parsePartials();
        }
    }

    private void parsePartials() {
        int total = 0;
        for (final PacketPartialItem ppi : this.dataChunks) {
            total += ppi.getSize();
        }

        final byte[] buffer = new byte[total];
        int cursor = 0;

        for (final PacketPartialItem ppi : this.dataChunks) {
            cursor = ppi.write(buffer, cursor);
        }

        try {
            final NBTTagCompound data = CompressedStreamTools.readCompressed(new ByteArrayInputStream(buffer));
            if (data != null) {
                this.setTargetStack(Platform.readStackNBT(data));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }

        this.dataChunks.clear();
    }

    public IAEStack<?> getTargetStack() {
        return this.clientRequestedTargetItem;
    }

    public void setTargetStack(final IAEStack<?> stack) {
        // client doesn't need to re-send, makes for lower overhead rapid packets.
        if (Platform.isClient()) {
            if (stack == null) {
                if (this.clientRequestedTargetItem == null) {
                    return;
                }
            } else {
                if (stack.equals(this.clientRequestedTargetItem)) {
                    return;
                }
            }

            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            final NBTTagCompound nbt = new NBTTagCompound();

            if (stack != null) {
                Platform.writeStackNBT(stack, nbt, true);
            }

            try {
                CompressedStreamTools.writeCompressed(nbt, stream);

                final int maxChunkSize = 30000;
                final List<byte[]> miniPackets = new LinkedList<>();

                final byte[] data = stream.toByteArray();

                final ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, stream.size());
                while (bis.available() > 0) {
                    final int nextBLock = bis.available() > maxChunkSize ? maxChunkSize : bis.available();
                    final byte[] nextSegment = new byte[nextBLock];
                    bis.read(nextSegment);
                    miniPackets.add(nextSegment);
                }
                bis.close();
                stream.close();

                int page = 0;
                for (final byte[] packet : miniPackets) {
                    final PacketPartialItem ppi = new PacketPartialItem(page, miniPackets.size(), packet);
                    page++;
                    NetworkHandler.instance.sendToServer(ppi);
                }
            } catch (final IOException e) {
                AELog.debug(e);
                return;
            }
        }

        this.clientRequestedTargetItem = stack == null ? null : stack.copy();
    }

    public BaseActionSource getActionSource() {
        return this.mySrc;
    }

    public void verifyPermissions(final SecurityPermissions security, final boolean requirePower) {
        if (Platform.isClient()) {
            return;
        }

        this.ticksSinceCheck++;
        if (this.ticksSinceCheck < 20) {
            return;
        }

        this.ticksSinceCheck = 0;
        this.setValidContainer(this.isValidContainer() && this.hasAccess(security, requirePower));
    }

    protected boolean hasAccess(final SecurityPermissions perm, final boolean requirePower) {
        final IActionHost host = this.getActionHost();

        if (host != null) {
            final IGridNode gn = host.getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    if (requirePower) {
                        final IEnergyGrid eg = g.getCache(IEnergyGrid.class);
                        if (!eg.isNetworkPowered()) {
                            return false;
                        }
                    }

                    final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                    if (sg.hasPermission(this.getInventoryPlayer().player, perm)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void lockPlayerInventorySlot(final int idx) {
        this.locked.add(idx);
    }

    public Object getTarget() {
        if (this.tileEntity != null) {
            return this.tileEntity;
        }
        if (this.part != null) {
            return this.part;
        }
        if (this.obj != null) {
            return this.obj;
        }
        return null;
    }

    public InventoryPlayer getPlayerInv() {
        return this.getInventoryPlayer();
    }

    public TileEntity getTileEntity() {
        return this.tileEntity;
    }

    public final void updateFullProgressBar(final int idx, final long value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
            return;
        }

        this.updateProgressBar(idx, (int) value);
    }

    public void stringSync(final int idx, final String value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
        }
    }

    protected void bindPlayerInventory(final InventoryPlayer inventoryPlayer, final int offsetX, final int offsetY) {
        // bind player inventory
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.locked.contains(j + i * 9 + 9)) {
                    this.addSlotToContainer(
                            new SlotDisabled(inventoryPlayer, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                } else {
                    this.addSlotToContainer(
                            new SlotPlayerInv(inventoryPlayer, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                }
            }
        }

        // bind player hotbar
        for (int i = 0; i < 9; i++) {
            if (this.locked.contains(i)) {
                this.addSlotToContainer(new SlotDisabled(inventoryPlayer, i, 8 + i * 18 + offsetX, 58 + offsetY));
            } else {
                this.addSlotToContainer(new SlotPlayerHotBar(inventoryPlayer, i, 8 + i * 18 + offsetX, 58 + offsetY));
            }
        }
    }

    @Override
    protected Slot addSlotToContainer(final Slot newSlot) {
        if (newSlot instanceof AppEngSlot s) {
            s.setContainer(this);
            return super.addSlotToContainer(newSlot);
        } else {
            throw new IllegalArgumentException("Invalid Slot [" + newSlot + "]for AE Container instead of AppEngSlot.");
        }
    }

    @Override
    public void detectAndSendChanges() {
        this.sendCustomName();

        if (Platform.isServer()) {
            for (final ICrafting crafter : this.crafters) {
                for (final SyncData sd : this.syncData.values()) {
                    sd.tick(crafter);
                }
            }
        }

        portableSourceTick();

        super.detectAndSendChanges();
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return null;
        }

        final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!

        if (clickSlot instanceof SlotDisabled || clickSlot instanceof SlotInaccessible) {
            return null;
        }
        if (clickSlot != null && clickSlot.getHasStack()) {
            ItemStack tis = clickSlot.getStack();

            if (tis == null) {
                return null;
            }

            final List<Slot> selectedSlots = new ArrayList<>();

            /**
             * Gather a list of valid destinations.
             */
            if (clickSlot.isPlayerSide()) {
                // target slots in the container...
                for (final Object inventorySlot : this.inventorySlots) {
                    final AppEngSlot cs = (AppEngSlot) inventorySlot;

                    if (!(cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                        if (cs.isItemValid(tis)) {
                            selectedSlots.add(cs);
                        }
                    }
                }
            } else {
                // target slots in the container...
                for (final Object inventorySlot : this.inventorySlots) {
                    final AppEngSlot cs = (AppEngSlot) inventorySlot;

                    if ((cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                        if (cs.isItemValid(tis)) {
                            selectedSlots.add(cs);
                        }
                    }
                }
            }

            /**
             * Handle Fake Slot Shift clicking.
             */
            if (selectedSlots.isEmpty() && clickSlot.isPlayerSide()) {
                if (tis != null) {
                    // target slots in the container...
                    for (final Object inventorySlot : this.inventorySlots) {
                        final AppEngSlot cs = (AppEngSlot) inventorySlot;
                        final ItemStack destination = cs.getStack();

                        if (!(cs.isPlayerSide()) && cs instanceof SlotFake) {
                            if (Platform.isSameItemPrecise(destination, tis)) {
                                break;
                            } else if (destination == null) {
                                cs.putStack(tis.copy());
                                cs.onSlotChanged();
                                this.updateSlot(cs);
                                break;
                            }
                        }
                    }
                }
            }

            if (tis != null) {
                // find partials..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled) {
                        continue;
                    }

                    if (d.isItemValid(tis)) {
                        if (d.getHasStack()) {
                            final ItemStack t = d.getStack();

                            if (Platform.isSameItemPrecise(tis, t)) // t.isItemEqual(tis))
                            {
                                int maxSize = t.getMaxStackSize();
                                if (maxSize > d.getSlotStackLimit()) {
                                    maxSize = d.getSlotStackLimit();
                                }

                                int placeAble = maxSize - t.stackSize;

                                if (tis.stackSize < placeAble) {
                                    placeAble = tis.stackSize;
                                }

                                t.stackSize += placeAble;
                                tis.stackSize -= placeAble;

                                if (tis.stackSize <= 0) {
                                    clickSlot.putStack(null);
                                    d.onSlotChanged();

                                    // if ( hasMETiles ) updateClient();

                                    this.updateSlot(clickSlot);
                                    this.updateSlot(d);
                                    return null;
                                } else {
                                    this.updateSlot(d);
                                }
                            }
                        }
                    }
                }

                // any match..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled) {
                        continue;
                    }

                    // For shift click upgrade card logic
                    if (ItemMultiMaterial.instance.getType(tis) != null) {
                        // Check now container is upgradeable or it's subclass
                        if (ContainerUpgradeable.class.isAssignableFrom(this.getClass())) {
                            // Check source or target
                            if (!((d.inventory instanceof UpgradeInventory)
                                    || (clickSlot.inventory instanceof UpgradeInventory)
                                    || (d.inventory instanceof ContainerCellWorkbench.Upgrades))) {
                                continue;
                            }
                        }
                    }

                    if (d.isItemValid(tis)) {
                        if (d.getHasStack()) {
                            final ItemStack t = d.getStack();

                            if (Platform.isSameItemPrecise(t, tis)) {
                                int maxSize = t.getMaxStackSize();
                                if (d.getSlotStackLimit() < maxSize) {
                                    maxSize = d.getSlotStackLimit();
                                }

                                int placeAble = maxSize - t.stackSize;

                                if (tis.stackSize < placeAble) {
                                    placeAble = tis.stackSize;
                                }

                                t.stackSize += placeAble;
                                tis.stackSize -= placeAble;

                                if (tis.stackSize <= 0) {
                                    clickSlot.putStack(null);
                                    d.onSlotChanged();

                                    // if ( worldEntity != null )
                                    // worldEntity.markDirty();
                                    // if ( hasMETiles ) updateClient();

                                    this.updateSlot(clickSlot);
                                    this.updateSlot(d);
                                    return null;
                                } else {
                                    this.updateSlot(d);
                                }
                            }
                        } else {
                            int maxSize = tis.getMaxStackSize();
                            if (maxSize > d.getSlotStackLimit()) {
                                maxSize = d.getSlotStackLimit();
                            }

                            final ItemStack tmp = tis.copy();
                            if (tmp.stackSize > maxSize) {
                                tmp.stackSize = maxSize;
                            }

                            tis.stackSize -= tmp.stackSize;
                            d.putStack(tmp);

                            if (tis.stackSize <= 0) {
                                clickSlot.putStack(null);
                                d.onSlotChanged();

                                // if ( worldEntity != null )
                                // worldEntity.markDirty();
                                // if ( hasMETiles ) updateClient();

                                this.updateSlot(clickSlot);
                                this.updateSlot(d);
                                return null;
                            } else {
                                this.updateSlot(d);
                            }
                        }
                    }
                }
            }

            clickSlot.putStack(tis != null ? tis.copy() : null);
        }

        this.updateSlot(clickSlot);
        return null;
    }

    @Override
    public final void updateProgressBar(final int idx, final int value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update((long) value);
        }
    }

    @Override
    public boolean canInteractWith(final EntityPlayer entityplayer) {
        if (this.isValidContainer()) {
            if (this.tileEntity instanceof IInventory) {
                return ((IInventory) this.tileEntity).isUseableByPlayer(entityplayer);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canDragIntoSlot(final Slot s) {
        return ((AppEngSlot) s).isDraggable();
    }

    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            final Slot s = this.getSlot(slot);

            if (s instanceof SlotCraftingTerm) {
                switch (action) {
                    case CRAFT_SHIFT:
                    case CRAFT_ITEM:
                    case CRAFT_STACK:
                        ((SlotCraftingTerm) s).doClick(action, player);
                        this.updateHeld(player);
                    default:
                }
            }

            if (s instanceof SlotFake) {
                final ItemStack hand = player.inventory.getItemStack();

                switch (action) {
                    case PICKUP_OR_SET_DOWN -> {
                        if (hand == null) {
                            s.putStack(null);
                        } else {
                            s.putStack(hand.copy());
                        }
                    }
                    case PLACE_SINGLE -> {
                        if (hand != null) {
                            final ItemStack is = hand.copy();
                            is.stackSize = 1;
                            s.putStack(is);
                        }
                    }
                    case SPLIT_OR_PLACE_SINGLE -> {
                        ItemStack is = s.getStack();
                        if (is != null) {
                            if (hand == null) {
                                if (is.stackSize > 1) {
                                    is.stackSize--;
                                }
                            } else if (hand.isItemEqual(is)) {
                                is.stackSize = Math.min(is.getMaxStackSize(), is.stackSize + 1);
                            } else {
                                is = hand.copy();
                                is.stackSize = 1;
                            }

                            s.putStack(is);
                        } else if (hand != null) {
                            is = hand.copy();
                            is.stackSize = 1;
                            s.putStack(is);
                        }
                    }
                    default -> {}
                }
            }

            if (action == InventoryAction.MOVE_REGION) {
                if (s instanceof SlotFake || s instanceof SlotPatternTerm) return;
                // SlotPatternTerm shouldn't transfer its ItemStack to the player

                final List<Slot> from = new LinkedList<>();

                for (final Object j : this.inventorySlots) {
                    if (j instanceof Slot && j.getClass() == s.getClass()) {
                        from.add((Slot) j);
                    }
                }

                for (final Slot fr : from) {
                    this.transferStackInSlot(player, fr.slotNumber);
                }
            }

            return;
        }

        // get target item.
        final IAEItemStack slotItem = this.clientRequestedTargetItem instanceof IAEItemStack ais ? ais : null;

        switch (action) {
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode && slotItem != null) {
                    final ItemStack is = slotItem.getItemStack();
                    is.stackSize = is.getMaxStackSize();
                    player.inventory.setItemStack(is);
                    this.updateHeld(player);
                }
            }
            case FIND_ITEMS -> {
                final Class<? extends IGridHost>[] checkedMachineClasses = new Class[] { TileDrive.class,
                        TileChest.class, PartStorageBus.class, };
                if (slotItem == null) return;

                IGrid g = null;
                // Pull grid
                final IActionHost host = this.getActionHost();
                if (host == null) return;

                final IGridNode gn = host.getActionableNode();
                if (gn != null) {
                    g = gn.getGrid();
                }

                if (g == null) return;

                List<ItemSearchDTO> coords = new ArrayList<>();

                List<IGridNode> machineList = new ArrayList<>();
                Class<? extends IGridHost> classType = null;
                if (slotItem.getChannel() == StorageChannel.ITEMS) {
                    classType = PartStorageBus.class;
                } else if (slotItem.getChannel() == StorageChannel.FLUIDS) {
                    // TODO Support Fluids for item searching
                }

                // Retrieve list of all grids
                NetworkList grids = g.getAllRecursiveGridConnections(classType);

                for (Grid subnet : grids) {
                    for (Class<? extends IGridHost> type : checkedMachineClasses) {
                        MachineSet subMachines = (MachineSet) subnet.getMachines(type);
                        if (!subMachines.isEmpty()) {
                            machineList.addAll(subMachines);
                        }
                    }
                }

                int machineCount = 0;
                for (IGridNode gridNode : machineList) {
                    if (machineCount > AEConfig.instance.maxMachineChecks) {
                        break;
                    }
                    machineCount++;
                    IGridHost machine = gridNode.getMachine();

                    if (machine instanceof TileDrive innerMachine) {
                        for (int i = 0; i < innerMachine.getSizeInventory(); i++) {
                            IMEInventoryHandler<IAEItemStack> cell = innerMachine.getCellInvBySlot(i);
                            if (cell == null || cell.getChannel() != slotItem.getChannel()) continue;

                            IAEStack<IAEItemStack> result = cell
                                    .getAvailableItem(slotItem, IterationCounter.fetchNewId());
                            if (result == null) continue;

                            String blockName = innerMachine.getCustomName();

                            coords.add(
                                    new ItemSearchDTO(
                                            innerMachine.getLocation(),
                                            result,
                                            blockName,
                                            i,
                                            innerMachine.getForward(),
                                            innerMachine.getUp()));
                        }
                    }
                    if (machine instanceof PartStorageBus innerMachine) {
                        if (innerMachine.getConnectedGrid() != null
                                || innerMachine.getStorageChannel() != StorageChannel.ITEMS) { // Check if storageBus is
                                                                                               // subnet
                            continue;
                        }
                        MEInventoryHandler<IAEItemStack> handler = innerMachine.getInternalHandler();
                        if (handler == null) continue;
                        IAEStack<IAEItemStack> result = handler
                                .getAvailableItem(slotItem, IterationCounter.fetchNewId());
                        if (result == null) continue;
                        coords.add(new ItemSearchDTO(innerMachine.getLocation(), result, innerMachine.getCustomName()));
                    }
                    if (machine instanceof TileChest innerMachine) {
                        try {
                            IMEInventoryHandler handler = innerMachine.getHandler(slotItem.getChannel());
                            IAEStack result = handler.getAvailableItem(slotItem, IterationCounter.fetchNewId());
                            if (result == null) continue;
                            coords.add(
                                    new ItemSearchDTO(
                                            innerMachine.getLocation(),
                                            result,
                                            innerMachine.getCustomName()));
                        } catch (Exception e) {
                            // :/
                        }
                    }
                }

                this.highlightBlocks(player, coords);
            }
            default -> {}
        }
    }

    protected void updateHeld(final EntityPlayerMP p) {
        if (Platform.isServer()) {
            try {
                NetworkHandler.instance.sendTo(
                        new PacketInventoryAction(
                                InventoryAction.UPDATE_HAND,
                                0,
                                AEItemStack.create(p.inventory.getItemStack())),
                        p);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    protected void highlightBlocks(final EntityPlayerMP p, List<ItemSearchDTO> coords) {
        if (Platform.isServer()) {
            try {
                NetworkHandler.instance.sendTo(new PacketHighlightBlockStorage(coords), p);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    private void updateSlot(final Slot clickSlot) {
        // ???
        this.detectAndSendChanges();
    }

    private void sendCustomName() {
        if (!this.sentCustomName) {
            this.sentCustomName = true;
            if (Platform.isServer()) {
                ICustomNameObject name = null;

                if (this.part instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.part;
                }

                if (this.tileEntity instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.tileEntity;
                }

                if (this.obj instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.obj;
                }

                if (this instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this;
                }

                if (name != null) {
                    if (name.hasCustomName()) {
                        this.setCustomName(name.getCustomName());
                    }

                    if (this.getCustomName() != null) {
                        try {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig("CustomName", this.getCustomName()),
                                    (EntityPlayerMP) this.getInventoryPlayer().player);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }
        }
    }

    public void swapSlotContents(final int slotA, final int slotB) {
        final int inventorySize = this.inventorySlots.size();
        if (slotA < 0 || slotA >= inventorySize || slotB < 0 || slotB >= inventorySize) {
            return;
        }
        final Slot a = this.getSlot(slotA);
        final Slot b = this.getSlot(slotB);

        // NPE protection...
        if (a == null || b == null) {
            return;
        }

        final ItemStack isA = a.getStack();
        final ItemStack isB = b.getStack();

        // something to do?
        if (isA == null && isB == null) {
            return;
        }

        // can take?

        if (isA != null && !a.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        if (isB != null && !b.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        // swap valid?

        if (isB != null && !a.isItemValid(isB)) {
            return;
        }

        if (isA != null && !b.isItemValid(isA)) {
            return;
        }

        ItemStack testA = isB == null ? null : isB.copy();
        ItemStack testB = isA == null ? null : isA.copy();

        // can put some back?
        if (testA != null && testA.stackSize > a.getSlotStackLimit()) {
            if (testB != null) {
                return;
            }

            final int totalA = testA.stackSize;
            testA.stackSize = a.getSlotStackLimit();
            testB = testA.copy();

            testB.stackSize = totalA - testA.stackSize;
        }

        if (testB != null && testB.stackSize > b.getSlotStackLimit()) {
            if (testA != null) {
                return;
            }

            final int totalB = testB.stackSize;
            testB.stackSize = b.getSlotStackLimit();
            testA = testB.copy();

            testA.stackSize = totalB - testA.stackSize;
        }

        a.putStack(testA);
        b.putStack(testB);
    }

    public void onUpdate(final String field, final Object oldValue, final Object newValue) {}

    public void onSlotChange(final Slot s) {}

    public boolean isValidForSlot(final Slot s, final ItemStack i) {
        return true;
    }

    public IMEInventoryHandler<IAEItemStack> getCellInventory() {
        return this.cellItemInv;
    }

    public void setCellInventory(final IMEInventoryHandler<IAEItemStack> cellInv) {
        this.cellItemInv = cellInv;
    }

    public IMEInventoryHandler<IAEFluidStack> getCellFluidInventory() {
        return this.cellFluidInv;
    }

    public void setCellFluidInventory(final IMEInventoryHandler<IAEFluidStack> cellInv) {
        this.cellFluidInv = cellInv;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(final String customName) {
        this.customName = customName;
    }

    public InventoryPlayer getInventoryPlayer() {
        return this.invPlayer;
    }

    public boolean isValidContainer() {
        return this.isContainerValid;
    }

    public void setValidContainer(final boolean isContainerValid) {
        this.isContainerValid = isContainerValid;
    }

    public ContainerOpenContext getOpenContext() {
        return this.openContext;
    }

    public void setOpenContext(final ContainerOpenContext openContext) {
        this.openContext = openContext;
    }

    public IEnergySource getPowerSource() {
        return this.powerSrc;
    }

    public void setPowerSource(final IEnergySource powerSrc) {
        this.powerSrc = powerSrc;
    }

    // used for secondary gui for able handle tertiary, quaternary etc. gui
    public void setPrimaryGui(PrimaryGui primaryGui) {
        this.primaryGui = primaryGui;
    }

    public PrimaryGui createPrimaryGui() {
        ContainerOpenContext context = getOpenContext();

        final PrimaryGui pGui = new PrimaryGui(
                GuiBridge.getGuiByContainerClass(this.getClass()),
                getTarget() instanceof IPrimaryGuiIconProvider ip ? ip.getPrimaryGuiIcon()
                        : AEApi.instance().definitions().parts().terminal().maybeStack(1).orNull(),
                context.getTile(),
                context.getSide());

        pGui.setSlotIndex(this.targetSlotIndex);
        return pGui;
    }

    @Nullable
    public PrimaryGui getPrimaryGui() {
        return primaryGui;
    }

    public int getTargetSlotIndex() {
        return this.targetSlotIndex;
    }

    private int ticks = 0;
    private double powerMultiplier = 0.5;

    private double getPowerMultiplier() {
        return this.powerMultiplier;
    }

    protected void setPowerMultiplier(final double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }

    private void portableSourceTick() {
        final Object obj = this.getTarget();
        if (obj instanceof WirelessTerminalGuiObject wtgo) {
            if (!wtgo.rangeCheck()) {
                if (Platform.isServer() && this.isValidContainer()) {
                    this.getPlayerInv().player.addChatMessage(PlayerMessages.OutOfRange.toChat());
                }

                this.setValidContainer(false);
            } else {
                this.setPowerMultiplier(AEConfig.instance.wireless_getDrainRate(wtgo.getRange()));
            }
        }

        if (obj instanceof IPortableCell ipc) {
            final int slotIndex = ipc.getInventorySlot();
            final ItemStack currentItem = getItemFromPlayerInventoryBySlotIndex(
                    this.getInventoryPlayer().player,
                    slotIndex);
            final ItemStack is = ipc.getItemStack();
            if (currentItem != is) {
                if (currentItem != null) {
                    if (Platform.isSameItem(is, currentItem)) {
                        setPlayerInventorySlotByIndex(this.getInventoryPlayer().player, slotIndex, is);
                    } else {
                        this.setValidContainer(false);
                    }
                } else {
                    this.setValidContainer(false);
                }
            }
        }

        if (obj instanceof IEnergySource ies) {
            // drain 1 ae t
            this.ticks++;
            if (this.ticks > 10) {
                ies.extractAEPower(this.getPowerMultiplier() * this.ticks, Actionable.MODULATE, PowerMultiplier.CONFIG);
                this.ticks = 0;
            }
        }
    }

    // need for universal terminal, because getMode(ItemStack terminal) always have outdated mode
    private int switchAbleGuiItemNext = Integer.MIN_VALUE;

    public int getSwitchAbleGuiNext() {
        return this.switchAbleGuiItemNext;
    }

    public void setSwitchAbleGuiNext(int n) {
        this.switchAbleGuiItemNext = n;
    }

    protected void updateVirtualSlots(StorageName invName, IAEStackInventory inventory,
            IAEStack<?>[] clientSlotsStacks) {
        var list = new Int2ObjectOpenHashMap<IAEStack<?>>();
        for (int i = 0; i < inventory.getSizeInventory(); ++i) {
            IAEStack<?> aes = inventory.getAEStackInSlot(i);
            IAEStack<?> aesClient = clientSlotsStacks[i];

            if (!isStacksIdentical(aes, aesClient)) {
                list.put(i, aes);
                clientSlotsStacks[i] = aes != null ? aes.copy() : null;
            }
        }

        if (!list.isEmpty()) {
            for (ICrafting crafter : this.crafters) {
                final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                NetworkHandler.instance.sendTo(new PacketVirtualSlot(invName, list), emp);
            }
        }
    }
}
