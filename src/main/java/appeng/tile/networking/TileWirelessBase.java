/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.PowerMultiplier;
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

public abstract class TileWirelessBase extends AENetworkTile implements IColorableTile {

    TileWirelessBase(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    private AEColor color = AEColor.Transparent;

    private final int maxConnections;

    protected DimensionalCoord location = getLocation();

    protected abstract void setDataConnections(TileWirelessBase other, IGridConnection connection);

    protected abstract void removeDataConnections(TileWirelessBase other);

    public abstract Set<TileWirelessBase> getConnectedTiles();

    public abstract Set<IGridConnection> getAllConnections();

    public abstract Map<TileWirelessBase, IGridConnection> getConnectionMap();

    public abstract IGridConnection getConnection(TileWirelessBase other);

    public boolean isConnectedTo(TileWirelessBase other) {
        return getConnection(other) != null && other.getConnection(this) != null;
    }

    public boolean isLinked() {
        return !getConnectedTiles().isEmpty();
    }

    public boolean isHub() {
        return maxConnections > 1;
    }

    public int getFreeSlots() {
        return maxConnections - getConnectedTiles().size();
    }

    public boolean canAddLink() {
        return getFreeSlots() > 0;
    }

    public int getUsedChannels() {
        int used = 0;
        for (IGridConnection connection : getGridNode(ForgeDirection.UNKNOWN).getConnections()) {
            used = Math.max(used, connection.getUsedChannels());
        }
        return used;
    }

    public abstract boolean doLink(TileWirelessBase other);

    public abstract void doUnlink(TileWirelessBase other);

    public abstract void doUnlink();

    protected boolean setupConnection(TileWirelessBase other) {
        if (!canAddLink() && other.canAddLink()) return false;
        try {
            IGridNode selfNode = getGridNode(ForgeDirection.UNKNOWN);
            IGridNode targetNode = other.getGridNode(ForgeDirection.UNKNOWN);
            if (selfNode != null && targetNode != null) {
                IGridConnection connection = AEApi.instance().createGridConnection(selfNode, targetNode);

                setDataConnections(other, connection);
                other.setDataConnections(this, connection);
                updateActive();
                other.updateActive();
                return true;
            }
        } catch (FailedConnection ignored) {}
        return false;
    }

    protected void breakConnection(TileWirelessBase other) {
        IGridConnection connection = getConnection(other);
        if (connection == null) return;
        connection.destroy();
        removeDataConnections(other);
        other.removeDataConnections(this);
        updateActive();
        other.updateActive();
    }

    protected void breakAllConnections() {
        if (!isLinked()) return;
        for (TileWirelessBase other : getConnectedTiles()) {
            breakConnection(other);
        }
    }

    public void setConnectionsPowerDraw() {
        double idlePowerUse = PowerMultiplier.CONFIG.multiply( // apply the AE2 configuration multiplier
                getConnectedTiles().stream().mapToDouble(tile -> {
                    int dx = this.xCoord - tile.xCoord;
                    int dy = this.yCoord - tile.yCoord;
                    int dz = this.zCoord - tile.zCoord;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    return AEConfig.instance.getWirelessConnectorPowerBase()
                            + AEConfig.instance.getWirelessConnectorPowerDistanceMultiplier() * dist
                                    * Math.log(dist * dist + 3);
                }).sum());
        this.setPowerDraw(idlePowerUse);
    }

    public void setPowerDraw(double d) {
        this.getProxy().setIdlePowerUsage(d);
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
        setConnectionsPowerDraw();
        if (isLinked()) {
            worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 1, 3);
        } else {
            worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 0, 3);
        }
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
        // tryRestoreConnection();
    }

    // private void tryRestoreConnection() {
    // if (connection == null && target != null) {
    // setupConnection(target);
    // }
    // }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileWirelessConnector(final NBTTagCompound data) {
        data.setShort("Color", (short) color.ordinal());
        NBTTagList tagsList = new NBTTagList();
        for (TileWirelessBase other : getConnectedTiles()) {
            NBTTagCompound locationTag = new NBTTagCompound();
            other.location.writeToNBT(locationTag);
            tagsList.appendTag(locationTag);
        }
        data.setTag("connectedTargets", tagsList);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileWirelessConnector(final NBTTagCompound data) {
        if (data.hasKey("Color")) {
            this.color = AEColor.values()[data.getShort("Color")];
            this.getProxy().setColor(this.color);
        }
        if (data.hasKey("connectedTargets")) {
            NBTTagList tagsList = (NBTTagList) data.getTag("connectedTargets");
            for (int i = 0; i < tagsList.tagCount(); i++) {
                NBTTagCompound tag = tagsList.getCompoundTagAt(i);
                DimensionalCoord dc = DimensionalCoord.readFromNBT(tag);
                TileEntity te = worldObj.getTileEntity(dc.x, dc.y, dc.z);
                if (te instanceof TileWirelessBase tw && getConnection(tw) == null) {
                    setupConnection(tw);
                }
            }
        }
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
            if (te instanceof TileWirelessBase tw) {
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

    public SuperWirelessToolDataObject getDataForTool(int i) {
        return new SuperWirelessToolDataObject(
                i,
                this.hasCustomName() ? this.getCustomName() : this.getBlockType().getLocalizedName(),
                getLocation(),
                isLinked(),
                getConnectedTiles().stream().map(t -> t.location).findFirst().orElse(null),
                getColor(),
                getUsedChannels(),
                isHub(),
                getFreeSlots());
    }
}
