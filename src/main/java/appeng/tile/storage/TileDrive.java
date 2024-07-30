/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.tile.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.base.Optional;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IPriorityHost;
import appeng.items.materials.ItemMultiMaterial;
import appeng.items.storage.ItemExtremeStorageCell;
import appeng.me.GridAccessException;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.ItemList;
import io.netty.buffer.ByteBuf;

public class TileDrive extends AENetworkInvTile implements IChestOrDrive, IPriorityHost, IGridTickable {

    private static final int INV_SIZE = 10;
    /**
     * Masks the part of {@link #state} that contains information
     */
    private static final int STATE_MASK = 0b111111111111111111111;
    private static final int STATE_ACTIVE_MASK = 1 << 20;

    private final int[] sides = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, INV_SIZE);
    private final ICellHandler[] handlersBySlot = new ICellHandler[INV_SIZE];
    private final MEInventoryHandler<IAEItemStack>[] invBySlot = new MEInventoryHandler[INV_SIZE];
    private final BaseActionSource mySrc;
    private boolean isCached = false;
    private List<MEInventoryHandler<?>> items = new ArrayList<>(INV_SIZE);
    private List<MEInventoryHandler<?>> fluids = new ArrayList<>(INV_SIZE);
    /**
     * Bit mask representing the state of all cells and the active status of the drive. The lower 20 bits represent the
     * state of the cells, with each cell state taking up 2 bits. The 21st bit represents the active status of the
     * drive.
     */
    private int state = 0;
    private int priority = 0;
    private boolean wasActive = false;

    public TileDrive() {
        this.mySrc = new MachineSource(this);
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileDrive(final ByteBuf data) {
        data.writeInt(this.state);
    }

    @Override
    public int getCellCount() {
        return INV_SIZE;
    }

    @Override
    public int getCellStatus(final int slot) {
        if (Platform.isClient()) {
            return (this.state >> (slot * 2)) & 0b11;
        }

        final ItemStack cell = this.inv.getStackInSlot(2);
        final ICellHandler ch = this.handlersBySlot[slot];

        final MEInventoryHandler<IAEItemStack> handler = this.invBySlot[slot];
        if (handler == null) {
            return 0;
        }

        if (handler.getChannel() == StorageChannel.ITEMS) {
            if (ch != null) {
                return ch.getStatusForCell(cell, handler.getInternal());
            }
        }

        if (handler.getChannel() == StorageChannel.FLUIDS) {
            if (ch != null) {
                return ch.getStatusForCell(cell, handler.getInternal());
            }
        }

        return 0;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(15, 15, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.recalculateDisplay();
        return TickRateModulation.SAME;
    }

    @Override
    public boolean isPowered() {
        if (Platform.isClient()) {
            return (this.state & STATE_ACTIVE_MASK) == STATE_ACTIVE_MASK;
        }

        return this.getProxy().isActive();
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileDrive(final ByteBuf data) {
        final int oldState = this.state;
        this.state = data.readInt() & STATE_MASK;
        return this.state != oldState;
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileDrive(final NBTTagCompound data) {
        this.isCached = false;
        this.priority = data.getInteger("priority");
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileDrive(final NBTTagCompound data) {
        data.setInteger("priority", this.priority);
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.recalculateDisplay();
    }

    private void recalculateDisplay() {
        int newState = 0;
        final boolean currentActive = this.getProxy().isActive();
        if (currentActive) {
            newState |= STATE_ACTIVE_MASK;
        }

        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        for (int x = 0; x < this.getCellCount(); x++) {
            newState |= ((this.getCellStatus(x) & 0b11) << (2 * x));
        }

        if (this.state != newState) {
            this.markForUpdate();
            this.state = newState;
        }
    }

    @MENetworkEventSubscribe
    public void channelRender(final MENetworkChannelsChanged c) {
        this.recalculateDisplay();
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public IInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
        return itemstack != null && AEApi.instance().registries().cell().isCellHandled(itemstack);
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        if (this.isCached) {
            this.isCached = false; // recalculate the storage cell.
            this.updateState();
        }

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());

            final IStorageGrid gs = this.getProxy().getStorage();
            Platform.postChanges(gs, removed, added, this.mySrc);
        } catch (final GridAccessException ignored) {}

        this.markForUpdate();
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return this.sides;
    }

    private void updateState() {
        if (!this.isCached) {
            this.items = new ArrayList<>(INV_SIZE);
            this.fluids = new ArrayList<>(INV_SIZE);

            double power = 2.0;

            for (int x = 0; x < this.inv.getSizeInventory(); x++) {
                final ItemStack is = this.inv.getStackInSlot(x);
                this.invBySlot[x] = null;
                this.handlersBySlot[x] = null;

                if (is != null) {
                    this.handlersBySlot[x] = AEApi.instance().registries().cell().getHandler(is);

                    if (this.handlersBySlot[x] != null) {
                        IMEInventoryHandler cell = this.handlersBySlot[x]
                                .getCellInventory(is, this, StorageChannel.ITEMS);

                        if (cell != null) {
                            power += this.handlersBySlot[x].cellIdleDrain(is, cell);

                            final MEInventoryHandler<IAEItemStack> ih = new MEInventoryHandler<IAEItemStack>(
                                    cell,
                                    cell.getChannel());
                            ih.setPriority(this.priority);
                            this.invBySlot[x] = ih;
                            this.items.add(ih);
                        } else {
                            cell = this.handlersBySlot[x].getCellInventory(is, this, StorageChannel.FLUIDS);

                            if (cell != null) {
                                power += this.handlersBySlot[x].cellIdleDrain(is, cell);

                                final MEInventoryHandler<IAEItemStack> ih = new MEInventoryHandler<IAEItemStack>(
                                        cell,
                                        cell.getChannel());
                                ih.setPriority(this.priority);
                                this.invBySlot[x] = ih;
                                this.fluids.add(ih);
                            }
                        }
                    }
                }
            }

            this.getProxy().setIdlePowerUsage(power);

            this.isCached = true;
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        this.updateState();
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        if (this.getProxy().isActive()) {
            this.updateState();
            return (List) (channel == StorageChannel.ITEMS ? this.items : this.fluids);
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.markDirty();

        this.isCached = false; // recalculate the storage cell.
        this.updateState();

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
    }

    public static void partitionDigitalSingularityCellToItemOnCell(ICellInventoryHandler handler) {
        ICellInventory cellInventory = handler.getCellInv();
        if (cellInventory != null) {
            if (cellInventory.getStoredItemTypes() != 0) {
                ItemStack partition = handler.getAvailableItems(new ItemList(), IterationCounter.fetchNewId())
                        .getFirstItem().getItemStack().copy();
                partition.stackSize = 1;
                cellInventory.getConfigInventory().setInventorySlotContents(0, partition);
            }
        }
    }

    public static boolean applyStickyCardToDigitalSingularityCell(ICellHandler cellHandler, ItemStack cell,
            ISaveProvider host, ICellWorkbenchItem cellItem) {
        final IMEInventoryHandler<?> inv = cellHandler.getCellInventory(cell, host, StorageChannel.ITEMS);
        if (inv instanceof ICellInventoryHandler handler) {
            final ICellInventory cellInventory = handler.getCellInv();
            if (cellInventory != null && cellInventory.getStoredItemTypes() == 1) {
                IInventory cellUpgrades = cellItem.getUpgradesInventory(cell);
                int freeSlot = -1;
                for (int i = 0; i < cellUpgrades.getSizeInventory(); i++) {
                    if (freeSlot == -1 && cellUpgrades.getStackInSlot(i) == null) {
                        freeSlot = i;
                        continue;
                    } else if (cellUpgrades.getStackInSlot(i) == null) {
                        continue;
                    }
                    if (ItemMultiMaterial.instance.getType(cellUpgrades.getStackInSlot(i)) == Upgrades.STICKY) {
                        freeSlot = -1;
                        break;
                    }
                }
                if (freeSlot != -1) {
                    Optional<ItemStack> stickyCard = AEApi.instance().definitions().materials().cardSticky()
                            .maybeStack(1);
                    if (stickyCard.isPresent()) {
                        cellUpgrades.setInventorySlotContents(freeSlot, stickyCard.get());
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    public boolean lockDigitalSingularityCells() {
        boolean res = false;
        for (int i = 0; i < this.handlersBySlot.length; i++) {
            ICellHandler cellHandler = this.handlersBySlot[i];
            final ItemStack cell = this.inv.getStackInSlot(i);
            if (ItemExtremeStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler)) {
                continue;
            }
            final IMEInventoryHandler<?> inv = cellHandler.getCellInventory(cell, this, StorageChannel.ITEMS);
            if (inv instanceof ICellInventoryHandler handler) {
                partitionDigitalSingularityCellToItemOnCell(handler);
                res = true;
            }
        }
        return res;
    }

    public int applyStickyToDigitalSingularityCells(ItemStack cards) {
        int res = 0;
        for (int i = 0; i < this.handlersBySlot.length; i++) {
            ICellHandler cellHandler = this.handlersBySlot[i];
            ItemStack cell = this.inv.getStackInSlot(i);
            if (ItemExtremeStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler)) {
                continue;
            }
            if (cell.getItem() instanceof ICellWorkbenchItem cellItem && res + 1 <= cards.stackSize) {
                if (applyStickyCardToDigitalSingularityCell(cellHandler, cell, this, cellItem)) {
                    res++;
                }
            }
        }
        if (this.isCached) {
            this.isCached = false;
            this.updateState();
        }
        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException ignored) {}
        return res;
    }
}
