package appeng.tile.powersink;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.integration.IntegrationType;
import appeng.transformer.annotations.Integration;
import appeng.util.Platform;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.fluids.IFluidHandler;

@Integration.Interface( iname = IntegrationType.GT, iface = "gregtech.api.interfaces.tileentity.IEnergyConnected" )
public abstract class GTPowerSink extends AERootPoweredTile implements IEnergyConnected {
    @Override
    public long injectEnergyUnits(byte side, long voltage, long amperage) {
        double e = PowerUnits.EU.convertTo(PowerUnits.AE, voltage * amperage);
        double overflow = PowerUnits.AE.convertTo(PowerUnits.EU, this.funnelPowerIntoStorage(e, Actionable.SIMULATE));
        long used = amperage - (int)Math.ceil(overflow / voltage);
        if (used > 0) {
            e = PowerUnits.EU.convertTo(PowerUnits.AE, voltage * used);
            PowerUnits.AE.convertTo(PowerUnits.EU, this.funnelPowerIntoStorage(e, Actionable.MODULATE));
        }
        return used;
    }

    @Override
    public boolean inputEnergyFrom(byte b) {
        return true;
    }

    @Override
    public boolean outputsEnergyTo(byte b) {
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

    @Override
    public World getWorld() {
        return getWorldObj();
    }

    @Override
    public int getXCoord() {
        return xCoord;
    }

    @Override
    public short getYCoord() {
        return (short)yCoord;
    }

    @Override
    public int getZCoord() {
        return zCoord;
    }

    @Override
    public boolean isServerSide() {
        return Platform.isServer();
    }

    @Override
    public boolean isClientSide() {
        return !Platform.isServer();
    }

    @Override
    public int getRandomNumber(int i) {
        return 0;
    }

    @Override
    public TileEntity getTileEntity(int i, int i1, int i2) {
        return worldObj.getTileEntity(i, i1, i2);
    }

    @Override
    public TileEntity getTileEntityOffset(int i, int i1, int i2) {
        return null;
    }

    @Override
    public TileEntity getTileEntityAtSide(byte b) {
        return null;
    }

    @Override
    public TileEntity getTileEntityAtSideAndDistance(byte b, int i) {
        return null;
    }

    @Override
    public IInventory getIInventory(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IInventory getIInventoryOffset(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IInventory getIInventoryAtSide(byte b) {
        return null;
    }

    @Override
    public IInventory getIInventoryAtSideAndDistance(byte b, int i) {
        return null;
    }

    @Override
    public IFluidHandler getITankContainer(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IFluidHandler getITankContainerOffset(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IFluidHandler getITankContainerAtSide(byte b) {
        return null;
    }

    @Override
    public IFluidHandler getITankContainerAtSideAndDistance(byte b, int i) {
        return null;
    }

    @Override
    public IGregTechTileEntity getIGregTechTileEntity(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IGregTechTileEntity getIGregTechTileEntityOffset(int i, int i1, int i2) {
        return null;
    }

    @Override
    public IGregTechTileEntity getIGregTechTileEntityAtSide(byte b) {
        return null;
    }

    @Override
    public IGregTechTileEntity getIGregTechTileEntityAtSideAndDistance(byte b, int i) {
        return null;
    }

    @Override
    public Block getBlock(int i, int i1, int i2) {
        return null;
    }

    @Override
    public Block getBlockOffset(int i, int i1, int i2) {
        return null;
    }

    @Override
    public Block getBlockAtSide(byte b) {
        return null;
    }

    @Override
    public Block getBlockAtSideAndDistance(byte b, int i) {
        return null;
    }

    @Override
    public byte getMetaID(int i, int i1, int i2) {
        return 0;
    }

    @Override
    public byte getMetaIDOffset(int i, int i1, int i2) {
        return 0;
    }

    @Override
    public byte getMetaIDAtSide(byte b) {
        return 0;
    }

    @Override
    public byte getMetaIDAtSideAndDistance(byte b, int i) {
        return 0;
    }

    @Override
    public byte getLightLevel(int i, int i1, int i2) {
        return 0;
    }

    @Override
    public byte getLightLevelOffset(int i, int i1, int i2) {
        return 0;
    }

    @Override
    public byte getLightLevelAtSide(byte b) {
        return 0;
    }

    @Override
    public byte getLightLevelAtSideAndDistance(byte b, int i) {
        return 0;
    }

    @Override
    public boolean getOpacity(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getOpacityOffset(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getOpacityAtSide(byte b) {
        return false;
    }

    @Override
    public boolean getOpacityAtSideAndDistance(byte b, int i) {
        return false;
    }

    @Override
    public boolean getSky(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getSkyOffset(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getSkyAtSide(byte b) {
        return false;
    }

    @Override
    public boolean getSkyAtSideAndDistance(byte b, int i) {
        return false;
    }

    @Override
    public boolean getAir(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getAirOffset(int i, int i1, int i2) {
        return false;
    }

    @Override
    public boolean getAirAtSide(byte b) {
        return false;
    }

    @Override
    public boolean getAirAtSideAndDistance(byte b, int i) {
        return false;
    }

    @Override
    public BiomeGenBase getBiome() {
        return null;
    }

    @Override
    public BiomeGenBase getBiome(int i, int i1) {
        return null;
    }

    @Override
    public int getOffsetX(byte b, int i) {
        return 0;
    }

    @Override
    public short getOffsetY(byte b, int i) {
        return 0;
    }

    @Override
    public int getOffsetZ(byte b, int i) {
        return 0;
    }

    @Override
    public boolean isDead() {
        return tileEntityInvalid;
    }

    @Override
    public void sendBlockEvent(byte b, byte b1) {

    }

    @Override
    public long getTimer() {
        return 0;
    }

    @Override
    public void setLightValue(byte b) {

    }

    @Override
    public boolean isInvalidTileEntity() {
        return tileEntityInvalid;
    }

    @Override
    public boolean openGUI(EntityPlayer entityPlayer, int i) {
        return false;
    }

    @Override
    public boolean openGUI(EntityPlayer entityPlayer) {
        return false;
    }
}
