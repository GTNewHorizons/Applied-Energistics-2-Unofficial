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
import appeng.api.storage.StorageChannel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IOreFilterable;
import appeng.me.GridAccessException;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

public abstract class PartSharedItemBus<StackType extends IAEStack<StackType>> extends PartUpgradeable
        implements IGridTickable, IOreFilterable, IIAEStackInventory {

    private final IAEStackInventory config = new IAEStackInventory(this, 9);
    private int adaptorHash = 0;
    private InventoryAdaptor adaptor;
    private boolean lastRedstone = false;
    protected String oreFilterString = "";
    protected Predicate<IAEItemStack> filterPredicate = null;
    protected final BaseActionSource mySrc;
    private final StorageChannel channel;

    public PartSharedItemBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);

        this.mySrc = new MachineSource(this);
        this.channel = StorageChannel.getStorageChannelByParametrizedClass(this.getClass());
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
        this.updateState();
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

    protected InventoryAdaptor getHandler() {
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = this.getTileEntity(
                self,
                self.xCoord + this.getSide().offsetX,
                self.yCoord + this.getSide().offsetY,
                self.zCoord + this.getSide().offsetZ);

        final int newAdaptorHash = Platform.generateTileHash(target);

        if (this.adaptorHash == newAdaptorHash && newAdaptorHash != 0) {
            return this.adaptor;
        }

        this.adaptorHash = newAdaptorHash;
        // noinspection MagicConstant
        this.adaptor = InventoryAdaptor.getAdaptor(target, this.getSide().getOpposite(), getAdaptorFlags());

        return this.adaptor;
    }

    protected int availableSlots() {
        return Math.min(1 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 4, this.config.getSizeInventory());
    }

    public abstract int calculateAmountToSend();

    public IIcon getFaceIcon() {
        return this.getItemStack().getIconIndex();
    }

    /**
     * Checks if the bus can actually do something.
     * <p>
     * Currently this tests if the chunk for the target is actually loaded.
     *
     * @return true, if the the bus should do its work.
     */
    protected boolean canDoBusWork() {
        final TileEntity self = this.getHost().getTile();
        final World world = self.getWorldObj();
        final int xCoordinate = self.xCoord + this.getSide().offsetX;
        final int zCoordinate = self.zCoord + this.getSide().offsetZ;

        return world != null && world.getChunkProvider().chunkExists(xCoordinate >> 4, zCoordinate >> 4);
    }

    private void updateState() {
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

    private TileEntity getTileEntity(final TileEntity self, final int x, final int y, final int z) {
        final World w = self.getWorldObj();

        if (w.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
            return w.getTileEntity(x, y, z);
        }

        return null;
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
        return this.getHandler() == null || super.isSleeping();
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
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
        return this.config;
    }

    public StorageChannel getStorageChannel() {
        return this.channel;
    }

    protected abstract IMEMonitor<StackType> getMonitor();

    protected boolean supportFuzzy() {
        return false;
    }

    protected boolean supportOreDict() {
        return false;
    }
}
