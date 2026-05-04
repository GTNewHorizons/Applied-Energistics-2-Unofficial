package appeng.tile.misc;

import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.ITileDefinition;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.items.IGrowableCrystal;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.parts.automation.DefinitionUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.WrapperInventoryRange;

public class TileCrystalGrowthChamber extends AENetworkPowerTile
        implements IGridTickable, IUpgradeableHost, IConfigManagerHost {

    private static final int CYCLE_POWER = 100;

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 27);
    private final UpgradeInventory upgrades;
    private final IConfigManager settings;
    private final IItemDefinition chargedCertusQuartz = AEApi.instance().definitions().materials()
            .certusQuartzCrystalCharged();
    private final IItemDefinition fluixCrystal = AEApi.instance().definitions().materials().fluixCrystal();

    public TileCrystalGrowthChamber() {
        setInternalMaxPower(10000);
        getProxy().setIdlePowerUsage(0);

        final ITileDefinition growerDefinition = AEApi.instance().definitions().blocks().crystalGrowthChamber();
        upgrades = new DefinitionUpgradeInventory(growerDefinition, this, 3);
        settings = new ConfigManager(this);
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileGrower(final NBTTagCompound data) {
        upgrades.writeToNBT(data, "upgrades");
        settings.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileGrower(final NBTTagCompound data) {
        upgrades.readFromNBT(data, "upgrades");
        settings.readFromNBT(data);
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        super.getDrops(w, x, y, z, drops);

        for (int h = 0; h < this.upgrades.getSizeInventory(); h++) {
            final ItemStack is = this.upgrades.getStackInSlot(h);
            if (is != null) {
                drops.add(is);
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack is) {
        return is != null && (is.getItem() instanceof IGrowableCrystal || is.getItem() == Items.quartz
                || is.getItem() == Items.redstone
                || chargedCertusQuartz.isSameAs(is));
    }

    @Override
    public boolean canExtractItem(int i, ItemStack is, int side) {
        return !isItemValidForSlot(i, is);
    }

    @Override
    public IInventory getInternalInventory() {
        return inv;
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        try {
            if (mc != InvOperation.markDirty) {
                markForUpdate();
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public int[] getAccessibleSlotsBySide(ForgeDirection whichSide) {
        return IntStream.range(0, 27).toArray();
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, true, false);
    }

    @Override
    public void gridChanged() {
        try {
            this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
        } catch (final GridAccessException ignored) {}
    }

    public boolean simulatePower() {
        try {
            final IEnergyGrid eg = getProxy().getEnergy();
            IEnergySource src = this;

            // Base 1, increase by 1 for each card
            final int speedFactor = 1 + upgrades.getInstalledUpgrades(Upgrades.SPEED);
            final int powerConsumption = CYCLE_POWER * speedFactor;
            final double powerThreshold = powerConsumption - 0.01;
            double powerReq = extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

            if (powerReq <= powerThreshold) {
                src = eg;
                powerReq = eg.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            }

            if (powerReq > powerThreshold) {
                return true;
            }
        } catch (final GridAccessException e) {
            // :P
        }
        return false;
    }

    public void consumePower() {
        try {
            final IEnergyGrid eg = getProxy().getEnergy();
            IEnergySource src = this;

            // Base 1, increase by 1 for each card
            final int speedFactor = 1 + upgrades.getInstalledUpgrades(Upgrades.SPEED);
            final int powerConsumption = CYCLE_POWER * speedFactor;
            final double powerThreshold = powerConsumption - 0.01;
            double powerReq = extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);

            if (powerReq <= powerThreshold) {
                src = eg;
                powerReq = eg.extractAEPower(powerConsumption, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            }

            if (powerReq > powerThreshold) {
                src.extractAEPower(powerConsumption, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        boolean hasWork = false;
        if (simulatePower()) {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack is = inv.getStackInSlot(i);
                if (is != null) {
                    if (is.getItem() instanceof IGrowableCrystal gc) {
                        ItemStack ns = null;
                        for (int j = 0; j < 1 + getInstalledUpgrades(Upgrades.SPEED); j++) {
                            ns = gc.triggerGrowth(is);
                        }
                        setInventorySlotContents(i, ns);
                        hasWork = true;
                    }
                }
            }

            hasWork |= tryCreateFluix();
        } else return TickRateModulation.SLOWER;

        if (hasWork) consumePower();
        updateMeta(hasWork);

        return hasWork ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
    }

    private boolean tryCreateFluix() {
        int certusPos = -1;
        int redstonePos = -1;
        int netherPos = -1;

        for (int j = 0; j < inv.getSizeInventory(); j++) {
            final ItemStack is = inv.getStackInSlot(j);
            if (is == null) continue;

            if (certusPos < 0 && chargedCertusQuartz.isSameAs(is)) {
                certusPos = j;
            } else if (redstonePos < 0 && is.getItem() == Items.redstone) {
                redstonePos = j;
            } else if (netherPos < 0 && is.getItem() == Items.quartz) {
                netherPos = j;
            }

            if (certusPos > -1 && redstonePos > -1 && netherPos > -1) break;
        }

        if (certusPos < 0 || redstonePos < 0 || netherPos < 0) return false;

        final ItemStack output = fluixCrystal.maybeStack(2).get();
        final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(
                new WrapperInventoryRange(this.inv, 0, this.inv.getSizeInventory(), true),
                ForgeDirection.UNKNOWN);

        if (adaptor.addItems(output) != null) return false;

        decrStackSize(certusPos, 1);
        decrStackSize(netherPos, 1);
        decrStackSize(redstonePos, 1);
        return true;
    }

    private void updateMeta(boolean hasWork) {
        if (hasWork) {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 1, 3);
        } else {
            worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, 0, 3);
        }
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.upgrades.getInstalledUpgrades(u);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("inv")) {
            return this.inv;
        }

        if (name.equals("upgrades")) {
            return this.upgrades;
        }

        return null;
    }

    @Override
    public boolean shouldRefresh(final Block oldBlock, final Block newBlock, final int oldMeta, final int newMeta,
            final World world, final int x, final int y, final int z) {
        return oldBlock != newBlock;
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {}
}
