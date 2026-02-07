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

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.events.MENetworkPowerStorage;
import appeng.api.networking.events.MENetworkPowerStorage.PowerEventType;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalTypeFilterProvider;
import appeng.api.storage.MEMonitorHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AEColor;
import appeng.api.util.IConfigManager;
import appeng.core.localization.GuiText;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.items.storage.ItemBasicStorageCell;
import appeng.me.GridAccessException;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.MonitorableTypeFilter;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class TileChest extends AENetworkPowerTile implements IMEChest, IFluidHandler, ITerminalHost, IPriorityHost,
        IConfigManagerHost, IColorableTile, IGridTickable, IPrimaryGuiIconProvider, ITerminalTypeFilterProvider {

    private static final ChestNoHandler NO_HANDLER = new ChestNoHandler();
    private static final int[] SIDES = { 0 };
    private static final int[] FRONT = { 1 };
    private static final int[] NO_SLOTS = {};
    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 2, Integer.MAX_VALUE, true);
    private final BaseActionSource mySrc = new MachineSource(this);
    private final IConfigManager config = new ConfigManager(this);
    private ItemStack storageType;
    private int priority = 0;
    /**
     * Bit mask representing the state of the cell and the active status of the chest. The lower 2 bits represent the
     * state of the cell, the 3rd bit represents the active status of the chest.
     */
    private int state = 0;
    private int type = 0;
    private boolean wasActive = false;
    private AEColor paintedColor = AEColor.Transparent;
    private boolean isCached = false;
    private ICellHandler cellHandler;
    @SuppressWarnings("rawtypes")
    private final Map<IAEStackType<?>, MEMonitorHandler> cellMap = new IdentityHashMap<>();
    private boolean displayNeedsUpdate;
    @NotNull
    private final MonitorableTypeFilter typeFilters = new MonitorableTypeFilter();

    public TileChest() {
        this.setInternalMaxPower(PowerMultiplier.CONFIG.multiply(40));
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.config.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.config.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.config.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.setInternalPublicPowerStorage(true);
        this.setInternalPowerFlow(AccessRestriction.WRITE);
    }

    @Override
    protected void PowerEvent(final PowerEventType x) {
        if (x == PowerEventType.REQUEST_POWER) {
            try {
                this.getProxy().getGrid().postEvent(new MENetworkPowerStorage(this, PowerEventType.REQUEST_POWER));
            } catch (final GridAccessException e) {
                // :(
            }
        } else {
            displayNeedsUpdate = true;
        }
    }

    private void recalculateDisplay() {
        this.displayNeedsUpdate = false;

        int newState = 0;
        int newType = 0;

        for (int x = 0; x < this.getCellCount(); x++) {
            newState |= (this.getCellStatus(x) << (3 * x));
            newType |= (this.getCellType(x) << (2 * x));
        }

        if (this.isPowered()) {
            newState |= 0b1000;
        }

        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        if (this.state != newState || this.type != newType) {
            this.markForUpdate();
            this.state = newState;
            this.type = newType;
        }
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
    public int getCellCount() {
        return 1;
    }

    public IMEInventoryHandler getInternalHandler(final IAEStackType<?> type) {
        return this.cellHandler.getCellInventory(this.inv.getStackInSlot(1), this, type);
    }

    public IMEInventoryHandler getHandler(final IAEStackType<?> type) throws ChestNoHandler {
        if (!this.isCached) {
            this.cellMap.clear();

            final ItemStack is = this.inv.getStackInSlot(1);
            if (is != null) {
                this.isCached = true;
                this.cellHandler = AEApi.instance().registries().cell().getHandler(is);
                if (this.cellHandler != null) {
                    double power = 1.0;

                    for (IAEStackType<?> internalType : AEStackTypeRegistry.getAllTypes()) {
                        final IMEInventoryHandler cell = this.cellHandler.getCellInventory(is, this, internalType);
                        if (cell != null) {
                            this.cellMap.put(internalType, this.wrap(cell));
                            power += this.cellHandler.cellIdleDrain(is, cell);
                            break;
                        }
                    }

                    this.getProxy().setIdlePowerUsage(power);
                }
            }
        }

        IMEInventoryHandler cell = this.cellMap.get(type);
        if (cell == null) {
            throw NO_HANDLER;
        }
        return cell;
    }

    private <StackType extends IAEStack<?>> MEMonitorHandler<StackType> wrap(final IMEInventoryHandler h) {
        if (h == null) {
            return null;
        }

        final MEInventoryHandler ih = new MEInventoryHandler(h, h.getStackType());
        ih.setPriority(this.priority);

        final MEMonitorHandler<StackType> g = new ChestMonitorHandler<StackType>(ih);
        g.addListener(new ChestNetNotifier(h.getStackType()), g);

        return g;
    }

    public BaseActionSource getActionSource() {
        return mySrc;
    }

    @Override
    public int getCellStatus(final int slot) {
        if (Platform.isClient()) {
            return (this.state >> (slot * 3)) & 0b111;
        }

        final ItemStack cell = this.inv.getStackInSlot(1);
        final ICellHandler ch = AEApi.instance().registries().cell().getHandler(cell);

        if (ch != null) {
            for (IAEStackType<?> internalType : AEStackTypeRegistry.getAllTypes()) {
                try {
                    final IMEInventoryHandler handler = this.getHandler(internalType);
                    if (handler instanceof ChestMonitorHandler) {
                        return ch.getStatusForCell(cell, ((ChestMonitorHandler) handler).getInternalHandler());
                    }
                } catch (final ChestNoHandler ignored) {}
            }
        }

        return 0;
    }

    @Override
    public int getCellType(final int slot) {
        if (Platform.isClient()) {
            return (this.type >> (slot * 2)) & 0b11;
        }

        final ItemStack cell = this.inv.getStackInSlot(1);
        final ICellHandler ch = AEApi.instance().registries().cell().getHandler(cell);
        ChestMonitorHandler tempCMH = null;
        if (ch != null) {
            for (IAEStackType<?> internalType : AEStackTypeRegistry.getAllTypes()) {
                try {
                    final IMEInventoryHandler handler = this.getHandler(internalType);
                    if (handler instanceof ChestMonitorHandler CMH) {
                        tempCMH = CMH;
                        break;
                    }
                } catch (final ChestNoHandler ignored) {}
            }

            if (tempCMH != null && tempCMH.getInternalHandler() instanceof ICellCacheRegistry iccr) {
                return switch (iccr.getCellType()) {
                    case ITEM -> 0;
                    case FLUID -> 1;
                    case ESSENTIA -> 2;
                };
            }
        }

        return 0;
    }

    @Override
    public boolean isPowered() {
        if (Platform.isClient()) {
            return (this.state & 0b1000) == 0b1000;
        }

        boolean gridPowered = this.getAECurrentPower() > 64;

        if (!gridPowered) {
            try {
                gridPowered = this.getProxy().getEnergy().isNetworkPowered();
            } catch (final GridAccessException ignored) {}
        }

        return super.getAECurrentPower() > 1 || gridPowered;
    }

    @Override
    protected double extractAEPower(final double amt, final Actionable mode) {
        double stash = 0.0;

        try {
            final IEnergyGrid eg = this.getProxy().getEnergy();
            stash = eg.extractAEPower(amt, mode, PowerMultiplier.ONE);
            if (stash >= amt) {
                return stash;
            }
        } catch (final GridAccessException e) {
            // no grid :(
        }

        // local battery!
        return super.extractAEPower(amt - stash, mode) + stash;
    }

    @TileEvent(TileEventType.TICK)
    public void Tick_TileChest() {
        if (this.worldObj.isRemote) {
            return;
        }

        final double idleUsage = this.getProxy().getIdlePowerUsage();

        try {
            if (!this.getProxy().getEnergy().isNetworkPowered()) {
                final double powerUsed = this.extractAEPower(idleUsage, Actionable.MODULATE, PowerMultiplier.CONFIG); // drain
                if (powerUsed + 0.1 >= idleUsage != (this.state & 0b1000) > 0) {
                    displayNeedsUpdate = true;
                }
            }
        } catch (final GridAccessException e) {
            final double powerUsed = this
                    .extractAEPower(this.getProxy().getIdlePowerUsage(), Actionable.MODULATE, PowerMultiplier.CONFIG); // drain
            if (powerUsed + 0.1 >= idleUsage != (this.state & 0b1000) > 0) {
                displayNeedsUpdate = true;
            }
        }

        if (displayNeedsUpdate) {
            recalculateDisplay();
        }

        if (this.inv.getStackInSlot(0) != null) {
            this.tryToStoreContents();
        }
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileChest(final ByteBuf data) {
        data.writeByte(this.state);
        data.writeByte(this.type);
        data.writeByte(this.paintedColor.ordinal());

        final ItemStack is = this.inv.getStackInSlot(1);

        if (is == null) {
            data.writeInt(0);
        } else {
            data.writeInt((is.getItemDamage() << Platform.DEF_OFFSET) | Item.getIdFromItem(is.getItem()));
        }
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileChest(final ByteBuf data) {
        final int oldState = this.state;
        final int oldTypes = this.type;
        final ItemStack oldType = this.storageType;

        this.state = data.readByte() & 0b1111;
        this.type = data.readByte() & 0b11;
        final AEColor oldPaintedColor = this.paintedColor;
        this.paintedColor = AEColor.values()[data.readByte()];
        this.getProxy().setColor(this.paintedColor);

        final int item = data.readInt();

        if (item == 0) {
            this.storageType = null;
        } else {
            this.storageType = new ItemStack(Item.getItemById(item & 0xffff), 1, item >> Platform.DEF_OFFSET);
        }

        return oldPaintedColor != this.paintedColor || this.state != oldState
                || this.type != oldTypes
                || !Platform.isSameItemPrecise(oldType, this.storageType);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileChest(final NBTTagCompound data) {
        this.config.readFromNBT(data);
        this.priority = data.getInteger("priority");
        if (data.hasKey("paintedColor")) {
            this.paintedColor = AEColor.values()[data.getByte("paintedColor")];
            this.getProxy().setColor(this.paintedColor);
        }
        this.typeFilters.readFromNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileChest(final NBTTagCompound data) {
        this.config.writeToNBT(data);
        data.setInteger("priority", this.priority);
        data.setByte("paintedColor", (byte) this.paintedColor.ordinal());
        this.typeFilters.writeToNBT(data);
    }

    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        displayNeedsUpdate = true;
    }

    @MENetworkEventSubscribe
    public void channelRender(final MENetworkChannelsChanged c) {
        displayNeedsUpdate = true;
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.recalculateDisplay();
    }

    @Override
    public IMEMonitor getItemInventory() {
        return this.cellMap.get(ITEM_STACK_TYPE);
    }

    @Override
    public IMEMonitor getFluidInventory() {
        return this.cellMap.get(FLUID_STACK_TYPE);
    }

    @Nullable
    @Override
    public IMEMonitor<?> getMEMonitor(@Nonnull IAEStackType<?> type) {
        return this.cellMap.get(type);
    }

    @Override
    public IInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void setInventorySlotContents(final int i, final ItemStack itemstack) {
        this.inv.setInventorySlotContents(i, itemstack);
        this.tryToStoreContents();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        if (slot == 1) {
            this.cellMap.clear();
            this.isCached = false; // recalculate the storage cell.

            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());

                final IStorageGrid gs = this.getProxy().getStorage();
                Platform.postChanges(gs, removed, added, this.mySrc);
            } catch (final GridAccessException ignored) {

            }

            // update the neighbors
            if (this.worldObj != null) {
                Platform.notifyBlocksOfNeighbors(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
                this.markForUpdate();
            }
        }
    }

    @Override
    public boolean canInsertItem(final int slotIndex, final ItemStack insertingItem, final int side) {
        if (slotIndex == 1) {
            if (AEApi.instance().registries().cell().getCellInventory(insertingItem, this, ITEM_STACK_TYPE) != null) {
                return true;
            }
            if (AEApi.instance().registries().cell().getCellInventory(insertingItem, this, FLUID_STACK_TYPE) != null) {
                return true;
            }
        } else {
            try {
                final IMEInventory<IAEItemStack> cell = this.getHandler(ITEM_STACK_TYPE);
                final IAEItemStack returns = cell.injectItems(
                        AEApi.instance().storage().createItemStack(this.inv.getStackInSlot(0)),
                        Actionable.SIMULATE,
                        this.mySrc);
                return returns == null || returns.getStackSize() != insertingItem.stackSize;
            } catch (final ChestNoHandler ignored) {}
        }
        return false;
    }

    @Override
    public boolean canExtractItem(final int slotIndex, final ItemStack extractedItem, final int side) {
        return slotIndex == 1;
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        if (ForgeDirection.SOUTH == side) {
            return FRONT;
        }

        if (this.isPowered()) {
            try {
                if (this.getHandler(ITEM_STACK_TYPE) != null) {
                    return SIDES;
                }
            } catch (final ChestNoHandler e) {
                // nope!
            }
        }
        return NO_SLOTS;
    }

    private void tryToStoreContents() {
        try {
            if (this.getStackInSlot(0) != null) {
                final IMEInventory<IAEItemStack> cell = this.getHandler(ITEM_STACK_TYPE);

                final IAEItemStack returns = Platform.poweredInsert(
                        this,
                        cell,
                        AEApi.instance().storage().createItemStack(this.inv.getStackInSlot(0)),
                        this.mySrc);

                if (returns == null) {
                    this.inv.setInventorySlotContents(0, null);
                } else {
                    this.inv.setInventorySlotContents(0, returns.getItemStack());
                }
            }
        } catch (final ChestNoHandler ignored) {}
    }

    @Override
    @Nonnull
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        if (this.getProxy().isActive()) {
            try {
                return Collections.singletonList(this.getHandler(type));
            } catch (final ChestNoHandler e) {
                // :P
            }
        }
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;

        this.cellMap.clear();
        this.isCached = false; // recalculate the storage cell.

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public int fill(final ForgeDirection from, final FluidStack resource, final boolean doFill) {
        final double req = resource.amount / 500.0;
        final double available = this.extractAEPower(req, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (available >= req - 0.01) {
            try {
                final IMEInventoryHandler h = this.getHandler(FLUID_STACK_TYPE);

                this.extractAEPower(req, Actionable.MODULATE, PowerMultiplier.CONFIG);
                final IAEStack results = h.injectItems(
                        AEFluidStack.create(resource),
                        doFill ? Actionable.MODULATE : Actionable.SIMULATE,
                        this.mySrc);

                if (results == null) {
                    return resource.amount;
                }

                return resource.amount - (int) results.getStackSize();
            } catch (final ChestNoHandler ignored) {}
        }
        return 0;
    }

    @Override
    public FluidStack drain(final ForgeDirection from, final FluidStack resource, final boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(final ForgeDirection from, final int maxDrain, final boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(final ForgeDirection from, final Fluid fluid) {
        try {
            final IMEInventoryHandler h = this.getHandler(FLUID_STACK_TYPE);
            return h.canAccept(AEFluidStack.create(new FluidStack(fluid, 1)));
        } catch (final ChestNoHandler ignored) {}
        return false;
    }

    @Override
    public boolean canDrain(final ForgeDirection from, final Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(final ForgeDirection from) {
        try {
            final IMEInventoryHandler h = this.getHandler(FLUID_STACK_TYPE);
            if (h.getStackType() == FLUID_STACK_TYPE) {
                return new FluidTankInfo[] { new FluidTankInfo(null, 1) }; // eh?
            }
        } catch (final ChestNoHandler ignored) {}

        return null;
    }

    @Override
    public IStorageMonitorable getMonitorable(final ForgeDirection side, final BaseActionSource src) {
        if (Platform.canAccess(this.getProxy(), src) && side != this.getForward()) {
            return this;
        }
        return null;
    }

    public ItemStack getStorageType() {
        if (this.isPowered()) {
            return this.storageType;
        }
        return null;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.config;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {}

    public boolean openGui(final EntityPlayer p, final ICellHandler ch, final ItemStack cell, final int side) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            try {
                final IMEInventoryHandler invHandler = this.getHandler(type);
                if (ch != null && invHandler != null) {
                    StorageChannel channel = null;
                    if (type == ITEM_STACK_TYPE) {
                        channel = StorageChannel.ITEMS;
                    } else if (type == FLUID_STACK_TYPE) {
                        channel = StorageChannel.FLUIDS;
                    }
                    ch.openChestGui(p, this, ch, invHandler, cell, channel);
                    return true;
                }
            } catch (final ChestNoHandler e) {
                // :P
            }
        }

        return false;
    }

    @Override
    public AEColor getColor() {
        return this.paintedColor;
    }

    @Override
    public boolean recolourBlock(final ForgeDirection side, final AEColor newPaintedColor, final EntityPlayer who) {
        if (this.paintedColor == newPaintedColor) {
            return false;
        }
        this.paintedColor = newPaintedColor;
        this.getProxy().setColor(this.paintedColor);
        if (getGridNode(side) != null) {
            getGridNode(side).updateState();
        }
        this.markDirty();
        this.markForUpdate();
        return true;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
    }

    public boolean toggleItemStorageCellLocking() {
        final ItemStack cell = this.inv.getStackInSlot(1);
        if (ItemBasicStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler)) {
            return false;
        }
        final IMEInventoryHandler<?> inv = cellHandler.getCellInventory(cell, this, ITEM_STACK_TYPE);
        if (inv instanceof ICellInventoryHandler handler) {
            if (ItemBasicStorageCell.cellIsPartitioned(handler)) {
                TileDrive.unpartitionStorageCell(handler);
            } else {
                TileDrive.partitionStorageCellToItemsOnCell(handler);
            }
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
            } catch (final GridAccessException ignored) {}
        }
        return true;
    }

    public int applyStickyToItemStorageCells(ItemStack cards) {
        ItemStack cell = this.inv.getStackInSlot(1);
        if (ItemBasicStorageCell.checkInvalidForLockingAndStickyCarding(cell, cellHandler) && cards.stackSize != 0) {
            return 0;
        }
        if (cell.getItem() instanceof ICellWorkbenchItem cellItem) {
            if (TileDrive.applyStickyCardToItemStorageCell(cellHandler, cell, this, cellItem)) {
                if (this.isCached) {
                    this.isCached = false;
                }
                try {
                    this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                } catch (final GridAccessException ignored) {}
                return 1;
            }
        }
        return 0;
    }

    @Override
    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getTypeFilter(EntityPlayer player) {
        return this.typeFilters.getFilters(player);
    }

    @Override
    public void saveTypeFilter() {
        this.saveChanges();
    }

    private static class ChestNoHandler extends Exception {

        private static final long serialVersionUID = 7995805326136526631L;
    }

    private class ChestNetNotifier implements IMEMonitorHandlerReceiver<IAEStack<?>> {

        private final IAEStackType<?> type;

        public ChestNetNotifier(final IAEStackType<?> type) {
            this.type = type;
        }

        @Override
        public boolean isValid(final Object verificationToken) {
            return cellMap.get(type) == verificationToken;
        }

        @Override
        public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
                final BaseActionSource source) {
            try {
                if (TileChest.this.getProxy().isActive()) {
                    TileChest.this.getProxy().getStorage()
                            .postAlterationOfStoredItems(this.type, change, TileChest.this.mySrc);
                }
            } catch (final GridAccessException e) {
                // :(
            }

            TileChest.this.displayNeedsUpdate = true;
        }

        @Override
        public void onListUpdate() {
            // not used here
        }
    }

    private class ChestMonitorHandler<T extends IAEStack<?>> extends MEMonitorHandler<T> {

        public ChestMonitorHandler(final IMEInventoryHandler<T> t) {
            super(t);
        }

        private IMEInventoryHandler<T> getInternalHandler() {
            final IMEInventoryHandler<T> h = this.getHandler();
            if (h instanceof MEInventoryHandler) {
                return (IMEInventoryHandler<T>) ((MEInventoryHandler) h).getInternal();
            }
            return this.getHandler();
        }

        @Override
        public T injectItems(final T input, final Actionable mode, final BaseActionSource src) {
            if (src.isPlayer() && !this.securityCheck(((PlayerSource) src).player, SecurityPermissions.INJECT)) {
                return input;
            }
            return super.injectItems(input, mode, src);
        }

        private boolean securityCheck(final EntityPlayer player, final SecurityPermissions requiredPermission) {
            if (TileChest.this.getTile() instanceof IActionHost && requiredPermission != null) {
                final IGridNode gn = ((IActionHost) TileChest.this.getTile()).getActionableNode();
                if (gn != null) {
                    final IGrid g = gn.getGrid();
                    if (g != null) {
                        final boolean requirePower = false;
                        if (requirePower) {
                            final IEnergyGrid eg = g.getCache(IEnergyGrid.class);
                            if (!eg.isNetworkPowered()) {
                                return false;
                            }
                        }

                        final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                        if (sg.hasPermission(player, requiredPermission)) {
                            return true;
                        }
                    }
                }

                return false;
            }
            return true;
        }

        @Override
        public T extractItems(final T request, final Actionable mode, final BaseActionSource src) {
            if (src.isPlayer() && !this.securityCheck(((PlayerSource) src).player, SecurityPermissions.EXTRACT)) {
                return null;
            }
            return super.extractItems(request, mode, src);
        }
    }

    @Override
    public GuiText getName() {
        return GuiText.Chest;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().blocks().chest().maybeStack(1).orNull();
    }
}
