package appeng.parts.automation;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartBaseImportBus<StackType extends IAEStack<StackType>> extends PartSharedItemBus<StackType> {

    protected IMEInventory<StackType> destination = null;
    protected StackType lastItemChecked = null;
    protected int itemToSend; // used in tickingRequest
    protected boolean worked; // used in tickingRequest

    public PartBaseImportBus(final ItemStack is) {
        super(is);
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 13);
        bch.addBox(5, 5, 13, 11, 11, 14);
        bch.addBox(4, 4, 14, 12, 12, 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getFaceIcon(),
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartImportSides.getIcon());

        rh.setBounds(3, 3, 15, 13, 13, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(4, 4, 14, 12, 12, 15);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartMonitorBack.getIcon(),
                this.getFaceIcon(),
                CableBusTextures.PartImportSides.getIcon(),
                CableBusTextures.PartImportSides.getIcon());

        rh.setBounds(4, 4, 14, 12, 12, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(5, 5, 13, 11, 11, 14);
        rh.renderBlock(x, y, z, renderer);

        rh.setBounds(6, 6, 12, 10, 10, 13);
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
        return new TickingRequest(
                TickRates.ImportBus.getMin(),
                TickRates.ImportBus.getMax(),
                this.getHandler() == null,
                false);
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        this.worked = false;

        final Object myTarget = this.getTarget();
        final FuzzyMode fzMode = (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE);

        if (myTarget != null) {
            try {
                this.itemToSend = this.calculateAmountToSend();
                final double availablePower = this.getProxy().getEnergy().extractAEPower(
                        Platform.ceilDiv(this.itemToSend, getPowerMultiplier()),
                        Actionable.SIMULATE,
                        PowerMultiplier.CONFIG);
                this.itemToSend = Math.min(this.itemToSend, (int) (availablePower * getPowerMultiplier() + 0.01));

                final IMEMonitor<StackType> inv = this.getMonitor();
                final IEnergyGrid energy = this.getProxy().getEnergy();

                boolean configured = false;
                if (this.getInstalledUpgrades(Upgrades.ORE_FILTER) == 0) {
                    for (int x = 0; x < this.availableSlots(); x++) {
                        final StackType ais = (StackType) this.getAEInventoryByName(StorageName.NONE)
                                .getAEStackInSlot(x);
                        if (ais != null && this.itemToSend > 0) {
                            configured = true;
                            while (this.itemToSend > 0) {
                                if (this.importStuff(myTarget, ais, inv, energy, fzMode)) {
                                    break;
                                }
                            }
                        }
                    }
                } else if (supportOreDict()) configured = doOreDict(myTarget, inv, energy, fzMode);

                if (!configured) {
                    while (this.itemToSend > 0) {
                        if (this.importStuff(myTarget, null, inv, energy, fzMode)) {
                            break;
                        }
                    }
                }
            } catch (final GridAccessException e) {
                // :3
            }
        } else {
            return TickRateModulation.SLEEP;
        }

        return this.worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    protected abstract Object getTarget();

    protected abstract boolean importStuff(final Object myTarget, final StackType whatToImport,
            final IMEMonitor<StackType> inv, final IEnergySource energy, final FuzzyMode fzMode);

    protected abstract boolean doOreDict(final Object myTarget, IMEMonitor<StackType> inv, final IEnergyGrid energy,
            final FuzzyMode fzMode);

    protected abstract int getPowerMultiplier();
}
