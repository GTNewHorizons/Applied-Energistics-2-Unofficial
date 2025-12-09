/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.misc;

import static appeng.util.Platform.isAE2FCLoaded;
import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.writeStackNBT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.glodblock.github.common.item.ItemFluidPacket;
import com.glodblock.github.inventory.MEMonitorIFluidHandler;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.parts.IStorageBus;
import appeng.api.parts.PartItemStack;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.client.texture.CableBusTextures;
import appeng.core.settings.TickRates;
import appeng.core.stats.Achievements;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.Reflected;
import appeng.integration.IntegrationType;
import appeng.me.GridAccessException;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEMonitorIInventory;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.MEPassThrough;
import appeng.me.storage.StorageBusInventoryHandler;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.IAEStackInventory;
import appeng.transformer.annotations.Integration.Method;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;
import buildcraft.api.transport.IPipeTile.PipeType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PartStorageBus extends PartUpgradeable implements IStorageBus {

    private final BaseActionSource mySrc;
    private final IAEStackInventory Config = new IAEStackInventory(this, 63) {

        @Override
        public void putAEStackInSlot(int n, IAEStack<?> aes) {
            super.putAEStackInSlot(n, aes);

            if (n >= 18 && (n < (18 + getInstalledUpgrades(Upgrades.CAPACITY) * 9))) {
                filterCache[n - 18] = aes;
            }

            resetCache(true);
        }
    };

    private boolean needSyncGUI = false;
    // represents the 45 optional slots unlockable with a Storage Card
    private final IAEStack<?>[] filterCache = new IAEStack<?>[63 - 18];
    private int priority = 0;
    private boolean cached = false;
    private IMEMonitor monitor = null;
    private MEInventoryHandler handler = null;
    private int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    private String oreFilterString = "";
    private String previousOreFilterString = "";

    /**
     * used to read changes once when the list of extractable items was changed
     */
    private boolean readOncePass = false;

    @Reflected
    public PartStorageBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.getConfigManager().registerSetting(Settings.STICKY_MODE, YesNo.NO);
        this.mySrc = new MachineSource(this);
        if (is.getTagCompound() != null) {
            NBTTagCompound tag = is.getTagCompound();
            if (tag.hasKey("priority")) {
                priority = tag.getInteger("priority");
            }
            if (tag.hasKey("config")) {
                Config.readFromNBT(tag, "config");
            }
            if (tag.hasKey("filter")) {
                previousOreFilterString = tag.getString("filter");
            }
            if (tag.hasKey("filterCache")) {
                NBTTagCompound tagCompound = tag.getCompoundTag("filterCache");
                readFilterCache(tagCompound);
            }
            if (tag.hasKey("configManager")) {
                NBTTagCompound configManagerTag = tag.getCompoundTag("configManager");
                final IConfigManager manager = this.getConfigManager();
                // cannot use manager.readFromNBT(configManagerTag) because throws an exception
                for (final Settings setting : manager.getSettings()) {
                    String value = configManagerTag.getString(setting.name());
                    Enum<?> oldValue = manager.getSetting(setting);
                    manager.registerSetting(setting, Enum.valueOf(oldValue.getClass(), value));
                }
            }
            String customName = null; // DisplayName is cached because it is stored on the nbt.
            if (is.hasDisplayName()) customName = is.getDisplayName();
            // if we don't do this, the tag will stick forever to the storage bus, as it's never cleaned up,
            // even when the item is broken with a pickaxe
            this.is.setTagCompound(null);
            if (customName != null) this.setCustomName(customName);
        }
    }

    @Override
    public ItemStack getItemStack(final PartItemStack type) {
        if (type == PartItemStack.Wrench) {
            final NBTTagCompound tag = new NBTTagCompound();

            if (!this.Config.isEmpty()) this.Config.writeToNBT(tag, "config");
            if (this.priority != 0) tag.setInteger("priority", this.priority);
            if (!this.oreFilterString.isEmpty()) tag.setString("filter", this.oreFilterString);

            final NBTTagCompound tagCompound = new NBTTagCompound();
            writeFilterCache(tagCompound);
            tag.setTag("filterCache", tagCompound);

            final NBTTagCompound configManagerTag = new NBTTagCompound();
            this.getConfigManager().writeToNBT(configManagerTag);
            tag.setTag("configManager", configManagerTag);

            final ItemStack copy = this.is.copy();
            copy.setTagCompound(tag);
            if (this.hasCustomName()) copy.setStackDisplayName(this.getCustomName());
            return copy;
        }
        return super.getItemStack(type);
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.updateStatus();
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    private void updateStatus() {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                this.getHost().markForUpdate();
            } catch (final GridAccessException e) {
                // :P
            }
        }
    }

    @MENetworkEventSubscribe
    public void updateChannels(final MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) this.oreFilterString = "";
        else if (this.oreFilterString.isEmpty()) this.oreFilterString = previousOreFilterString;

        for (int x = 0; x < (this.getInstalledUpgrades(Upgrades.CAPACITY) * 9); x++) {
            final IAEStack<?> aes = filterCache[x];
            if (aes != null) this.Config.putAEStackInSlot(x + 18, aes);
        }
        needSyncGUI = true;

        this.resetCache(true);
    }

    @Override
    public boolean needSyncGUI() {
        return this.needSyncGUI;
    }

    @Override
    public void setNeedSyncGUI(boolean needSyncGUI) {
        this.needSyncGUI = needSyncGUI;
    }

    private void readFilterCache(NBTTagCompound tagCompound) {
        for (int x = 0; x < filterCache.length; x++) {
            if (tagCompound.hasKey("#" + x)) {
                NBTTagCompound isTag = tagCompound.getCompoundTag("#" + x);
                filterCache[x] = readStackNBT(isTag, true);
            } else {
                filterCache[x] = null;
            }
        }
    }

    private void writeFilterCache(NBTTagCompound tagCompound) {
        for (int x = 0; x < filterCache.length; x++) {
            if (filterCache[x] != null) {
                NBTTagCompound isTag = new NBTTagCompound();
                writeStackNBT(filterCache[x], isTag, true);
                tagCompound.setTag("#" + x, isTag);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.Config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
        this.oreFilterString = data.getString("filter");
        final NBTTagCompound filterCacheTag = data.getCompoundTag("filterCache");
        if (data.hasKey("customName")) this.setCustomName(data.getString("customName"));
        readFilterCache(filterCacheTag);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.Config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
        data.setString("filter", this.oreFilterString);
        final NBTTagCompound tagCompound = new NBTTagCompound();
        if (this.hasCustomName()) data.setString("customName", this.getCustomName());
        writeFilterCache(tagCompound);
        data.setTag("filterCache", tagCompound);
    }

    private void resetCache(final boolean fullReset) {
        if (this.getHost() == null || this.getHost().getTile() == null
                || this.getHost().getTile().getWorldObj() == null
                || this.getHost().getTile().getWorldObj().isRemote) {
            return;
        }

        if (fullReset) {
            this.resetCacheLogic = 2;
        } else {
            this.resetCacheLogic = 1;
        }

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return this.handler == verificationToken;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEStack<?>> monitor, final Iterable<IAEStack<?>> change,
            final BaseActionSource source) {
        try {
            if (this.getProxy().isActive()) {
                if (!this.readOncePass) {
                    AccessRestriction currentAccess = (AccessRestriction) this.getConfigManager()
                            .getSetting(Settings.ACCESS);
                    if (!currentAccess.hasPermission(AccessRestriction.READ)
                            && !this.getInternalHandler().isVisible()) {
                        return;
                    }
                }
                Iterable<IAEStack<?>> filteredChanges = this.filterChanges(change, this.readOncePass);
                this.readOncePass = false;
                if (filteredChanges == null) return;
                this.getProxy().getStorage()
                        .postAlterationOfStoredItems(getStorageChannel(), filteredChanges, this.mySrc);
            }
        } catch (final GridAccessException e) {
            // :(
        }
    }

    /**
     * Filters the changes to only include items that pass the handlers extract filter. Will return null if none of the
     * changes match the filter.
     */
    @Nullable
    private Iterable<IAEStack<?>> filterChanges(final Iterable<IAEStack<?>> change, final boolean readOncePass) {
        if (readOncePass) {
            return change;
        }

        if (this.handler != null && this.handler.isExtractFilterActive()
                && !this.handler.getExtractPartitionList().isEmpty()) {
            List<IAEStack<?>> filteredChanges = new ArrayList<>();
            Predicate<IAEStack<?>> extractFilterCondition = this.handler.getExtractFilterCondition();
            for (final IAEStack<?> changedItem : change) {
                if (extractFilterCondition.test(changedItem)) {
                    filteredChanges.add(changedItem);
                }
            }
            return filteredChanges.isEmpty() ? null : Collections.unmodifiableList(filteredChanges);
        }
        return change;
    }

    @Override
    public void onListUpdate() {
        // not used here.
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(3, 3, 15, 13, 13, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(2, 2, 14, 14, 14, 15);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 12, 11, 11, 14);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(3, 3, 15, 13, 13, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(2, 2, 14, 14, 14, 15);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartStorageSides.getIcon(),
                CableBusTextures.PartStorageSides.getIcon());

        rh.setBounds(5, 5, 12, 11, 11, 13);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getItemStack().getIconIndex(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public void onNeighborChanged() {
        this.resetCache(false);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 4;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.isSneaking()) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_STORAGEBUS);
            return true;
        }

        return false;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
                TickRates.StorageBus.getMin(),
                TickRates.StorageBus.getMax(),
                this.monitor == null,
                true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.resetCacheLogic != 0) {
            this.resetCache();
        }

        if (this.monitor != null) {
            if (getStorageChannel() == StorageChannel.ITEMS) return ((MEMonitorIInventory) this.monitor).onTick();
            else return ((MEMonitorIFluidHandler) this.monitor).onTick();
        }

        return TickRateModulation.SLEEP;
    }

    private void resetCache() {
        final boolean fullReset = this.resetCacheLogic == 2;
        this.resetCacheLogic = 0;

        final IMEInventory in = this.getInternalHandler();

        IItemList before = getItemList();

        if (in != null) {
            before = in.getAvailableItems(before, IterationCounter.fetchNewId());
        }

        this.cached = false;
        if (fullReset) {
            this.handlerHash = 0;
        }

        final IMEInventory out = this.getInternalHandler();

        if (this.monitor != null) {
            if (getStorageChannel() == StorageChannel.ITEMS) ((MEMonitorIInventory) this.monitor).onTick();
            else((MEMonitorIFluidHandler) this.monitor).onTick();
        }

        IItemList after = getItemList();

        if (out != null) {
            after = out.getAvailableItems(after, IterationCounter.fetchNewId());
        }

        Platform.postListChanges(before, after, this, this.mySrc);
    }

    /**
     *
     * @return Subnet grid if found, null if not connected to a grid different from self
     */
    public IGrid getConnectedGrid() {
        try {
            IGrid currentGrid = this.getProxy().getGrid();
            if (this.getConnectedInventory() instanceof MEPassThrough passThrough) {
                IGrid passThroughGrid = passThrough.getGrid();
                if (!currentGrid.equals(passThroughGrid)) return passThroughGrid;
            }
        } catch (GridAccessException e) {
            return null;
        }
        return null;
    }

    // Looks for interfaces for subnet
    private IMEInventory getConnectedInventory() {
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = self.getWorldObj().getTileEntity(
                self.xCoord + this.getSide().offsetX,
                self.yCoord + this.getSide().offsetY,
                self.zCoord + this.getSide().offsetZ);

        if (target != null) {
            final IExternalStorageHandler esh = AEApi.instance().registries().externalStorage()
                    .getHandler(target, this.getSide().getOpposite(), getStorageChannel(), this.mySrc);
            if (esh != null) {
                return esh.getInventory(target, this.getSide().getOpposite(), getStorageChannel(), this.mySrc);
            }
        }
        return null;
    }

    public MEInventoryHandler getInternalHandler() {
        if (this.cached) {
            return this.handler;
        }

        final boolean wasSleeping = this.monitor == null;

        this.cached = true;
        final TileEntity self = this.getHost().getTile();

        ForgeDirection side = this.getSide();
        final TileEntity target = self.getWorldObj().getTileEntity(self.xCoord + side.offsetX, self.yCoord + side.offsetY, self.zCoord +  side.offsetZ);

        final int newHandlerHash = Platform.generateTileHash(target);

        if (this.handlerHash == newHandlerHash && this.handlerHash != 0) {
            return this.handler;
        }

        this.handlerHash = newHandlerHash;
        this.handler = null;
        this.monitor = null;
        this.readOncePass = true;
        if (target != null) {
            final IExternalStorageHandler esh = AEApi.instance().registries().externalStorage()
                    .getHandler(target, this.getSide().getOpposite(), getStorageChannel(), this.mySrc);
            if (esh != null) {
                final IMEInventory inv = esh
                        .getInventory(target, this.getSide().getOpposite(), getStorageChannel(), this.mySrc);

                if (inv instanceof MEMonitorIInventory h) {
                    h.setMode((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
                    h.setActionSource(new MachineSource(this));
                    this.monitor = h;
                } else if (inv instanceof final MEMonitorIFluidHandler h) {
                    h.setMode((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
                    h.setActionSource(new MachineSource(this));
                    this.monitor = h;
                }

                if (inv instanceof MEMonitorPassThrough<?>h) {
                    h.setMode((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
                }

                if (inv != null) {
                    this.checkInterfaceVsStorageBus(target, this.getSide().getOpposite());

                    this.handler = new StorageBusInventoryHandler<>(inv, getStorageChannel());

                    AccessRestriction currentAccess = (AccessRestriction) this.getConfigManager()
                            .getSetting(Settings.ACCESS);
                    this.handler.setBaseAccess(currentAccess);
                    this.handler.setWhitelist(
                            this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST
                                    : IncludeExclude.WHITELIST);
                    this.handler.setPriority(this.priority);
                    // only READ since READ_WRITE would break compat of existing storage buses
                    // could use a new setting that is applied via button or a card too
                    this.handler.setIsExtractFilterActive(currentAccess == AccessRestriction.READ);

                    if (this.getInstalledUpgrades(Upgrades.STICKY) > 0) {
                        this.handler.setSticky(true);
                    }

                    if (this.oreFilterString.isEmpty()) {
                        final IItemList priorityList = getItemList();

                        final int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
                        for (int x = 0; x < this.Config.getSizeInventory() && x < slotsToUse; x++) {
                            IAEStack<?> is = this.Config.getAEStackInSlot(x);

                            if (getStorageChannel() == StorageChannel.FLUIDS && isAE2FCLoaded && is instanceof IAEItemStack ais && ais.getItem() instanceof ItemFluidPacket) {
                                is = ItemFluidPacket.getFluidAEStack(ais);
                            }
                            if (is != null) {
                                priorityList.add(is);
                            }
                        }

                        if (getStorageChannel() == StorageChannel.ITEMS
                                && this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                            FuzzyPriorityList<IAEItemStack> partitionList = new FuzzyPriorityList<>(
                                    priorityList,
                                    (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE));
                            this.handler.setPartitionList(partitionList);
                            this.handler.setExtractPartitionList(partitionList);
                        } else {
                            PrecisePriorityList partitionList = new PrecisePriorityList<>(priorityList);
                            this.handler.setPartitionList(partitionList);
                            this.handler.setExtractPartitionList(partitionList);
                        }
                    } else {
                        OreFilteredList partitionList = new OreFilteredList(oreFilterString);
                        this.handler.setPartitionList(partitionList);
                        this.handler.setExtractPartitionList(partitionList);
                    }

                    if (inv instanceof IMEMonitor) {
                        ((IBaseMonitor) inv).addListener(this, this.handler);
                    }
                }
            }
        }

        // update sleep state...
        if (wasSleeping != (this.monitor == null)) {
            try {
                final ITickManager tm = this.getProxy().getTick();
                if (this.monitor == null) {
                    tm.sleepDevice(this.getProxy().getNode());
                } else {
                    tm.wakeDevice(this.getProxy().getNode());
                }
            } catch (final GridAccessException e) {
                // :(
            }
        }

        try {
            // force grid to update handlers...
            if (!(new Throwable().getStackTrace()[3].getMethodName().equals("cellUpdate")))
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :3
        }

        return this.handler;
    }

    private void checkInterfaceVsStorageBus(final TileEntity target, final ForgeDirection side) {
        IInterfaceHost achievement = null;

        if (target instanceof IInterfaceHost) {
            achievement = (IInterfaceHost) target;
        }

        if (target instanceof IPartHost) {
            final Object part = ((IPartHost) target).getPart(side);
            if (part instanceof IInterfaceHost) {
                achievement = (IInterfaceHost) part;
            }
        }

        if (achievement != null && achievement.getActionableNode() != null) {
            Platform.addStat(achievement.getActionableNode().getPlayerID(), Achievements.Recursive.getAchievement());
            // Platform.addStat( getActionableNode().getPlayerID(), Achievements.Recursive.getAchievement() );
        }
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        if (channel == getStorageChannel()) {
            final IMEInventoryHandler out = this.getProxy().isActive() ? this.getInternalHandler() : null;
            if (out != null) {
                return Collections.singletonList(out);
            }
        }

        return Arrays.asList(new IMEInventoryHandler[] {});
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.resetCache(true);
    }

    @Override
    @Method(iname = IntegrationType.BuildCraftTransport)
    public ConnectOverride overridePipeConnection(final PipeType type, final ForgeDirection with) {
        return type == PipeType.ITEM && with == this.getSide() ? ConnectOverride.CONNECT : ConnectOverride.DISCONNECT;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // nope!
    }

    @Override
    public String getFilter() {
        return oreFilterString;
    }

    @Override
    public void setFilter(String filter) {
        oreFilterString = filter;
        previousOreFilterString = filter;
        resetCache(true);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().parts().storageBus().maybeStack(1).orNull();
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public void saveAEStackInv() {}

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        return this.Config;
    }
}
