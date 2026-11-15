/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IOreFilterable;
import appeng.hooks.TickHandler;
import appeng.me.GridAccessException;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.target.TileTarget;
import appeng.util.target.TileTargetCache;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartSharedItemBus<StackType extends IAEStack<StackType>> extends PartUpgradeable
        implements IGridTickable, IOreFilterable, IIAEStackInventory {

    private final IAEStackInventory config = new IAEStackInventory(this, 9);
    private int cachedAdaptorHash = 0;
    private InventoryAdaptor cachedAdaptor;
    private final TileTargetCache targetCache = new TileTargetCache();
    private @Nullable TileTarget cachedTarget;
    private boolean lastRedstone = false;
    private boolean pendingStateUpdate = false;
    protected String oreFilterString = "";
    protected Predicate<IAEItemStack> filterPredicate = null;
    protected final BaseActionSource mySrc;

    public PartSharedItemBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);

        this.mySrc = new MachineSource(this);
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.updateState();
    }

    @Override
    public void upgradesChanged() {
        if (getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
            oreFilterString = "";
            filterPredicate = null;
        }
        this.updateState();
    }

    @Override
    public void readFromNBT(final net.minecraft.nbt.NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.config.readFromNBT(extra, "config");
        this.oreFilterString = extra.getString("filter");
    }

    @Override
    public void writeToNBT(final net.minecraft.nbt.NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.config.writeToNBT(extra, "config");
        extra.setString("filter", this.oreFilterString);
    }

    @Override
    public void onNeighborChanged() {
        this.targetCache.markStale();
        this.updateState();
        this.scheduleStateUpdate();
        if (this.lastRedstone != this.getHost().hasRedstone(this.getSide())) {
            this.lastRedstone = !this.lastRedstone;
            if (this.lastRedstone && this.getRSMode() == RedstoneMode.SIGNAL_PULSE) {
                this.doBusWork();
            }
        }
    }

    protected int getAdaptorFlags() {
        return InventoryAdaptor.DEFAULT & ~InventoryAdaptor.ALLOW_FLUIDS;
    }

    protected Object getTarget() {
        final TileTarget target = this.resolveTarget();
        if (target == null) return null;
        final int newAdaptorHash = Platform.generateTileHash(target.tile());
        if (this.cachedTarget == target && this.cachedAdaptorHash == newAdaptorHash
                && newAdaptorHash != 0
                && this.cachedAdaptor != null)
            return this.cachedAdaptor;

        this.cachedAdaptor = InventoryAdaptor
                .getAdaptorForTarget(target, this.getSide().getOpposite(), getAdaptorFlags());
        this.cachedAdaptorHash = this.cachedAdaptor == null ? 0 : newAdaptorHash;
        this.cachedTarget = target;
        return this.cachedAdaptor;
    }

    protected int availableSlots() {
        return Math.min(1 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 4, this.config.getSizeInventory());
    }

    public abstract int calculateAmountToSend();

    public IIcon getFaceIcon() {
        return this.getItemStack().getIconIndex();
    }

    /**
     * Checks whether the adjacent tile can be accessed without loading its chunk.
     *
     * @return true if the target is loaded.
     */
    protected boolean canDoBusWork() {
        return this.resolveTarget() != null;
    }

    private @Nullable TileTarget resolveTarget() {
        final boolean wasWaiting = this.targetCache.isWaitingForChunk();
        final TileTarget target = this.targetCache.get(this.getHost().getTile(), this.getSide());
        if (target == null) {
            // Also release a target that went away while sleeping, so that it does not keep its chunk alive.
            this.cachedAdaptor = null;
            this.cachedTarget = null;
            this.cachedAdaptorHash = 0;
            if (wasWaiting && !this.targetCache.isWaitingForChunk()) this.scheduleStateUpdate();
        }
        return target;
    }

    private void updateState() {
        if (this.getGridNode() == null) return;
        try {
            if (!this.isSleeping()) {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } else {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void scheduleStateUpdate() {
        if (this.pendingStateUpdate) return;

        final TileEntity self = this.getHost().getTile();
        final World world = self.getWorldObj();

        if (world == null || world.isRemote) return;

        // Give newly placed neighbors one tick to finish initialization before deciding to sleep.
        this.pendingStateUpdate = true;
        TickHandler.INSTANCE.addCallable(world, w -> {
            this.pendingStateUpdate = false;
            if (this.getProxy().getNode() != null) this.updateState();
            return true;
        });
    }

    protected TileEntity getTileEntity(final TileEntity self, final int x, final int y, final int z) {
        final World w = self.getWorldObj();
        return Platform.getLoadedTileEntity(w, x, y, z);
    }

    protected abstract TickRateModulation doBusWork();

    @Override
    public String getFilter() {
        return oreFilterString;
    }

    @Override
    public void setFilter(String filter) {
        oreFilterString = filter;
        filterPredicate = null;
    }

    @Override
    protected boolean isSleeping() {
        // Nothing reports chunk loads to the bus, so it keeps polling while the target's chunk is not loaded.
        return (this.getTarget() == null && !this.targetCache.isWaitingForChunk()) || super.isSleeping();
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.canDoBusWork() && this.targetCache.isWaitingForChunk()) return TickRateModulation.SLOWER;
        return this.doBusWork();
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        if (!player.isSneaking()) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), GuiBridge.GUI_BUS);
            return true;
        }

        return false;
    }

    @Override
    public RedstoneMode getRSMode() {
        return (RedstoneMode) this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 5;
    }

    @Override
    public void saveAEStackInv() {}

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }

    public abstract IAEStackType<StackType> getStackType();

    protected abstract IMEMonitor<StackType> getMonitor();

    protected boolean supportFuzzy() {
        return false;
    }

    protected boolean supportOreDict() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public String getBusName() {
        return this.getItemStack().getDisplayName();
    }
}
