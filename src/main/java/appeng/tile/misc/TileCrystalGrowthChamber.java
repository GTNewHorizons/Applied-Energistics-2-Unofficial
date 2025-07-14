package appeng.tile.misc;

import java.util.List;

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

public class TileCrystalGrowthChamber extends AENetworkPowerTile
        implements IGridTickable, IUpgradeableHost, IConfigManagerHost {

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
        inv.writeToNBT(data, "inscriberInv");
        upgrades.writeToNBT(data, "upgrades");
        settings.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileGrower(final NBTTagCompound data) {
        inv.readFromNBT(data, "inscriberInv");
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
        return new int[27];
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, true, false);
    }

    public boolean simulatePower() {
        try {
            final IEnergyGrid eg = getProxy().getEnergy();
            IEnergySource src = this;

            // Base 1, increase by 1 for each card
            final int speedFactor = 1 + upgrades.getInstalledUpgrades(Upgrades.SPEED);
            final int powerConsumption = 10 * speedFactor;
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
            final int powerConsumption = 10 * speedFactor;
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
                    } else if (chargedCertusQuartz.isSameAs(is)) {
                        int redstonePos = -1;
                        int netherPos = -1;
                        boolean found = false;
                        for (int j = 0; j < inv.getSizeInventory(); j++) {
                            ItemStack isTemp = inv.getStackInSlot(j);
                            if (isTemp != null) {
                                if (isTemp.getItem() == Items.redstone) {
                                    redstonePos = j;
                                } else if (isTemp.getItem() == Items.quartz) {
                                    netherPos = j;
                                }
                            }
                            if (redstonePos > -1 && netherPos > -1) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            int slot = -1;
                            for (int j = 0; j < inv.getSizeInventory(); j++) {
                                ItemStack isTemp = inv.getStackInSlot(j);
                                if (isTemp != null) {
                                    if (fluixCrystal.isSameAs(isTemp) && isTemp.stackSize < 64) {
                                        slot = j;
                                        break;
                                    }
                                } else if (slot < 0) {
                                    slot = j;
                                }
                            }
                            if (slot > -1) {
                                decrStackSize(i, 1);
                                decrStackSize(netherPos, 1);
                                decrStackSize(redstonePos, 1);
                                if (inv.getStackInSlot(slot) != null) {
                                    decrStackSize(slot, -2);
                                } else {
                                    setInventorySlotContents(slot, fluixCrystal.maybeStack(2).get());
                                }
                                hasWork = true;
                            }
                        }
                    }
                }
            }
        } else return TickRateModulation.SLOWER;

        if (hasWork) consumePower();
        updateMeta(hasWork);

        return hasWork ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
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
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {}
}
