package appeng.tile.powersink;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.integration.IntegrationType;
import appeng.transformer.annotations.Integration;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

@Integration.Interface(iname = IntegrationType.GT, iface = "gregtech.api.interfaces.tileentity.IEnergyConnected")
public abstract class GTPowerSink extends AERootPoweredTile implements IEnergyConnected {

    @Override
    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        double e = PowerUnits.EU.convertTo(PowerUnits.AE, voltage * amperage);
        double overflow = this.funnelPowerIntoStorage(e, Actionable.SIMULATE);
        // Energy grid may keep some "extra energy" that it is happy to get rid of
        // so overflow may actually be greater than input
        if (overflow >= e) return 0;
        long used = amperage - (int) Math.ceil(PowerUnits.AE.convertTo(PowerUnits.EU, overflow) / voltage);
        if (used > 0) {
            e = PowerUnits.EU.convertTo(PowerUnits.AE, voltage * used);
            this.funnelPowerIntoStorage(e, Actionable.MODULATE);
        } else if (used < 0) {
            used = 0;
        }
        return used;
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        return true;
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(byte b) {
        return -1;
    }
}
