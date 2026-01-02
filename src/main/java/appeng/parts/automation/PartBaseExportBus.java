package appeng.parts.automation;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.common.collect.ImmutableSet;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.SchedulingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.AELog;
import appeng.core.settings.TickRates;
import appeng.helpers.MultiCraftingTracker;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartBaseExportBus<StackType extends IAEStack<StackType>> extends PartSharedItemBus<StackType>
        implements ICraftingRequester {

    private final MultiCraftingTracker craftingTracker = new MultiCraftingTracker(this, 9);
    protected long itemToSend = 1;
    private boolean didSomething = false;
    private int nextSlot = 0;

    @Reflected
    public PartBaseExportBus(final ItemStack is) {
        super(is);

        this.getConfigManager().registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        this.getConfigManager().registerSetting(Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.craftingTracker.readFromNBT(extra);
        this.nextSlot = extra.getInteger("nextSlot");
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.craftingTracker.writeToNBT(extra);
        extra.setInteger("nextSlot", this.nextSlot);
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        this.itemToSend = this.calculateAmountToSend();
        this.didSomething = false;

        try {
            final InventoryAdaptor destination = this.getHandler();
            final IMEMonitor<StackType> gridInv = this.getMonitor();
            final IEnergyGrid energy = this.getProxy().getEnergy();
            final ICraftingGrid cg = this.getProxy().getCrafting();
            final FuzzyMode fuzzyMode = supportFuzzy()
                    ? (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)
                    : null;
            final SchedulingMode schedulingMode = (SchedulingMode) this.getConfigManager()
                    .getSetting(Settings.SCHEDULING_MODE);

            if (destination != null) {
                if (this.getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
                    int x = 0;

                    for (x = 0; x < this.availableSlots() && this.itemToSend > 0; x++) {
                        final int slotToExport = this.getStartingSlot(schedulingMode, x);

                        final StackType aes = (StackType) this.getAEInventoryByName(StorageName.NONE)
                                .getAEStackInSlot(slotToExport);

                        if (aes == null || this.itemToSend <= 0 || this.craftOnly()) {
                            if (this.isCraftingEnabled()) {
                                this.didSomething = this.craftingTracker.handleCrafting(
                                        slotToExport,
                                        this.itemToSend,
                                        aes,
                                        destination,
                                        this.getTile().getWorldObj(),
                                        this.getProxy().getGrid(),
                                        cg,
                                        this.mySrc) || this.didSomething;
                            }
                            continue;
                        }

                        final long before = this.itemToSend;

                        if (supportFuzzy() && this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                            doFuzzy(aes, fuzzyMode, destination, energy, gridInv);
                        } else {
                            this.pushItemIntoTarget(destination, energy, gridInv, aes);
                        }

                        if (this.itemToSend == before && this.isCraftingEnabled()) {
                            this.didSomething = this.craftingTracker.handleCrafting(
                                    slotToExport,
                                    this.itemToSend,
                                    aes,
                                    destination,
                                    this.getTile().getWorldObj(),
                                    this.getProxy().getGrid(),
                                    cg,
                                    this.mySrc) || this.didSomething;
                        }
                    }

                    this.updateSchedulingMode(schedulingMode, x);
                } else if (supportOreDict()) {
                    doOreDict(destination, energy, gridInv);
                }
            } else {
                return TickRateModulation.SLEEP;
            }
        } catch (final GridAccessException e) {
            // :P
        }

        return this.didSomething ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 14);
        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(6, 6, 11, 10, 10, 12);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getFaceIcon(),
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartExportSides.getIcon());

        rh.setBounds(4, 4, 12, 12, 12, 14);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderInventoryBox(renderer);

        rh.setBounds(6, 6, 15, 10, 10, 16);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getFaceIcon(),
                CableBusTextures.PartExportSides.getIcon(),
                CableBusTextures.PartExportSides.getIcon());

        rh.setBounds(4, 4, 12, 12, 12, 14);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(6, 6, 15, 10, 10, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getFaceIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon(),
                CableBusTextures.PartMonitorSidesStatus.getIcon());

        rh.setBounds(6, 6, 11, 10, 10, 12);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false);
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    @Override
    public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items, final Actionable mode) {
        final InventoryAdaptor d = this.getHandler();

        try {
            if (d != null && this.getProxy().isActive()) {
                final IEnergyGrid energy = this.getProxy().getEnergy();
                final double power = (double) items.getStackSize() / items.getPowerMultiplier();

                if (energy.extractAEPower(power, mode, PowerMultiplier.CONFIG) > power - 0.01) {
                    if (mode == Actionable.MODULATE) {
                        return d.addStack(items, InsertionMode.DEFAULT);
                    }
                    return d.simulateAddStack(items, InsertionMode.DEFAULT);
                }
            }
        } catch (final GridAccessException e) {
            AELog.debug(e);
        }

        return items;
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
    }

    private boolean craftOnly() {
        return this.getConfigManager().getSetting(Settings.CRAFT_ONLY) == YesNo.YES;
    }

    private boolean isCraftingEnabled() {
        return this.getInstalledUpgrades(Upgrades.CRAFTING) > 0;
    }

    protected void pushItemIntoTarget(final InventoryAdaptor d, final IEnergyGrid energy,
            final IMEInventory<StackType> inv, StackType aes) {
        final StackType ins = aes.copy();
        ins.setStackSize(this.itemToSend);

        final StackType o = (StackType) d.simulateAddStack(ins, InsertionMode.DEFAULT);
        final long canFit = o == null ? this.itemToSend : this.itemToSend - o.getStackSize();

        if (canFit > 0) {
            aes = aes.copy();
            aes.setStackSize(canFit);
            final StackType itemsToAdd = Platform.poweredExtraction(energy, inv, aes, this.mySrc);

            if (itemsToAdd != null) {
                this.itemToSend -= itemsToAdd.getStackSize();

                final StackType failed = (StackType) d.addStack(itemsToAdd, InsertionMode.DEFAULT);
                if (failed != null) {
                    aes.setStackSize(failed.getStackSize());
                    inv.injectItems(aes, Actionable.MODULATE, this.mySrc);
                } else {
                    this.didSomething = true;
                }
            }
        }
    }

    private int getStartingSlot(final SchedulingMode schedulingMode, final int x) {
        if (schedulingMode == SchedulingMode.RANDOM) {
            return Platform.getRandom().nextInt(this.availableSlots());
        }

        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            return (this.nextSlot + x) % this.availableSlots();
        }

        return x;
    }

    private void updateSchedulingMode(final SchedulingMode schedulingMode, final int x) {
        if (schedulingMode == SchedulingMode.ROUNDROBIN) {
            this.nextSlot = (this.nextSlot + x) % this.availableSlots();
        }
    }

    protected abstract void doFuzzy(StackType aes, FuzzyMode fzMode, InventoryAdaptor destination, IEnergyGrid energy,
            IMEMonitor<StackType> gridInv);

    protected abstract void doOreDict(InventoryAdaptor destination, IEnergyGrid energy, IMEMonitor<StackType> gridInv);
}
