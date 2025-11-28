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

import static appeng.util.FluidUtils.clearFluidContainer;
import static appeng.util.FluidUtils.fillFluidContainer;
import static appeng.util.FluidUtils.getFilledFluidContainer;
import static appeng.util.FluidUtils.getFluidContainerCapacity;
import static appeng.util.FluidUtils.getFluidFromContainer;
import static appeng.util.FluidUtils.isFilledFluidContainer;
import static appeng.util.FluidUtils.isFluidContainer;
import static appeng.util.IterationCounter.fetchNewId;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.config.PinsState;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPinsHandler;
import appeng.helpers.MonitorableAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinsHandler;
import appeng.items.storage.ItemViewCell;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;

public class ContainerMEMonitorable extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver<IAEStack<?>>, IPinsHandler {

    private final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];
    private final IMEMonitor<IAEItemStack> monitorItems;
    private final IMEMonitor<IAEFluidStack> monitorFluids;
    private final IItemList<IAEItemStack> items = AEApi.instance().storage().createItemList();
    private final IItemList<IAEFluidStack> fluids = AEApi.instance().storage().createFluidList();
    private final IConfigManager clientCM;
    private final ITerminalHost host;

    private PinsHandler pinsHandler = null;

    @GuiSync(99)
    public boolean canAccessViewCells = false;

    @GuiSync(98)
    public boolean hasPower = false;

    private IConfigManagerHost gui;
    private IConfigManager serverCM;
    private IGridNode networkNode;
    protected SlotRestrictedInput patternRefiller = null;

    public ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable) {
        this(ip, monitorable, true);
    }

    protected ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable,
            final boolean bindInventory) {
        super(ip, monitorable);

        this.host = monitorable;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.TYPE_FILTER, TypeFilter.ALL);
        this.clientCM.registerSetting(Settings.PINS_STATE, PinsState.DISABLED); // use for GUI

        if (Platform.isServer()) {
            if (monitorable instanceof ITerminalPins t) {
                this.pinsHandler = t.getPinsHandler(ip.player);
            }

            this.serverCM = monitorable.getConfigManager();

            this.monitorItems = monitorable.getItemInventory();
            if (monitorItems != null) {
                this.monitorItems.addListener(this, null);
                this.setCellInventory(this.monitorItems);
            }

            this.monitorFluids = monitorable.getFluidInventory();
            if (monitorFluids != null) {
                this.monitorFluids.addListener(this, null);
                this.setCellFluidInventory(this.monitorFluids);
            }

            if (this.monitorItems == null && this.monitorFluids == null) {
                this.setValidContainer(false);
                return;
            }

            if (monitorable instanceof IGridHost igh) {
                this.networkNode = igh.getGridNode(ForgeDirection.UNKNOWN);
            }

            if (monitorable instanceof IPortableCell pc) {
                this.setPowerSource(pc);
            } else if (monitorable instanceof IMEChest imc) {
                this.setPowerSource(imc);
            } else if (this.networkNode != null) {
                final IGrid g = this.networkNode.getGrid();
                if (g != null) {
                    this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                }
            }
        } else {
            this.monitorItems = null;
            this.monitorFluids = null;
        }

        this.canAccessViewCells = false;
        if (monitorable instanceof IViewCellStorage vcs) {
            for (int y = 0; y < 5; y++) {
                this.cellView[y] = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.VIEW_CELL,
                        vcs.getViewCellStorage(),
                        y,
                        206,
                        y * 18 + 8,
                        this.getInventoryPlayer());
                this.cellView[y].setAllowEdit(this.canAccessViewCells);
                this.addSlotToContainer(this.cellView[y]);
            }
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 0);
        }
    }

    public IGridNode getNetworkNode() {
        return this.networkNode;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            if (this.monitorItems != this.host.getItemInventory()
                    || this.monitorFluids != this.host.getFluidInventory()) {
                this.setValidContainer(false);
            }

            for (final Settings set : this.serverCM.getSettings()) {
                final Enum<?> sideLocal = this.serverCM.getSetting(set);
                final Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    this.clientCM.putSetting(set, sideLocal);
                    for (final Object crafter : this.crafters) {
                        try {
                            NetworkHandler.instance.sendTo(
                                    new PacketValueConfig(set.name(), sideLocal.name()),
                                    (EntityPlayerMP) crafter);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }

            if (pinsHandler != null) {
                if (clientCM.getSetting(Settings.PINS_STATE) != pinsHandler.getPinsState()) {
                    this.clientCM.putSetting(Settings.PINS_STATE, pinsHandler.getPinsState());
                    updatePins(true);
                } else {
                    updatePins(false);
                }
            }

            try {
                final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                if (this.monitorItems != null) updateList(piu, this.monitorItems.getStorageList(), this.items);

                if (this.monitorFluids != null) updateList(piu, this.monitorFluids.getStorageList(), this.fluids);

                if (!piu.isEmpty()) {
                    this.items.resetStatus();
                    this.fluids.resetStatus();

                    for (final Object c : this.crafters) {
                        if (c instanceof EntityPlayer) {
                            NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);
                        }
                    }
                }
            } catch (Exception ignored) {}

            this.updatePowerStatus();

            final boolean oldAccessible = this.canAccessViewCells;
            this.canAccessViewCells = this.host instanceof WirelessTerminalGuiObject
                    || this.hasAccess(SecurityPermissions.BUILD, false);
            if (this.canAccessViewCells != oldAccessible) {
                for (int y = 0; y < 5; y++) {
                    if (this.cellView[y] != null) {
                        this.cellView[y].setAllowEdit(this.canAccessViewCells);
                    }
                }
            }

            super.detectAndSendChanges();
        }
    }

    private void updateList(PacketMEInventoryUpdate piu, IItemList monitorCache, IItemList<?> list) {
        if (!list.isEmpty()) {
            try {
                for (final IAEStack<?> aes : list) {
                    final IAEStack<?> send = monitorCache.findPrecise(aes);
                    if (send == null) {
                        aes.setStackSize(0);
                        piu.appendItem(aes);
                    } else {
                        piu.appendItem(send);
                    }
                }
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    protected void updatePowerStatus() {
        try {
            if (this.networkNode != null) {
                this.setPowered(this.networkNode.isActive());
            } else if (this.getPowerSource() instanceof IEnergyGrid) {
                this.setPowered(((IEnergyGrid) this.getPowerSource()).isNetworkPowered());
            } else {
                this.setPowered(
                        this.getPowerSource().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.8);
            }
        } catch (final Throwable t) {
            // :P
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("canAccessViewCells")) {
            for (int y = 0; y < 5; y++) {
                if (this.cellView[y] != null) {
                    this.cellView[y].setAllowEdit(this.canAccessViewCells);
                }
            }
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public void addCraftingToCrafters(final ICrafting c) {
        super.addCraftingToCrafters(c);
        this.queueInventory(c);
    }

    private void queueInventory(final ICrafting c) {
        if (Platform.isServer() && c instanceof EntityPlayerMP pmp) {
            try {
                PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                if (this.monitorItems != null) queueInventoryList(piu, this.monitorItems.getStorageList(), pmp);

                if (this.monitorFluids != null) queueInventoryList(piu, this.monitorFluids.getStorageList(), pmp);

                NetworkHandler.instance.sendTo(piu, pmp);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    private void queueInventoryList(PacketMEInventoryUpdate piu, IItemList monitorCache, EntityPlayerMP c) {
        try {
            for (final IAEStack<?> send : (IItemList<?>) monitorCache) {
                try {
                    piu.appendItem(send);
                } catch (final BufferOverflowException boe) {
                    NetworkHandler.instance.sendTo(piu, c);

                    piu = new PacketMEInventoryUpdate();
                    piu.appendItem(send);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);

        if (this.crafters.isEmpty() && this.monitorItems != null) {
            this.monitorItems.removeListener(this);
        }
        if (this.crafters.isEmpty() && this.monitorFluids != null) {
            this.monitorFluids.removeListener(this);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.monitorItems != null) {
            this.monitorItems.removeListener(this);
        }
        if (this.monitorFluids != null) {
            this.monitorFluids.removeListener(this);
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(IBaseMonitor<IAEStack<?>> monitor, Iterable<IAEStack<?>> change,
            BaseActionSource actionSource) {
        for (final IAEStack<?> aes : change) {
            if (aes instanceof IAEItemStack ais) {
                this.items.add(ais);
            } else if (aes instanceof IAEFluidStack afs) {
                this.fluids.add(afs);
            }
        }
    }

    @Override
    public void onListUpdate() {
        for (final Object c : this.crafters) {
            if (c instanceof ICrafting cr) {
                this.queueInventory(cr);
            }
        }
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    public ItemStack[] getViewCells() {
        final ItemStack[] list = new ItemStack[this.cellView.length];

        for (int x = 0; x < this.cellView.length; x++) {
            list[x] = this.cellView[x].getStack();
        }

        return list;
    }

    public SlotRestrictedInput getCellViewSlot(final int index) {
        return this.cellView[index];
    }

    public SlotRestrictedInput[] getCellViewSlots() {
        return this.cellView;
    }

    public void toggleViewCell(int slotIdx) {
        if (!this.canAccessViewCells) return;
        Slot slot = getSlot(slotIdx);
        if (!(slot instanceof SlotRestrictedInput)) return;
        ItemStack cellStack = slot.getStack();
        if (cellStack == null) return;
        if (!(cellStack.getItem() instanceof ItemViewCell viewCell)) return;
        viewCell.toggleViewMode(cellStack);
        detectAndSendChanges();
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    private void setPowered(final boolean isPowered) {
        this.hasPower = isPowered;
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    public boolean isAPatternTerminal() {
        return false;
    }

    public IMEMonitor<IAEItemStack> getMonitor() {
        return monitorItems;
    }

    public IMEMonitor<IAEFluidStack> getFluidMonitor() {
        return monitorFluids;
    }

    private int lastUpdate = 0;

    public void updatePins(boolean forceUpdate) {
        if (pinsHandler == null || !(host instanceof ITerminalPins itp)) return;

        boolean isActive = pinsHandler.getPinsState() != PinsState.DISABLED;
        ++lastUpdate;
        if (!forceUpdate && lastUpdate <= 20) return;
        lastUpdate = 0;
        if (isActive) {
            final ICraftingGrid cc = itp.getGrid().getCache(ICraftingGrid.class);
            final ImmutableList<ICraftingCPU> cpuList = cc.getCpus().asList();

            List<IAEStack<?>> craftedItems = new ArrayList<>();

            // fetch the first available crafting output
            for (int i = 0; i < cpuList.size(); i++) {
                ICraftingCPU cpu = cpuList.get(i);
                IAEStack<?> output = cpu.getFinalMultiOutput();
                if (cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER && output != null
                        && cpu.getCurrentJobSource() instanceof PlayerSource src
                        && src.player == pinsHandler.getPlayer()) {
                    if (craftedItems.contains(output)) {
                        continue; // skip if already added
                    }
                    if (cpu.isBusy()) craftedItems.add(0, output.copy());
                    else craftedItems.add(output.copy());
                }
            }

            pinsHandler.addItemsToPins(craftedItems);
        }
        pinsHandler.update(forceUpdate);
        onListUpdate(); // notify the repo that the pins have changed
    }

    @Override
    public void setPin(IAEStack<?> is, int idx) {
        if (pinsHandler == null || !(host instanceof ITerminalPins itp)) return;

        if (is == null) {
            final ICraftingGrid cc = itp.getGrid().getCache(ICraftingGrid.class);
            final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();
            for (ICraftingCPU cpu : cpuSet) {
                IAEStack<?> output = cpu.getFinalMultiOutput();
                if (cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER && output != null
                        && output.isSameType(getPin(idx))) {
                    if (!cpu.isBusy()) {
                        cpu.resetFinalOutput();
                    } else {
                        return;
                    }
                }
            }
        }
        pinsHandler.setPin(idx, is);
        updatePins(true);
    }

    @Override
    public IAEStack<?> getPin(int idx) {
        if (pinsHandler == null) return null;
        return pinsHandler.getPin(idx);
    }

    public void setPinsState(PinsState pinsState) {
        if (pinsHandler == null) return;
        clientCM.putSetting(Settings.PINS_STATE, pinsState);
        pinsHandler.setPinsState(pinsState);
        updatePins(true);
    }

    public PinsState getPinsState() {
        return pinsHandler != null ? pinsHandler.getPinsState() : PinsState.DISABLED;
    }

    public PinsHandler getPinsHandler() {
        return pinsHandler;
    }

    public void doMonitorableAction(MonitorableAction action, int custom, final EntityPlayerMP player) {
        IAEItemStack slotItem = null;
        IAEFluidStack slotFluid = null;
        if (this.getTargetStack() instanceof IAEFluidStack afs) {
            slotFluid = this.getCellFluidInventory().getAvailableItem(afs, fetchNewId());
        } else if (this.getTargetStack() instanceof IAEItemStack ais) {
            slotItem = this.getCellInventory().getAvailableItem(ais, fetchNewId());
        }

        switch (action) {
            case AUTO_CRAFT -> {} // handled in PacketMonitorableAction
            case SET_ITEM_PIN -> {
                ItemStack hand = player.inventory.getItemStack();
                if (hand == null) return;

                if (this.getPin(custom) != null && this.getPin(custom) instanceof IAEItemStack pinStack
                        && hand.isItemEqual(pinStack.getItemStack())) {
                    // put item in the terminal
                    doMonitorableAction(MonitorableAction.PICKUP_OR_SET_DOWN, this.inventorySlots.size(), player);
                } else {
                    this.setPin(AEItemStack.create(hand), custom);
                }
            }
            case SET_CONTAINER_PIN -> {
                ItemStack hand = player.inventory.getItemStack();
                if (!isFilledFluidContainer(hand)) return;
                FluidStack fluid = getFluidFromContainer(hand);
                if (fluid == null) return;

                if (this.getPin(custom) != null && this.getPin(custom) instanceof IAEFluidStack pinStack
                        && fluid.isFluidEqual(pinStack.getFluidStack())) {
                    // put item in the terminal
                    doMonitorableAction(MonitorableAction.FILL_SINGLE_CONTAINER, this.inventorySlots.size(), player);
                } else {
                    this.setPin(AEFluidStack.create(fluid), custom);
                }
            }
            case UNSET_PIN -> {
                this.setPin(null, custom);
            }
            case SHIFT_CLICK -> { // shift left click
                if (this.getPowerSource() == null || this.getCellInventory() == null || slotItem == null) {
                    return;
                }

                IAEItemStack ais = slotItem.copy();
                ItemStack myItem = ais.getItemStack();
                ais.setStackSize(myItem.getMaxStackSize());
                myItem.stackSize = (int) ais.getStackSize();

                final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                myItem = adaptor.simulateAdd(myItem);

                if (myItem != null) {
                    ais.setStackSize(ais.getStackSize() - myItem.stackSize);
                }

                ais = Platform
                        .poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
                if (ais != null) {
                    adaptor.addItems(ais.getItemStack());
                }
            }
            case PICKUP_SINGLE, ROLL_UP -> { // shift right click, roll up
                if (this.getPowerSource() == null || this.getCellInventory() == null || slotItem == null) {
                    return;
                }

                final ItemStack hand = player.inventory.getItemStack();

                if (hand != null) {
                    if (hand.stackSize >= hand.getMaxStackSize()) return;
                    if (!Platform.isSameItemPrecise(slotItem.getItemStack(), hand)) return;
                }

                IAEItemStack ais = slotItem.copy();
                ais.setStackSize(1);
                ais = Platform
                        .poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
                if (ais != null) {
                    final InventoryAdaptor ia = new AdaptorPlayerHand(player);

                    final ItemStack fail = ia.addItems(ais.getItemStack());
                    if (fail != null) {
                        this.getCellInventory().injectItems(ais, Actionable.MODULATE, this.getActionSource());
                    }

                    this.updateHeld(player);
                }
            }
            case PICKUP_OR_SET_DOWN -> { // left click
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                ItemStack hand = player.inventory.getItemStack();
                if (hand == null) {
                    if (slotItem == null) return;

                    IAEItemStack ais = slotItem.copy();
                    ais.setStackSize(ais.getItemStack().getMaxStackSize());
                    ais = Platform.poweredExtraction(
                            this.getPowerSource(),
                            this.getCellInventory(),
                            ais,
                            this.getActionSource());
                    if (ais != null) {
                        player.inventory.setItemStack(ais.getItemStack());
                    } else {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                } else {
                    IAEItemStack ais = AEApi.instance().storage().createItemStack(hand);
                    ais = Platform
                            .poweredInsert(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
                    if (ais != null) {
                        player.inventory.setItemStack(ais.getItemStack());
                    } else {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> { // right click
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                ItemStack hand = player.inventory.getItemStack();
                if (hand == null) {
                    if (slotItem == null) return;

                    IAEItemStack ais = slotItem.copy();
                    final long maxSize = ais.getItemStack().getMaxStackSize();
                    ais.setStackSize(maxSize);
                    ais = this.getCellInventory().extractItems(ais, Actionable.SIMULATE, this.getActionSource());

                    if (ais != null) {
                        final long stackSize = Math.min(maxSize, ais.getStackSize());
                        ais.setStackSize((stackSize + 1) >> 1);
                        ais = Platform.poweredExtraction(
                                this.getPowerSource(),
                                this.getCellInventory(),
                                ais,
                                this.getActionSource());
                    }

                    if (ais != null) {
                        player.inventory.setItemStack(ais.getItemStack());
                    } else {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                } else {
                    IAEItemStack ais = AEApi.instance().storage().createItemStack(player.inventory.getItemStack());
                    ais.setStackSize(1);
                    ais = Platform
                            .poweredInsert(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
                    if (ais == null) {
                        final ItemStack is = player.inventory.getItemStack();
                        is.stackSize--;
                        if (is.stackSize <= 0) {
                            player.inventory.setItemStack(null);
                        }
                        this.updateHeld(player);
                    }
                }
            }
            case ROLL_DOWN -> {
                final ItemStack hand = player.inventory.getItemStack();
                if (this.getPowerSource() == null || this.getCellInventory() == null || hand == null) {
                    return;
                }

                IAEItemStack ais = AEItemStack.create(hand);
                ais.setStackSize(1);

                ais = Platform
                        .poweredInsert(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
                if (ais == null) {
                    hand.stackSize--;
                    if (hand.stackSize <= 0) {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                }
            }
            case MOVE_REGION -> {
                if (slotItem == null) return;

                final long maxSize = slotItem.getItemStack().getMaxStackSize();
                final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                while (true) {
                    IAEItemStack ais = slotItem.copy();
                    ais.setStackSize(maxSize);
                    ais = this.getCellInventory().extractItems(ais, Actionable.SIMULATE, this.getActionSource());

                    if (ais == null || ais.getStackSize() <= 0) break;

                    ItemStack myItem = ais.getItemStack();
                    myItem = adaptor.simulateAdd(myItem);

                    if (myItem != null) {
                        if (ais.getStackSize() == myItem.stackSize) break;
                        ais.setStackSize(ais.getStackSize() - myItem.stackSize);
                    }

                    ais = Platform.poweredExtraction(
                            this.getPowerSource(),
                            this.getCellInventory(),
                            ais,
                            this.getActionSource());
                    if (ais == null || ais.getStackSize() <= 0) break;
                    adaptor.addItems(ais.getItemStack());
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.capabilities.isCreativeMode && slotItem != null) {
                    final ItemStack is = slotItem.getItemStack();
                    is.stackSize = is.getMaxStackSize();
                    player.inventory.setItemStack(is);
                    this.updateHeld(player);
                }
            }
            case DRAIN_SINGLE_CONTAINER -> {
                ItemStack hand = player.inventory.getItemStack();
                if (!isFilledFluidContainer(hand)) return;

                if (hand.stackSize == 1) {
                    extractFromSingleFluidContainer(player, hand);
                } else {
                    final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    if (hand.getItem() instanceof IFluidContainerItem container) {
                        ItemStack itemToInject = hand.copy();
                        itemToInject.stackSize = 1;
                        FluidStack fluid = container.getFluid(itemToInject);
                        IAEFluidStack aes = AEFluidStack.create(fluid);
                        IAEFluidStack leftover = this.getCellFluidInventory()
                                .injectItems(aes, Actionable.SIMULATE, this.getActionSource());
                        int injected = leftover == null ? fluid.amount : (int) (fluid.amount - leftover.getStackSize());
                        if (injected == 0) return;

                        container.drain(itemToInject, injected, true);
                        if (adaptor.addItems(itemToInject) != null) return;

                        this.getCellFluidInventory().injectItems(aes, Actionable.MODULATE, this.getActionSource());
                        hand.stackSize--;
                        this.updateHeld(player);
                    } else if (FluidContainerRegistry.isContainer(hand)) {
                        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(hand);
                        IAEFluidStack toInject = AEFluidStack.create(fluid);
                        IAEFluidStack leftover = this.getCellFluidInventory()
                                .injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
                        if (leftover != null) return;
                        ItemStack emptyContainer = FluidContainerRegistry.drainFluidContainer(hand);
                        if (adaptor.addItems(emptyContainer) != null) return;

                        this.getCellFluidInventory().injectItems(toInject, Actionable.MODULATE, this.getActionSource());
                        hand.stackSize--;
                        this.updateHeld(player);
                    }
                }

            }
            case DRAIN_CONTAINERS -> {
                final ItemStack hand = player.inventory.getItemStack();
                if (!isFilledFluidContainer(hand)) return;

                if (hand.stackSize == 1) {
                    extractFromSingleFluidContainer(player, hand);
                } else {
                    FluidStack fluid = getFluidFromContainer(hand);
                    IAEFluidStack toInject = AEFluidStack.create(fluid);
                    long toInjectAmount = (long) fluid.amount * hand.stackSize;
                    toInject.setStackSize(toInjectAmount);
                    IAEFluidStack leftover = this.getCellFluidInventory()
                            .injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
                    long injected = leftover == null ? toInjectAmount : toInjectAmount - leftover.getStackSize();
                    if (injected <= 0) return;

                    ItemStack cleared = hand.copy();
                    cleared.stackSize = 1;
                    cleared = clearFluidContainer(cleared);
                    if (cleared == null) return;

                    if (injected == toInjectAmount && hand.stackSize <= cleared.getMaxStackSize()) {
                        toInject.setStackSize(toInjectAmount);
                        this.getCellFluidInventory().injectItems(toInject, Actionable.MODULATE, this.getActionSource());
                        cleared.stackSize = hand.stackSize;
                        player.inventory.setItemStack(cleared);
                        this.updateHeld(player);
                    } else {
                        int stackSize = hand.stackSize;
                        int fluidAmountPerItem = getFluidFromContainer(hand).amount;
                        if (fluidAmountPerItem <= 0) return;

                        final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                        for (int i = 0; i < stackSize; i++) {
                            toInject.setStackSize(fluidAmountPerItem);
                            leftover = this.getCellFluidInventory()
                                    .injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
                            injected = leftover == null ? fluidAmountPerItem : toInjectAmount - leftover.getStackSize();
                            if (injected != fluidAmountPerItem) break;

                            if (adaptor.addItems(cleared.copy()) != null) break;

                            toInject.setStackSize(fluidAmountPerItem);
                            this.getCellFluidInventory()
                                    .injectItems(toInject, Actionable.MODULATE, this.getActionSource());
                            hand.stackSize--;
                        }
                        this.updateHeld(player);
                    }
                }
            }
            case FILL_SINGLE_CONTAINER -> {
                ItemStack hand = player.inventory.getItemStack();
                if (slotFluid == null || slotFluid.getStackSize() <= 0 || !isFluidContainer(hand)) return;

                if (hand.stackSize == 1) {
                    // Set filled item to player hand
                    fillSingleFluidContainer(player, hand, slotFluid);
                } else {
                    // Insert filled item to player inventory
                    ItemStack itemToFill = hand.copy();
                    itemToFill.stackSize = 1;
                    ObjectIntPair<ItemStack> filledPair = fillFluidContainer(itemToFill, slotFluid.getFluidStack());
                    ItemStack filledContainer = filledPair.left();
                    int filledAmount = filledPair.rightInt();
                    if (filledContainer == null || filledAmount <= 0) return;

                    final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    if (adaptor.addItems(filledContainer) != null) return;

                    IAEFluidStack afs = slotFluid.copy();
                    afs.setStackSize(filledAmount);
                    this.getCellFluidInventory().extractItems(afs, Actionable.MODULATE, this.getActionSource());
                    hand.stackSize--;
                    this.updateHeld(player);
                }
            }
            case FILL_CONTAINERS -> {
                ItemStack hand = player.inventory.getItemStack();
                if (slotFluid == null || slotFluid.getStackSize() <= 0 || !isFluidContainer(hand)) return;

                // Set filled item to player hand
                if (hand.stackSize == 1) {
                    fillSingleFluidContainer(player, hand, slotFluid);
                } else {
                    int capacity = getFluidContainerCapacity(hand, slotFluid.getFluidStack());
                    if (capacity == 0) return;

                    ItemStack filledContainer = getFilledFluidContainer(hand, slotFluid.getFluidStack());
                    if (filledContainer.getMaxStackSize() >= hand.stackSize
                            && (long) capacity * hand.stackSize <= slotFluid.getStackSize()) {
                        // Fill all item in player hand
                        IAEFluidStack toExtract = slotFluid.copy();
                        toExtract.setStackSize((long) capacity * hand.stackSize);
                        this.getCellFluidInventory()
                                .extractItems(toExtract, Actionable.MODULATE, this.getActionSource());
                        filledContainer.stackSize = hand.stackSize;
                        player.inventory.setItemStack(filledContainer);
                        this.updateHeld(player);
                    } else {
                        // Set all filled item to player hand
                        IAEFluidStack afs = slotFluid.copy();
                        final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                        int stackSize = hand.stackSize;
                        for (int i = 0; i < stackSize; i++) {
                            ItemStack itemToFill = hand.copy();
                            itemToFill.stackSize = 1;
                            FluidStack fluidForFill = afs.getFluidStack();
                            ObjectIntPair<ItemStack> filled = fillFluidContainer(itemToFill, fluidForFill);
                            filledContainer = filled.left();
                            int filledAmount = filled.rightInt();
                            if (filledContainer == null || filledAmount <= 0) break;

                            if (adaptor.addItems(filledContainer) != null) break;

                            long amountBeforeExtract = afs.getStackSize();
                            afs.setStackSize(filledAmount);
                            this.getCellFluidInventory().extractItems(afs, Actionable.MODULATE, this.getActionSource());
                            afs.setStackSize(amountBeforeExtract - filledAmount);
                            hand.stackSize--;
                        }
                        this.updateHeld(player);
                    }
                }
            }
        }
    }

    private void fillSingleFluidContainer(EntityPlayerMP player, ItemStack hand, IAEFluidStack slotFluid) {
        ObjectIntPair<ItemStack> filledPair = fillFluidContainer(hand, slotFluid.getFluidStack());
        ItemStack filledContainer = filledPair.left();
        int filledAmount = filledPair.rightInt();
        if (filledContainer == null || filledAmount <= 0) return;

        IAEFluidStack afs = slotFluid.copy();
        afs.setStackSize(filledAmount);
        this.getCellFluidInventory().extractItems(afs, Actionable.MODULATE, this.getActionSource());
        player.inventory.setItemStack(filledContainer);
        this.updateHeld(player);
    }

    private void extractFromSingleFluidContainer(EntityPlayerMP player, ItemStack hand) {
        if (hand.getItem() instanceof IFluidContainerItem container) {
            FluidStack fluid = container.getFluid(hand);
            IAEFluidStack leftover = this.getCellFluidInventory()
                    .injectItems(AEFluidStack.create(fluid), Actionable.MODULATE, this.getActionSource());
            int injected = leftover == null ? fluid.amount : (int) (fluid.amount - leftover.getStackSize());
            if (injected == 0) return;
            container.drain(hand, injected, true);
        } else if (FluidContainerRegistry.isContainer(hand)) {
            FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(hand);
            IAEFluidStack toInject = AEFluidStack.create(fluid);
            IAEFluidStack leftover = this.getCellFluidInventory()
                    .injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
            if (leftover != null) return;
            this.getCellFluidInventory().injectItems(toInject, Actionable.MODULATE, this.getActionSource());
            player.inventory.setItemStack(FluidContainerRegistry.drainFluidContainer(hand));
        }
        this.updateHeld(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int idx) {
        if (Platform.isClient()) {
            return null;
        }

        final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!

        if (clickSlot instanceof SlotDisabled || clickSlot instanceof SlotInaccessible) {
            return null;
        }

        if (clickSlot != null && clickSlot.getHasStack() && clickSlot.isPlayerSide()) {
            ItemStack tis = clickSlot.getStack();
            tis = this.shiftStoreItem(tis);
            clickSlot.putStack(tis);
        }

        return super.transferStackInSlot(p, idx);
    }

    private ItemStack shiftStoreItem(final ItemStack input) {
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            return input;
        }
        final IAEItemStack ais = Platform.poweredInsert(
                this.getPowerSource(),
                this.getCellInventory(),
                AEApi.instance().storage().createItemStack(input),
                this.getActionSource());
        if (ais == null) {
            return null;
        }
        return ais.getItemStack();
    }
}
