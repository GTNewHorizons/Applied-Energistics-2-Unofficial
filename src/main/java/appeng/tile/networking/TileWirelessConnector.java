/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnection;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.helpers.SuperWirelessToolDataObject;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import io.netty.buffer.ByteBuf;

public class TileWirelessConnector extends AENetworkTile implements IColorableTile {

    private IGridConnection connection;
    private DimensionalCoord target;
    private AEColor color = AEColor.Transparent;

    private void tryRestoreConnection() {
        if (connection == null && target != null) {
            setupConnection(target);
        }
    }

    @Override
    protected AENetworkProxy createProxy() {
        AENetworkProxy ae = super.createProxy();
        ae.setFlags(GridFlags.DENSE_CAPACITY);
        return ae;
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    public void updateActive() {
        if (hasConnection()) {
            worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 1, 3);
        } else {
            worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 0, 3);
        }
    }

    public void setPowerDraw(double d) {
        this.getProxy().setIdlePowerUsage(d);
    }

    public boolean setupConnection(DimensionalCoord dc) {
        if (hasConnection()) return false;
        try {
            TileEntity te = worldObj.getTileEntity(dc.x, dc.y, dc.z);
            if (te instanceof TileWirelessConnector wc) {
                IGridNode selfNode = getGridNode(ForgeDirection.UNKNOWN);
                IGridNode targetNode = wc.getGridNode(ForgeDirection.UNKNOWN);
                if (selfNode != null && targetNode != null) {
                    double pw = getConnectionPowerDraw(dc);
                    connection = AEApi.instance().createGridConnection(selfNode, targetNode);
                    target = dc;
                    setPowerDraw(pw);
                    updateActive();

                    if (wc instanceof TileWirelessHub wh) {
                        wh.addConnectionList(getLocation());
                    } else {
                        wc.connection = connection;
                        wc.target = getLocation();
                    }
                    wc.setPowerDraw(pw);
                    wc.updateActive();
                    return true;
                }
            }
        } catch (FailedConnection ignored) {}
        return false;
    }

    public void breakConnection() {
        if (hasConnection()) {
            connection.destroy();
            TileEntity te = worldObj.getTileEntity(target.x, target.y, target.z);
            if (te instanceof TileWirelessConnector wc) {
                if (te instanceof TileWirelessHub wh) {
                    wh.removeConnectionList(getLocation());
                    wh.setPowerDraw(-getPowerUsage());
                }
                wc.cleanUp();
            }
            cleanUp();
        }
    }

    public void breakConnectionNoCall() {
        if (hasConnection()) {
            connection.destroy();
            cleanUp();
        }
    }

    public void cleanUp() {
        connection = null;
        target = null;
        setPowerDraw(0d);
        updateActive();
    }

    private double getConnectionPowerDraw(DimensionalCoord dc) {
        int dx = this.xCoord - dc.x;
        int dy = this.yCoord - dc.y;
        int dz = this.zCoord - dc.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return AEConfig.instance.getWirelessConnectorPowerBase()
                + AEConfig.instance.getWirelessConnectorPowerDistanceMultiplier() * dist * Math.log(dist * dist + 3);
    }

    public boolean hasConnection() {
        return connection != null;
    }

    public DimensionalCoord getTarget() {
        return target;
    }

    public int getChannelUsage() {
        return hasConnection() ? connection.getUsedChannels() : 0;
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileSecurity(final ByteBuf data) {
        final AEColor oldColor = this.color;
        this.color = AEColor.values()[data.readByte()];
        return oldColor != this.color;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSecurity(final ByteBuf data) {
        data.writeByte(this.color.ordinal());
        tryRestoreConnection();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileWirelessConnector(final NBTTagCompound data) {
        data.setShort("Color", (short) color.ordinal());
        if (target != null) {
            data.setInteger("Xc", target.x);
            data.setInteger("Yc", target.y);
            data.setInteger("Zc", target.z);
        }
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileWirelessConnector(final NBTTagCompound data) {
        if (data.hasKey("Color")) {
            this.color = AEColor.values()[data.getShort("Color")];
            this.getProxy().setColor(this.color);
        }
        if (data.hasKey("Xc"))
            this.target = new DimensionalCoord(data.getInteger("Xc"), data.getInteger("Yc"), data.getInteger("Zc"), 0);
    }

    @Override
    public AEColor getColor() {
        return this.color;
    }

    public double getPowerUsage() {
        return getProxy().getIdlePowerUsage();
    }

    @Override
    public boolean recolourBlock(ForgeDirection side, AEColor colour, EntityPlayer who) {
        if (this.color == colour) return false;
        this.color = colour;
        this.getProxy().setColor(this.color);

        if (getGridNode(side) != null) {
            getGridNode(side).updateState();
        }

        this.markDirty();
        this.markForUpdate();
        return true;
    }

    public void madChameleonRecolor() {
        DimensionalCoord dc = this.getLocation();
        ArrayList<Integer> ic = new ArrayList<>();
        int i = 0;
        for (ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity te = worldObj.getTileEntity(dc.x + fd.offsetX, dc.y + fd.offsetY, dc.z + fd.offsetZ);
            if (te instanceof TileWirelessConnector tw) {
                ic.add(tw.getColor().ordinal());
                while (ic.contains(i)) {
                    i++;
                }
            }
        }

        AEColor colour = AEColor.values()[i];

        if (this.color == colour) return;
        this.color = colour;
        this.getProxy().setColor(this.color);

        if (getGridNode(ForgeDirection.UNKNOWN) != null) {
            getGridNode(ForgeDirection.UNKNOWN).updateState();
        }

        this.markDirty();
        this.markForUpdate();
    }

    public boolean isHub() {
        return false;
    }

    public int getFreeSlots() {
        return hasConnection() ? 0 : 1;
    }

    public SuperWirelessToolDataObject getDataForTool(int i) {
        return new SuperWirelessToolDataObject(
                i,
                this.hasCustomName() ? this.getCustomName() : this.getBlockType().getLocalizedName(),
                getLocation(),
                hasConnection(),
                getTarget(),
                getColor(),
                getChannelUsage(),
                isHub(),
                getFreeSlots());
    }
}
