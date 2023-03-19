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

    public long injectEnergyUnits(byte side, long voltage, long amperage) {
        IPart part = this.getPart(ForgeDirection.getOrientation(side));
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).injectEnergyUnits(voltage, amperage);
        } else {
            ForgeDirection dir = ForgeDirection.getOrientation(side);
            if (part instanceof IEnergySink) {
                TileEntity source = this.getTileEntityAtSide(side);
                if (source != null && ((IEnergySink) part).acceptsEnergyFrom(source, dir)) {
                    long rUsedAmperes = 0;
                    while (amperage > rUsedAmperes && ((IEnergySink) part).getDemandedEnergy() > 0.0D
                            && ((IEnergySink) part).injectEnergy(dir, (double) voltage, (double) voltage)
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
    public boolean inputEnergyFrom(byte side) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        IPart part = this.getPart(dir);
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).inputEnergy();
        } else if (!(part instanceof IEnergySink)) {
            return false;
        } else {
            TileEntity source = this.getTileEntityAtSide(side);
            return source != null && ((IEnergySink) part).acceptsEnergyFrom(source, dir);
        }
    }

    @Override
    public boolean inputEnergyFrom(byte side, boolean q) {
        ForgeDirection dir = ForgeDirection.getOrientation(side);
        IPart part = this.getPart(dir);
        if (part instanceof IPartGT5Power) {
            return ((IPartGT5Power) part).inputEnergy();
        } else if (!(part instanceof IEnergySink)) {
            return false;
        } else {
            TileEntity source = this.getTileEntityAtSide(side);
            return source != null && ((IEnergySink) part).acceptsEnergyFrom(source, dir);
        }
    }

    @Override
    public boolean outputsEnergyTo(byte side) {
        IPart part = this.getPart(ForgeDirection.getOrientation(side));
        return part instanceof IPartGT5Power && ((IPartGT5Power) part).outputsEnergy();
    }

    @Override
    public boolean outputsEnergyTo(byte side, boolean q) {
        IPart part = this.getPart(ForgeDirection.getOrientation(side));
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

    private int getOffsetX(byte aSide, int aMultiplier) {
        return this.xCoord + ForgeDirection.getOrientation(aSide).offsetX * aMultiplier;
    }

    private short getOffsetY(byte aSide, int aMultiplier) {
        return (short) (this.yCoord + ForgeDirection.getOrientation(aSide).offsetY * aMultiplier);
    }

    private int getOffsetZ(byte aSide, int aMultiplier) {
        return this.zCoord + ForgeDirection.getOrientation(aSide).offsetZ * aMultiplier;
    }

    private boolean crossedChunkBorder(int aX, int aZ) {
        return aX >> 4 != this.xCoord >> 4 || aZ >> 4 != this.zCoord >> 4;
    }

    private final TileEntity getTileEntityAtSide(byte aSide) {
        int tX = this.getOffsetX(aSide, 1);
        int tY = this.getOffsetY(aSide, 1);
        int tZ = this.getOffsetZ(aSide, 1);
        return this.crossedChunkBorder(tX, tZ) && !this.worldObj.blockExists(tX, tY, tZ) ? null
                : this.worldObj.getTileEntity(tX, tY, tZ);
    }
}
