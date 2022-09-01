package appeng.parts.layers;

import appeng.api.parts.IPart;
import appeng.api.parts.LayerBase;
import appeng.integration.IntegrationType;
import appeng.parts.p2p.IPartGT5Power;
import appeng.transformer.annotations.Integration;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GT_Utility;
import ic2.api.energy.tile.IEnergySink;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

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
                    long rUsedAmperes;
                    for (rUsedAmperes = 0L;
                            amperage > rUsedAmperes
                                    && ((IEnergySink) part).getDemandedEnergy() > 0.0D
                                    && ((IEnergySink) part).injectEnergy(dir, (double) voltage, (double) voltage)
                                            < (double) voltage;
                            ++rUsedAmperes) {}

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

    public byte getColorization() {
        return -1;
    }

    public byte setColorization(byte b) {
        return -1;
    }

    public final World getWorld() {
        return this.worldObj;
    }

    public final int getXCoord() {
        return this.xCoord;
    }

    public final short getYCoord() {
        return (short) this.yCoord;
    }

    public final int getZCoord() {
        return this.zCoord;
    }

    public final int getOffsetX(byte aSide, int aMultiplier) {
        return this.xCoord + ForgeDirection.getOrientation(aSide).offsetX * aMultiplier;
    }

    public final short getOffsetY(byte aSide, int aMultiplier) {
        return (short) (this.yCoord + ForgeDirection.getOrientation(aSide).offsetY * aMultiplier);
    }

    public final int getOffsetZ(byte aSide, int aMultiplier) {
        return this.zCoord + ForgeDirection.getOrientation(aSide).offsetZ * aMultiplier;
    }

    public final boolean isServerSide() {
        return !this.worldObj.isRemote;
    }

    public final boolean isClientSide() {
        return this.worldObj.isRemote;
    }

    public final boolean openGUI(EntityPlayer aPlayer) {
        return this.openGUI(aPlayer, 0);
    }

    public final boolean openGUI(EntityPlayer aPlayer, int aID) {
        return false;
    }

    public final int getRandomNumber(int aRange) {
        return this.worldObj.rand.nextInt(aRange);
    }

    public final BiomeGenBase getBiome(int aX, int aZ) {
        return this.worldObj.getBiomeGenForCoords(aX, aZ);
    }

    public final BiomeGenBase getBiome() {
        return this.getBiome(this.xCoord, this.zCoord);
    }

    public final Block getBlockOffset(int aX, int aY, int aZ) {
        return this.getBlock(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final Block getBlockAtSide(byte aSide) {
        return this.getBlockAtSideAndDistance(aSide, 1);
    }

    public final Block getBlockAtSideAndDistance(byte aSide, int aDistance) {
        return this.getBlock(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final byte getMetaIDOffset(int aX, int aY, int aZ) {
        return this.getMetaID(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final byte getMetaIDAtSide(byte aSide) {
        return this.getMetaIDAtSideAndDistance(aSide, 1);
    }

    public final byte getMetaIDAtSideAndDistance(byte aSide, int aDistance) {
        return this.getMetaID(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final byte getLightLevelOffset(int aX, int aY, int aZ) {
        return this.getLightLevel(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final byte getLightLevelAtSide(byte aSide) {
        return this.getLightLevelAtSideAndDistance(aSide, 1);
    }

    public final byte getLightLevelAtSideAndDistance(byte aSide, int aDistance) {
        return this.getLightLevel(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final boolean getOpacityOffset(int aX, int aY, int aZ) {
        return this.getOpacity(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final boolean getOpacityAtSide(byte aSide) {
        return this.getOpacityAtSideAndDistance(aSide, 1);
    }

    public final boolean getOpacityAtSideAndDistance(byte aSide, int aDistance) {
        return this.getOpacity(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final boolean getSkyOffset(int aX, int aY, int aZ) {
        return this.getSky(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final boolean getSkyAtSide(byte aSide) {
        return this.getSkyAtSideAndDistance(aSide, 1);
    }

    public final boolean getSkyAtSideAndDistance(byte aSide, int aDistance) {
        return this.getSky(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final boolean getAirOffset(int aX, int aY, int aZ) {
        return this.getAir(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final boolean getAirAtSide(byte aSide) {
        return this.getAirAtSideAndDistance(aSide, 1);
    }

    public final boolean getAirAtSideAndDistance(byte aSide, int aDistance) {
        return this.getAir(
                this.getOffsetX(aSide, aDistance),
                this.getOffsetY(aSide, aDistance),
                this.getOffsetZ(aSide, aDistance));
    }

    public final TileEntity getTileEntityOffset(int aX, int aY, int aZ) {
        return this.getTileEntity(this.xCoord + aX, this.yCoord + aY, this.zCoord + aZ);
    }

    public final TileEntity getTileEntityAtSideAndDistance(byte aSide, int aDistance) {
        return aDistance == 1
                ? this.getTileEntityAtSide(aSide)
                : this.getTileEntity(
                        this.getOffsetX(aSide, aDistance),
                        this.getOffsetY(aSide, aDistance),
                        this.getOffsetZ(aSide, aDistance));
    }

    public final IInventory getIInventory(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntity(aX, aY, aZ);
        return tTileEntity instanceof IInventory ? (IInventory) tTileEntity : null;
    }

    public final IInventory getIInventoryOffset(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntityOffset(aX, aY, aZ);
        return tTileEntity instanceof IInventory ? (IInventory) tTileEntity : null;
    }

    public final IInventory getIInventoryAtSide(byte aSide) {
        TileEntity tTileEntity = this.getTileEntityAtSide(aSide);
        return tTileEntity instanceof IInventory ? (IInventory) tTileEntity : null;
    }

    public final IInventory getIInventoryAtSideAndDistance(byte aSide, int aDistance) {
        TileEntity tTileEntity = this.getTileEntityAtSideAndDistance(aSide, aDistance);
        return tTileEntity instanceof IInventory ? (IInventory) tTileEntity : null;
    }

    public final IFluidHandler getITankContainer(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntity(aX, aY, aZ);
        return tTileEntity instanceof IFluidHandler ? (IFluidHandler) tTileEntity : null;
    }

    public final IFluidHandler getITankContainerOffset(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntityOffset(aX, aY, aZ);
        return tTileEntity instanceof IFluidHandler ? (IFluidHandler) tTileEntity : null;
    }

    public final IFluidHandler getITankContainerAtSide(byte aSide) {
        TileEntity tTileEntity = this.getTileEntityAtSide(aSide);
        return tTileEntity instanceof IFluidHandler ? (IFluidHandler) tTileEntity : null;
    }

    public final IFluidHandler getITankContainerAtSideAndDistance(byte aSide, int aDistance) {
        TileEntity tTileEntity = this.getTileEntityAtSideAndDistance(aSide, aDistance);
        return tTileEntity instanceof IFluidHandler ? (IFluidHandler) tTileEntity : null;
    }

    public final IGregTechTileEntity getIGregTechTileEntity(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntity(aX, aY, aZ);
        return tTileEntity instanceof IGregTechTileEntity ? (IGregTechTileEntity) tTileEntity : null;
    }

    public final IGregTechTileEntity getIGregTechTileEntityOffset(int aX, int aY, int aZ) {
        TileEntity tTileEntity = this.getTileEntityOffset(aX, aY, aZ);
        return tTileEntity instanceof IGregTechTileEntity ? (IGregTechTileEntity) tTileEntity : null;
    }

    public final IGregTechTileEntity getIGregTechTileEntityAtSide(byte aSide) {
        TileEntity tTileEntity = this.getTileEntityAtSide(aSide);
        return tTileEntity instanceof IGregTechTileEntity ? (IGregTechTileEntity) tTileEntity : null;
    }

    public final IGregTechTileEntity getIGregTechTileEntityAtSideAndDistance(byte aSide, int aDistance) {
        TileEntity tTileEntity = this.getTileEntityAtSideAndDistance(aSide, aDistance);
        return tTileEntity instanceof IGregTechTileEntity ? (IGregTechTileEntity) tTileEntity : null;
    }

    private boolean crossedChunkBorder(int aX, int aZ) {
        return aX >> 4 != this.xCoord >> 4 || aZ >> 4 != this.zCoord >> 4;
    }

    public final Block getBlock(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                ? Blocks.air
                : this.worldObj.getBlock(aX, aY, aZ);
    }

    public final byte getMetaID(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                ? 0
                : (byte) this.worldObj.getBlockMetadata(aX, aY, aZ);
    }

    public final byte getLightLevel(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                ? 0
                : (byte) ((int) (this.worldObj.getLightBrightness(aX, aY, aZ) * 15.0F));
    }

    public final boolean getSky(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                || this.worldObj.canBlockSeeTheSky(aX, aY, aZ);
    }

    public final boolean getOpacity(int aX, int aY, int aZ) {
        return (!this.crossedChunkBorder(aX, aZ) || this.worldObj.blockExists(aX, aY, aZ))
                && GT_Utility.isOpaqueBlock(this.worldObj, aX, aY, aZ);
    }

    public final boolean getAir(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                || GT_Utility.isBlockAir(this.worldObj, aX, aY, aZ);
    }

    public final TileEntity getTileEntity(int aX, int aY, int aZ) {
        return this.crossedChunkBorder(aX, aZ) && !this.worldObj.blockExists(aX, aY, aZ)
                ? null
                : this.worldObj.getTileEntity(aX, aY, aZ);
    }

    public final TileEntity getTileEntityAtSide(byte aSide) {
        int tX = this.getOffsetX(aSide, 1);
        int tY = this.getOffsetY(aSide, 1);
        int tZ = this.getOffsetZ(aSide, 1);
        return this.crossedChunkBorder(tX, tZ) && !this.worldObj.blockExists(tX, tY, tZ)
                ? null
                : this.worldObj.getTileEntity(tX, tY, tZ);
    }

    public boolean isDead() {
        return this.isInvalidTileEntity();
    }

    public void sendBlockEvent(byte b, byte b1) {}

    public long getTimer() {
        return 0L;
    }

    public void setLightValue(byte b) {}

    public boolean isInvalidTileEntity() {
        return this.isInvalid();
    }
}
