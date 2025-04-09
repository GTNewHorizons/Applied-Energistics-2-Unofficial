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

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.Arrays;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

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
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.ITerminalPins;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPinsHandler;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class ContainerMEMonitorable extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver<IAEItemStack>, IPinsHandler {

    private final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];
    private final IMEMonitor<IAEItemStack> monitor;
    private final IItemList<IAEItemStack> items = AEApi.instance().storage().createItemList();
    private final IConfigManager clientCM;
    private final ITerminalHost host;
    private final IAEItemStack[] serverPins = new IAEItemStack[9];
    private ImmutableList<ICraftingCPU> lastCpuSet = null;
    private int lastUpdate = 0;

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
        super(
                ip,
                monitorable instanceof TileEntity ? (TileEntity) monitorable : null,
                monitorable instanceof IPart ? (IPart) monitorable : null);

        this.host = monitorable;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.TYPE_FILTER, TypeFilter.ALL);

        if (monitorable instanceof ITerminalPins) {
            this.clientCM.registerSetting(Settings.PINS_STATE, PinsState.DISABLED);
        }

        if (Platform.isServer()) {
            this.serverCM = monitorable.getConfigManager();

            this.monitor = monitorable.getItemInventory();
            if (this.monitor != null) {
                this.monitor.addListener(this, null);

                this.setCellInventory(this.monitor);

                if (monitorable instanceof IPortableCell) {
                    this.setPowerSource((IEnergySource) monitorable);
                } else if (monitorable instanceof IMEChest) {
                    this.setPowerSource((IEnergySource) monitorable);
                } else if (monitorable instanceof IGridHost) {
                    final IGridNode node = ((IGridHost) monitorable).getGridNode(ForgeDirection.UNKNOWN);
                    if (node != null) {
                        this.networkNode = node;
                        final IGrid g = node.getGrid();
                        if (g != null) {
                            this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                        }
                    }
                }
            } else {
                this.setValidContainer(false);
            }
        } else {
            this.monitor = null;
        }

        this.canAccessViewCells = false;
        if (monitorable instanceof IViewCellStorage) {
            for (int y = 0; y < 5; y++) {
                this.cellView[y] = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.VIEW_CELL,
                        ((IViewCellStorage) monitorable).getViewCellStorage(),
                        y,
                        206,
                        y * 18 + 8,
                        this.getInventoryPlayer());
                this.cellView[y].setAllowEdit(this.canAccessViewCells);
                this.addSlotToContainer(this.cellView[y]);
            }
        }
        if (isAPatternTerminal()) {
            patternRefiller = new SlotRestrictedInput(
                    PlacableItemType.UPGRADES,
                    ((IUpgradeableHost) monitorable).getInventoryByName("upgrades"),
                    0,
                    206,
                    5 * 18 + 11,
                    this.getInventoryPlayer());
            addSlotToContainer(patternRefiller);
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
            if (this.monitor != this.host.getItemInventory()) {
                this.setValidContainer(false);
            }

            boolean extraordinary = false;

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

                    if (set == Settings.PINS_STATE) {
                        extraordinary = true;
                        lastUpdate = 24;
                    }
                }
            }

            updatePins(extraordinary);

            if (!this.items.isEmpty()) {
                try {
                    final IItemList<IAEItemStack> monitorCache = this.monitor.getStorageList();

                    final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (final IAEItemStack is : this.items) {
                        final IAEItemStack send = monitorCache.findPrecise(is);
                        if (send == null) {
                            is.setStackSize(0);
                            piu.appendItem(is);
                        } else {
                            piu.appendItem(send);
                        }
                    }

                    if (!piu.isEmpty()) {
                        this.items.resetStatus();

                        for (final Object c : this.crafters) {
                            if (c instanceof EntityPlayer) {
                                NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);
                            }
                        }
                    }
                } catch (final IOException e) {
                    AELog.debug(e);
                }
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
        this.queueInventory(c);
    }

    private void queueInventory(final ICrafting c) {
        if (Platform.isServer() && c instanceof EntityPlayer && this.monitor != null) {
            try {
                PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
                final IItemList<IAEItemStack> monitorCache = this.monitor.getStorageList();

                for (final IAEItemStack send : monitorCache) {
                    try {
                        piu.appendItem(send);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);

                        piu = new PacketMEInventoryUpdate();
                        piu.appendItem(send);
                    }
                }

                NetworkHandler.instance.sendTo(piu, (EntityPlayerMP) c);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public void removeCraftingFromCrafters(final ICrafting c) {
        super.removeCraftingFromCrafters(c);

        if (this.crafters.isEmpty() && this.monitor != null) {
            this.monitor.removeListener(this);
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.monitor != null) {
            this.monitor.removeListener(this);
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEItemStack> monitor, final Iterable<IAEItemStack> change,
            final BaseActionSource source) {
        for (final IAEItemStack is : change) {
            this.items.add(is);
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
        return monitor;
    }

    // to avoid duplicating this method in 2 pattern terminals
    protected void refillBlankPatterns(Slot slot) {
        if (Platform.isServer()) {
            ItemStack blanks = slot.getStack();
            int blanksToRefill = 64;
            if (blanks != null) blanksToRefill -= blanks.stackSize;
            if (blanksToRefill <= 0) return;
            final AEItemStack request = AEItemStack
                    .create(AEApi.instance().definitions().materials().blankPattern().maybeStack(blanksToRefill).get());
            final IAEItemStack extracted = Platform
                    .poweredExtraction(this.getPowerSource(), this.getCellInventory(), request, this.getActionSource());
            if (extracted != null) {
                if (blanks != null) blanks.stackSize += extracted.getStackSize();
                else {
                    blanks = extracted.getItemStack();
                }
                slot.putStack(blanks);
            }
        }
    }

    public void updatePins(boolean ext) {
        if (host instanceof ITerminalPins itp) {
            ++lastUpdate;
            AppEngInternalAEInventory api = itp.getPins();
            boolean isActive = serverCM.getSetting(Settings.PINS_STATE) == PinsState.ACTIVE;
            if (ext || isActive) {
                if (isActive) {
                    final ICraftingGrid cc = itp.getGrid().getCache(ICraftingGrid.class);
                    final ImmutableList<ICraftingCPU> cpuSet = cc.getCpus().asList();

                    if (ext || !cpuSet.equals(lastCpuSet) || lastUpdate > 20) {
                        lastUpdate = 0;
                        lastCpuSet = cpuSet;
                        int j = 0;
                        int jj = 0;
                        for (int i = 0; i < api.getSizeInventory(); i++) {
                            IAEItemStack ais = api.getAEStackInSlot(i);
                            if (ais == null) {
                                while (j < cpuSet.size()) {
                                    ICraftingCPU cpu = cpuSet.get(j);
                                    j++;
                                    if (cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER && cpu.isBusy()
                                            && cpu.getFinalOutput() != null) {
                                        ais = cpu.getFinalOutput().copy();
                                        break;
                                    }
                                }
                                if (ais == null) {
                                    while (jj < cpuSet.size()) {
                                        ICraftingCPU cpu = cpuSet.get(jj);
                                        jj++;
                                        if (cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER && !cpu.isBusy()
                                                && cpu.getFinalOutput() != null) {
                                            ais = cpu.getFinalOutput().copy();
                                            break;
                                        }
                                    }
                                }
                            }
                            if (ext || (ais != null && checkPins(ais))) {
                                updatePin(ais, i);
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < api.getSizeInventory(); i++) {
                        updatePin(null, i);
                    }
                }
            }
        }
    }

    private boolean checkPins(IAEItemStack ais) {
        for (IAEItemStack serverPin : serverPins) {
            if (ais.isSameType(serverPin)) return false;
        }
        return true;
    }

    @Override
    public void setPin(ItemStack is, int idx) {
        if (host instanceof ITerminalPins itp) {
            AppEngInternalAEInventory aip = itp.getPins();

            for (int i = 0; i < aip.getSizeInventory(); i++) {
                if (aip.getAEStackInSlot(i) != null && aip.getAEStackInSlot(i).isSameType(is)) {
                    return;
                }
            }

            aip.setInventorySlotContents(idx, is);
            aip.markDirty();

            if (is == null) {
                final ICraftingGrid cc = itp.getGrid().getCache(ICraftingGrid.class);
                final ImmutableSet<ICraftingCPU> cpuSet = cc.getCpus();
                for (ICraftingCPU cpu : cpuSet.asList()) {
                    if (cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER && cpu.getFinalOutput() != null
                            && cpu.getFinalOutput().isSameType(serverPins[idx])) {
                        if (!cpu.isBusy()) {
                            cpu.resetFinalOutput();
                        } else {
                            return;
                        }
                    }
                }
            }
            Arrays.fill(serverPins, null);
            updatePins(true);
        }
    }

    public void updatePin(IAEItemStack is, int idx) {
        serverPins[idx] = is;
        if (is != null) is.setStackSize(0);
        for (final Object player : crafters) {
            if (player instanceof EntityPlayerMP) {
                try {
                    NetworkHandler.instance.sendTo(
                            new PacketInventoryAction(InventoryAction.SET_PIN, idx, is),
                            (EntityPlayerMP) player);
                } catch (IOException e) {
                    AELog.debug(e);
                }
            }
        }
    }
}
