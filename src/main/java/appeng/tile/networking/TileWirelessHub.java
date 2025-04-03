package appeng.tile.networking;

import java.util.ArrayList;

import net.minecraft.tileentity.TileEntity;

import appeng.api.util.DimensionalCoord;

public class TileWirelessHub extends TileWirelessConnector {

    private final ArrayList<DimensionalCoord> connectionList = new ArrayList<>();
    private double powerDraw = 0d;

    @Override
    public DimensionalCoord getTarget() {
        return null;
    }

    @Override
    public boolean hasConnection() {
        return !connectionList.isEmpty();
    }

    public void addConnectionList(DimensionalCoord dc) {
        connectionList.add(dc);
    }

    public void removeConnectionList(DimensionalCoord dc) {
        connectionList.remove(dc);
    }

    @Override
    public boolean setupConnection(DimensionalCoord dc) {
        TileWirelessConnector te = (TileWirelessConnector) worldObj.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof TileWirelessHub) return false;
        return te.setupConnection(this.getLocation());
    }

    @Override
    public void breakConnection() {
        for (DimensionalCoord dc : connectionList) {
            TileEntity te = worldObj.getTileEntity(dc.x, dc.y, dc.z);
            if (te instanceof TileWirelessConnector wc) {
                wc.breakConnectionNoCall();
            }
        }
    }

    @Override
    public void cleanUp() {
        updateActive();
    }

    @Override
    public void setPowerDraw(double d) {
        this.powerDraw += d;
        this.getProxy().setIdlePowerUsage(this.powerDraw);
    }

    @Override
    public int getChannelUsage() {
        int channels = 0;
        for (DimensionalCoord dc : connectionList) {
            TileEntity te = worldObj.getTileEntity(dc.x, dc.y, dc.z);
            if (te instanceof TileWirelessConnector wc) {
                channels += wc.getChannelUsage();
            }
        }
        return channels;
    }

    @Override
    public boolean isHub() {
        return true;
    }

    @Override
    public int getFreeSlots() {
        return 32 - connectionList.size();
    }

    public ArrayList<DimensionalCoord> getConnectionList() {
        return connectionList;
    }
}
