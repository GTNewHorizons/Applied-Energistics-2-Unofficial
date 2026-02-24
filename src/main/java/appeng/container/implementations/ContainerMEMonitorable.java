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

import static appeng.util.FluidUtils.getFluidFromContainer;
import static appeng.util.FluidUtils.isFilledFluidContainer;
import static appeng.util.IterationCounter.fetchNewId;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

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
import appeng.api.storage.ITerminalTypeFilterProvider;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketMonitorableTypeFilter;
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
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class ContainerMEMonitorable extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver<IAEStack<?>>, IPinsHandler {

    private final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];

    private final IMEMonitor<IAEItemStack> itemMonitor;
    private final Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
    private final Map<IAEStackType<?>, Set<IAEStack<?>>> updateQueue = new IdentityHashMap<>();

    private final IConfigManager clientCM;
    private final ITerminalHost host;
    private Reference2BooleanMap<IAEStackType<?>> typeFilters;

    private PinsHandler pinsHandler = null;

    @GuiSync(99)
    public boolean canAccessViewCells = false;

    @GuiSync(98)
    public boolean hasPower = false;

    private IConfigManagerHost gui;
    private IConfigManager serverCM;
    private IGridNode networkNode;

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
        this.clientCM.registerSetting(Settings.PINS_STATE, PinsState.DISABLED); // use for GUI

        if (Platform.isServer()) {
            if (monitorable instanceof ITerminalPins t) {
                this.pinsHandler = t.getPinsHandler(ip.player);
            }

            this.serverCM = monitorable.getConfigManager();

            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> monitor = monitorable.getMEMonitor(type);
                if (monitor != null) {
                    monitor.addListener(this, null);
                    this.monitors.put(type, monitor);
                    this.updateQueue.put(type, new HashSet<>());
                }
            }
            this.itemMonitor = this.getMonitor(ITEM_STACK_TYPE);

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

            if (monitorable instanceof ITerminalTypeFilterProvider provider) {
                this.typeFilters = provider.getTypeFilter(ip.player);
            }
        } else {
            this.itemMonitor = null;
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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                if (this.monitors.get(type) != this.host.getMEMonitor(type)) {
                    this.setValidContainer(false);
                    return;
                }
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

                for (var entry : this.updateQueue.entrySet()) {
                    IItemList list = this.monitors.get(entry.getKey()).getStorageList();
                    for (IAEStack<?> aes : entry.getValue()) {
                        final IAEStack<?> send = list.findPrecise(aes);
                        if (send == null) {
                            aes.setStackSize(0);
                            piu.appendItem(aes);
                        } else {
                            piu.appendItem(send);
                        }
                    }
                }

                if (!piu.isEmpty()) {
                    for (var list : this.updateQueue.values()) {
                        list.clear();
                    }

                    for (final Object c : this.crafters) {
                        if (c instanceof EntityPlayer) {
                            NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);
                        }
                    }
                }
            } catch (final IOException e) {
                AELog.debug(e);
            }

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
        if (Platform.isServer() && c instanceof EntityPlayerMP player) {
            if (this.host instanceof ITerminalTypeFilterProvider provider) {
                this.typeFilters = provider.getTypeFilter(player);
                try {
                    NetworkHandler.instance.sendTo(new PacketMonitorableTypeFilter(this.typeFilters), player);
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }

            this.queueInventory(player);
        }
    }

    private void queueInventory(final EntityPlayerMP player) {
        try {
            PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

            for (var monitor : this.monitors.values()) {
                piu = queueInventoryList(piu, monitor.getStorageList(), player);
            }

            NetworkHandler.instance.sendTo(piu, player);
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private PacketMEInventoryUpdate queueInventoryList(PacketMEInventoryUpdate piu, IItemList monitorCache,
            EntityPlayerMP player) {
        try {
            for (final IAEStack<?> send : (IItemList<?>) monitorCache) {
                try {
                    piu.appendItem(send);
                } catch (final BufferOverflowException boe) {
                    NetworkHandler.instance.sendTo(piu, player);

                    piu = new PacketMEInventoryUpdate();
                    piu.appendItem(send);
                }
            }
        } catch (Exception ignored) {}

        return piu;
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);

        if (this.crafters.isEmpty()) {
            for (IMEMonitor<?> monitor : this.monitors.values()) {
                monitor.removeListener(this);
            }
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);

        for (IMEMonitor<?> monitor : this.monitors.values()) {
            monitor.removeListener(this);
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
            this.updateQueue.get(aes.getStackType()).add(aes);
        }
    }

    @Override
    public void onListUpdate() {
        for (final Object c : this.crafters) {
            if (c instanceof EntityPlayerMP player) {
                this.queueInventory(player);
            }
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes" })
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

    public void updateTypeFilters(Reference2BooleanMap<IAEStackType<?>> map) {
        if (this.host instanceof ITerminalTypeFilterProvider provider) {
            for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : map.reference2BooleanEntrySet()) {
                this.typeFilters.put(entry.getKey(), entry.getBooleanValue());
            }

            provider.saveTypeFilter();
        }
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

    public IMEMonitor<IAEItemStack> getItemMonitor() {
        return this.itemMonitor;
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

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> IMEMonitor<T> getMonitor(IAEStackType<T> type) {
        return (IMEMonitor<T>) this.monitors.get(type);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void doMonitorableAction(MonitorableAction action, int custom, final EntityPlayerMP player) {
        IAEItemStack slotItem = null;
        if (this.itemMonitor != null && this.getTargetStack() instanceof IAEItemStack ais) {
            slotItem = this.itemMonitor.getAvailableItem(ais, fetchNewId());
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
                if (this.getPowerSource() == null || this.itemMonitor == null || slotItem == null) {
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

                ais = Platform.poweredExtraction(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
                if (ais != null) {
                    adaptor.addItems(ais.getItemStack());
                }
            }
            case PICKUP_SINGLE, ROLL_UP -> { // shift right click, roll up
                if (this.getPowerSource() == null || this.itemMonitor == null || slotItem == null) {
                    return;
                }

                final ItemStack hand = player.inventory.getItemStack();

                if (hand != null) {
                    if (hand.stackSize >= hand.getMaxStackSize()) return;
                    if (!Platform.isSameItemPrecise(slotItem.getItemStack(), hand)) return;
                }

                IAEItemStack ais = slotItem.copy();
                ais.setStackSize(1);
                ais = Platform.poweredExtraction(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
                if (ais != null) {
                    final InventoryAdaptor ia = new AdaptorPlayerHand(player);

                    final ItemStack fail = ia.addItems(ais.getItemStack());
                    if (fail != null) {
                        this.itemMonitor.injectItems(ais, Actionable.MODULATE, this.getActionSource());
                    }

                    this.updateHeld(player);
                }
            }
            case PICKUP_OR_SET_DOWN -> { // left click
                if (this.getPowerSource() == null || this.itemMonitor == null) {
                    return;
                }

                ItemStack hand = player.inventory.getItemStack();
                if (hand == null) {
                    if (slotItem == null) return;

                    IAEItemStack ais = slotItem.copy();
                    ais.setStackSize(ais.getItemStack().getMaxStackSize());
                    ais = Platform
                            .poweredExtraction(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
                    if (ais != null) {
                        player.inventory.setItemStack(ais.getItemStack());
                    } else {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                } else {
                    IAEItemStack ais = AEApi.instance().storage().createItemStack(hand);
                    ais = Platform.poweredInsert(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
                    if (ais != null) {
                        player.inventory.setItemStack(ais.getItemStack());
                    } else {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> { // right click
                if (this.getPowerSource() == null || this.itemMonitor == null) {
                    return;
                }

                ItemStack hand = player.inventory.getItemStack();
                if (hand == null) {
                    if (slotItem == null) return;

                    IAEItemStack ais = slotItem.copy();
                    final long maxSize = ais.getItemStack().getMaxStackSize();
                    ais.setStackSize(maxSize);
                    ais = this.itemMonitor.extractItems(ais, Actionable.SIMULATE, this.getActionSource());

                    if (ais != null) {
                        final long stackSize = Math.min(maxSize, ais.getStackSize());
                        ais.setStackSize((stackSize + 1) >> 1);
                        ais = Platform.poweredExtraction(
                                this.getPowerSource(),
                                this.itemMonitor,
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
                    ais = Platform.poweredInsert(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
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
                if (this.getPowerSource() == null || this.itemMonitor == null || hand == null) {
                    return;
                }

                IAEItemStack ais = AEItemStack.create(hand);
                ais.setStackSize(1);

                ais = Platform.poweredInsert(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
                if (ais == null) {
                    hand.stackSize--;
                    if (hand.stackSize <= 0) {
                        player.inventory.setItemStack(null);
                    }
                    this.updateHeld(player);
                }
            }
            case MOVE_REGION -> {
                if (slotItem == null || this.itemMonitor == null) return;

                final long maxSize = slotItem.getItemStack().getMaxStackSize();
                final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                while (true) {
                    IAEItemStack ais = slotItem.copy();
                    ais.setStackSize(maxSize);
                    ais = this.itemMonitor.extractItems(ais, Actionable.SIMULATE, this.getActionSource());

                    if (ais == null || ais.getStackSize() <= 0) break;

                    ItemStack myItem = ais.getItemStack();
                    myItem = adaptor.simulateAdd(myItem);

                    if (myItem != null) {
                        if (ais.getStackSize() == myItem.stackSize) break;
                        ais.setStackSize(ais.getStackSize() - myItem.stackSize);
                    }

                    ais = Platform
                            .poweredExtraction(this.getPowerSource(), this.itemMonitor, ais, this.getActionSource());
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

                final IAEStackType type = getStackTypeForContainer(hand);
                if (type == null) return;

                final IMEMonitor monitor = this.getMonitor(type);
                if (monitor == null) return;

                if (hand.stackSize == 1) {
                    extractFromSingleFluidContainer(player, hand, type, monitor);
                } else {
                    IAEStack<?> stack = type.getStackFromContainerItem(hand);
                    if (stack == null) return;

                    IAEStack<?> leftover = monitor.injectItems(stack, Actionable.SIMULATE, this.getActionSource());
                    if (leftover != null && leftover.getStackSize() == stack.getStackSize()) return;

                    long amountToInject = leftover == null ? stack.getStackSize()
                            : stack.getStackSize() - leftover.getStackSize();

                    ObjectLongPair<ItemStack> simulated = type
                            .drainStackFromContainer(hand.copy(), stack.setStackSize(amountToInject));
                    if (simulated.rightLong() == 0 || simulated.left() == null) return;

                    final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    if (adaptor.addItems(simulated.left()) != null) return;

                    monitor.injectItems(
                            stack.setStackSize(simulated.rightLong()),
                            Actionable.MODULATE,
                            this.getActionSource());
                    hand.stackSize--;
                    this.updateHeld(player);
                }

            }
            case DRAIN_CONTAINERS -> {
                final ItemStack hand = player.inventory.getItemStack();

                final IAEStackType<?> type = getStackTypeForContainer(hand);
                if (type == null) return;

                final IMEMonitor monitor = this.getMonitor(type);
                if (monitor == null) return;

                if (hand.stackSize == 1) {
                    extractFromSingleFluidContainer(player, hand, type, monitor);
                } else {
                    IAEStack<?> toInject = type.getStackFromContainerItem(hand);
                    if (toInject == null) return;

                    final long amountPerItem = toInject.getStackSize();
                    long toInjectAmount = amountPerItem * hand.stackSize;
                    toInject.setStackSize(toInjectAmount);
                    IAEStack<?> leftover = monitor.injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
                    long injected = leftover == null ? toInjectAmount : toInjectAmount - leftover.getStackSize();
                    if (injected <= 0) return;

                    ItemStack cleared = hand.copy();
                    cleared.stackSize = 1;
                    cleared = type.clearFilledContainer(cleared);
                    if (cleared == null) return;

                    // If all stack in hand can be empty
                    if (injected == toInjectAmount && hand.stackSize <= cleared.getMaxStackSize()) {
                        toInject.setStackSize(toInjectAmount);
                        monitor.injectItems(toInject, Actionable.MODULATE, this.getActionSource());
                        cleared.stackSize = hand.stackSize;
                        player.inventory.setItemStack(cleared);
                        this.updateHeld(player);
                    }
                    // If partial stack in hand will be empty
                    else {
                        int stackSize = hand.stackSize;

                        final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                        for (int i = 0; i < stackSize; i++) {
                            toInject.setStackSize(amountPerItem);
                            leftover = monitor.injectItems(toInject, Actionable.SIMULATE, this.getActionSource());
                            injected = leftover == null ? amountPerItem : amountPerItem - leftover.getStackSize();
                            if (injected != amountPerItem) break;

                            if (adaptor.addItems(cleared.copy()) != null) break;

                            toInject.setStackSize(amountPerItem);
                            monitor.injectItems(toInject, Actionable.MODULATE, this.getActionSource());
                            hand.stackSize--;
                        }
                        this.updateHeld(player);
                    }
                }
            }
            case FILL_SINGLE_CONTAINER -> {
                ItemStack hand = player.inventory.getItemStack();
                IAEStack<?> stackInSlot = this.getTargetStack();
                if (hand == null || stackInSlot == null) return;

                IAEStackType type = stackInSlot.getStackType();
                if (!type.isContainerItemForType(hand)) return;

                IMEMonitor monitor = this.getMonitor(type);
                if (monitor == null) return;

                stackInSlot = monitor.getAvailableItem(stackInSlot, IterationCounter.fetchNewId());
                if (stackInSlot == null || stackInSlot.getStackSize() <= 0) return;

                if (hand.stackSize == 1) {
                    // Set filled item to player hand
                    fillSingleFluidContainer(player, hand, stackInSlot, type, monitor);
                } else {
                    // Insert filled item to player inventory
                    ItemStack itemToFill = hand.copy();
                    itemToFill.stackSize = 1;

                    ObjectLongPair<ItemStack> filledPair = type.fillContainer(itemToFill, stackInSlot);
                    ItemStack filledContainer = filledPair.left();
                    long filledAmount = filledPair.rightLong();
                    if (filledContainer == null || filledAmount <= 0) return;

                    final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    if (adaptor.addItems(filledContainer) != null) return;

                    monitor.extractItems(
                            stackInSlot.copy().setStackSize(filledAmount),
                            Actionable.MODULATE,
                            this.getActionSource());
                    hand.stackSize--;
                    this.updateHeld(player);
                }
            }
            case FILL_CONTAINERS -> {
                ItemStack hand = player.inventory.getItemStack();
                IAEStack<?> stackInSlot = this.getTargetStack();
                if (hand == null || stackInSlot == null) return;

                IAEStackType type = stackInSlot.getStackType();
                if (!type.isContainerItemForType(hand)) return;

                IMEMonitor monitor = this.getMonitor(type);
                if (monitor == null) return;

                stackInSlot = monitor.getAvailableItem(stackInSlot, IterationCounter.fetchNewId());
                if (stackInSlot == null || stackInSlot.getStackSize() <= 0) return;

                // Set filled item to player hand
                if (hand.stackSize == 1) {
                    fillSingleFluidContainer(player, hand, stackInSlot, type, monitor);
                } else {
                    long capacity = type.getContainerItemCapacity(hand, stackInSlot);
                    if (capacity <= 0) return;

                    ObjectLongPair<ItemStack> fullFilledPair = type
                            .fillContainer(hand.copy(), stackInSlot.copy().setStackSize(Long.MAX_VALUE));
                    ItemStack fullFilledContainer = fullFilledPair.left();
                    if (fullFilledContainer == null) return;

                    if (fullFilledContainer.getMaxStackSize() >= hand.stackSize
                            && capacity * hand.stackSize <= stackInSlot.getStackSize()) {
                        // Fill all item in player hand
                        monitor.extractItems(
                                stackInSlot.copy().setStackSize(capacity * hand.stackSize),
                                Actionable.MODULATE,
                                this.getActionSource());
                        fullFilledContainer.stackSize = hand.stackSize;
                        player.inventory.setItemStack(fullFilledContainer);
                        this.updateHeld(player);
                    } else {
                        // Set all filled item to player hand
                        IAEStack<?> aes = stackInSlot.copy();
                        final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                        int stackSize = hand.stackSize;
                        for (int i = 0; i < stackSize; i++) {
                            ItemStack itemToFill = hand.copy();
                            itemToFill.stackSize = 1;

                            ObjectLongPair<ItemStack> filledPair = type.fillContainer(itemToFill, aes.copy());
                            ItemStack filledContainer = filledPair.left();
                            long filledAmount = filledPair.rightLong();
                            if (filledContainer == null || filledAmount <= 0) break;

                            if (hand.stackSize - 1 == 0) {
                                player.inventory.setItemStack(filledContainer);
                                break;
                            }

                            if (adaptor.addItems(filledContainer) != null) break;

                            long amountBeforeExtract = aes.getStackSize();
                            monitor.extractItems(
                                    aes.setStackSize(filledAmount),
                                    Actionable.MODULATE,
                                    this.getActionSource());
                            aes.setStackSize(amountBeforeExtract - filledAmount);

                            hand.stackSize--;
                            if (aes.getStackSize() <= 0) break;
                        }
                        this.updateHeld(player);
                    }
                }
            }
        }
    }

    @Nullable
    private IAEStackType<?> getStackTypeForContainer(final ItemStack itemStack) {
        if (itemStack == null) return null;

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (type.isContainerItemForType(itemStack)) {
                return type;
            }
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fillSingleFluidContainer(EntityPlayerMP player, ItemStack hand, IAEStack<?> stack,
            final IAEStackType type, final IMEMonitor monitor) {
        ObjectLongPair<ItemStack> filledPair = type.fillContainer(hand, stack);

        ItemStack filledContainer = filledPair.left();
        long filledAmount = filledPair.rightLong();
        if (filledContainer == null || filledAmount <= 0) return;

        monitor.extractItems(stack.copy().setStackSize(filledAmount), Actionable.MODULATE, this.getActionSource());

        player.inventory.setItemStack(filledContainer);
        this.updateHeld(player);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void extractFromSingleFluidContainer(EntityPlayerMP player, ItemStack hand, final IAEStackType type,
            final IMEMonitor monitor) {
        IAEStack<?> stack = type.getStackFromContainerItem(hand);
        if (stack == null) return;

        IAEStack<?> leftover = monitor.injectItems(stack, Actionable.SIMULATE, this.getActionSource());
        if (leftover != null && leftover.getStackSize() == stack.getStackSize()) return;

        long amountToInject = leftover == null ? stack.getStackSize() : stack.getStackSize() - leftover.getStackSize();

        ObjectLongPair<ItemStack> simulated = type
                .drainStackFromContainer(hand.copy(), stack.setStackSize(amountToInject));
        if (simulated.rightLong() == 0 || simulated.left() == null) return;

        monitor.injectItems(stack.setStackSize(simulated.rightLong()), Actionable.MODULATE, this.getActionSource());
        player.inventory.setItemStack(simulated.left());
        this.updateHeld(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int idx) {
        if (Platform.isClient()) {
            return null;
        }

        final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!

        if (!this.isValidSrcSlotForTransfer(clickSlot)) {
            return null;
        }

        ItemStack result = super.transferStackInSlot(p, idx);

        if (result == null && clickSlot != null && clickSlot.getHasStack() && clickSlot.isPlayerSide()) {
            ItemStack tis = clickSlot.getStack();
            tis = this.shiftStoreItem(tis);
            clickSlot.putStack(tis);
            this.detectAndSendChanges();
        }

        return null;
    }

    private ItemStack shiftStoreItem(final ItemStack input) {
        if (this.getPowerSource() == null) {
            return input;
        }
        final IAEItemStack ais = Platform.poweredInsert(
                this.getPowerSource(),
                this.getMonitor(ITEM_STACK_TYPE),
                AEApi.instance().storage().createItemStack(input),
                this.getActionSource());
        if (ais == null) {
            return null;
        }
        return ais.getItemStack();
    }
}
