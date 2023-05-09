package appeng.parts.layers;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.LayerBase;
import appeng.integration.IntegrationType;
import appeng.parts.p2p.IPartGT5Power;
import appeng.transformer.annotations.Integration;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import ic2.api.energy.tile.IEnergySink;

@Integration.Interface(iname = IntegrationType.GT, iface = "gregtech.api.interfaces.tileentity.IEnergyConnected")
public class LayerIEnergyConnected extends LayerBase implements IEnergyConnected {

    public LayerIEnergyConnected() {}

    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        IPart part = this.getPart(side);
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).injectEnergyUnits(voltage, amperage);
        } else {
            if (part instanceof IEnergySink) {
                TileEntity source = this.getTileEntityAtSide(side);
                if (source != null && ((IEnergySink) part).acceptsEnergyFrom(source, side)) {
                    long rUsedAmperes = 0;
                    while (amperage > rUsedAmperes && ((IEnergySink) part).getDemandedEnergy() > 0.0D
                            && ((IEnergySink) part).injectEnergy(side, (double) voltage, (double) voltage)
                                    < (double) voltage) {
                        ++rUsedAmperes;
                    }
                    return rUsedAmperes;
                }
            }
            return 0L;
        }
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side) {
        IPart part = this.getPart(side);
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).inputEnergy();
        } else if (!(part instanceof IEnergySink)) {
            return false;
        } else {
            TileEntity source = this.getTileEntityAtSide(side);
            return source != null && ((IEnergySink) part).acceptsEnergyFrom(source, side);
        }
    }

    @Override
    public boolean inputEnergyFrom(ForgeDirection side, boolean waitForActive) {
        IPart part = this.getPart(side);
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).inputEnergy();
        } else if (!(part instanceof IEnergySink)) {
            return false;
        } else {
            TileEntity source = this.getTileEntityAtSide(side);
            return source != null && ((IEnergySink) part).acceptsEnergyFrom(source, side);
        }
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection dir) {
        IPart part = this.getPart(dir);
        return part instanceof IPartGT5Power && ((IPartGT5Power) part).outputsEnergy();
    }

    @Override
    public boolean outputsEnergyTo(ForgeDirection dir, boolean waitForActive) {
        IPart part = this.getPart(dir);
        return part instanceof IPartGT5Power && ((IPartGT5Power) part).outputsEnergy();
    }

    @Override
    public byte getColorization() {
        return -1;
    }

    @Override
    public byte setColorization(byte b) {
        return -1;
    }

    private int getOffsetX(ForgeDirection side, int aMultiplier) {
        return this.xCoord + side.offsetX * aMultiplier;
    }

    private short getOffsetY(ForgeDirection side, int aMultiplier) {
        return (short) (this.yCoord + side.offsetY * aMultiplier);
    }

    private int getOffsetZ(ForgeDirection side, int aMultiplier) {
        return this.zCoord + side.offsetZ * aMultiplier;
    }

    private boolean crossedChunkBorder(int aX, int aZ) {
        return aX >> 4 != this.xCoord >> 4 || aZ >> 4 != this.zCoord >> 4;
    }

    private TileEntity getTileEntityAtSide(ForgeDirection side) {
        int tX = this.getOffsetX(side, 1);
        int tY = this.getOffsetY(side, 1);
        int tZ = this.getOffsetZ(side, 1);
        return this.crossedChunkBorder(tX, tZ) && !this.worldObj.blockExists(tX, tY, tZ) ? null
                : this.worldObj.getTileEntity(tX, tY, tZ);
    }
}
