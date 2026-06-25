/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableList;

import appeng.api.AEApi;
import appeng.api.config.PowerMultiplier;
import appeng.api.exceptions.ExistingConnectionException;
import appeng.api.exceptions.FailedConnection;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.helpers.WireLessToolHelper.BindResult;
import appeng.helpers.WirelessToolDataObject;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;

public abstract class TileWirelessBase extends AENetworkTile implements IColorableTile {

    TileWirelessBase(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    private AEColor color = AEColor.Transparent;

    private final int maxConnections;
    private final Set<DimensionalCoord> linkedTargets = new LinkedHashSet<>();

    protected abstract void addActiveConnection(TileWirelessBase other, IGridConnection connection);

    protected abstract void removeActiveConnection(TileWirelessBase other);

    public abstract List<TileWirelessBase> getConnectedTiles();

    public List<DimensionalCoord> getConnectedCoords() {
        return ImmutableList.copyOf(this.linkedTargets);
    }

    public abstract IGridConnection getConnection(TileWirelessBase other);

    public boolean isConnectedTo(TileWirelessBase other) {
        return getConnection(other) != null && other.getConnection(this) != null;
    }

    private boolean isSameLocation(TileWirelessBase other) {
        return getLocation().isEqual(other.getLocation());
    }

    public boolean isLinked() {
        return !this.linkedTargets.isEmpty();
    }

    public boolean isHub() {
        return maxConnections > 1;
    }

    public int getFreeSlots() {
        return maxConnections - this.linkedTargets.size();
    }

    public boolean canAddLink() {
        return getFreeSlots() > 0;
    }

    public boolean canAddLink(TileWirelessBase other) {
        return this.hasLinkedTarget(other.getLocation()) || canAddLink();
    }

    public int getUsedChannels() {
        int used = 0;
        for (IGridConnection connection : getGridNode(ForgeDirection.UNKNOWN).getConnections()) {
            used = Math.max(used, connection.getUsedChannels());
        }
        return used;
    }

    /**
     * DO NOT USE THIS, USE WireLessToolHelper.performConnection()
     **/
    public abstract BindResult doLink(TileWirelessBase other);

    /**
     * DO NOT USE THIS, USE WireLessToolHelper.restoreConnection()
     **/
    public BindResult restoreLink(TileWirelessBase other) {
        return setupConnection(other, true);
    }

    /**
     * DO NOT USE THIS, USE WireLessToolHelper.breakConnection()
     **/
    public abstract void unlink(TileWirelessBase other);

    /**
     * DO NOT USE THIS, USE WireLessToolHelper.breakConnection()
     **/
    public abstract void unlinkAll();

    protected void removeActiveConnectionToLocation(DimensionalCoord location) {
        for (TileWirelessBase other : getConnectedTiles()) {
            if (other.getLocation().isEqual(location)) {
                breakActiveConnection(other);
            }
        }
    }

    protected BindResult setupConnection(TileWirelessBase other) {
        return setupConnection(other, false);
    }

    private BindResult setupConnection(TileWirelessBase other, boolean restoring) {
        if (this == other || isSameLocation(other)) return BindResult.INVALID_SOURCE;
        if (isConnectedTo(other)) return BindResult.ALREADY_BIND;

        removeActiveConnectionToLocation(other.getLocation());
        other.removeActiveConnectionToLocation(getLocation());

        if (!canAddLink(other)) return BindResult.INVALID_SOURCE;

        try {
            final IGridNode selfNode = getGridNode(ForgeDirection.UNKNOWN);
            final IGridNode targetNode = other.getGridNode(ForgeDirection.UNKNOWN);

            if (selfNode == null) return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.INVALID_SOURCE;
            if (targetNode == null) return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.INVALID_SOURCE;

            final IGridConnection connection = AEApi.instance().createGridConnection(selfNode, targetNode);

            addActiveConnection(other, connection);
            other.addActiveConnection(this, connection);
            addLinkedTarget(other.getLocation());
            other.addLinkedTarget(getLocation());
            updateActive();
            other.updateActive();
            shareCustomName(other);

            return BindResult.SUCCESS;
        } catch (ExistingConnectionException e) {
            return BindResult.ALREADY_BIND;
        } catch (SecurityConnectionException e) {
            return BindResult.FAILED;
        } catch (FailedConnection e) {
            return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.FAILED;
        }
    }

    protected void breakActiveConnection(TileWirelessBase other) {
        IGridConnection connection = getConnection(other);
        if (connection != null) connection.destroy();
        removeActiveConnection(other);
        other.removeActiveConnection(this);
        updateActiveIfLoaded();
        other.updateActiveIfLoaded();
    }

    private void updateActiveIfLoaded() {
        if (worldObj != null && worldObj.blockExists(this.xCoord, this.yCoord, this.zCoord)) updateActive();
    }

    protected void breakAllLinks() {
        for (TileWirelessBase other : getConnectedTiles()) {
            other.removeLinkedTarget(getLocation());
        }

        clearLinkedTargets();

        for (TileWirelessBase other : getConnectedTiles()) {
            breakActiveConnection(other);
        }

        updateActiveIfLoaded();
    }

    protected void breakLink(TileWirelessBase other) {
        removeLinkedTarget(other.getLocation());
        other.removeLinkedTarget(getLocation());
        breakActiveConnection(other);
    }

    protected void breakAllActiveConnections() {
        for (TileWirelessBase other : getConnectedTiles()) {
            breakActiveConnection(other);
        }
    }

    private DimensionalCoord location = null;

    @Override
    public DimensionalCoord getLocation() {
        if (location == null) location = new DimensionalCoord(this);
        return location;
    }

    public void setConnectionsPowerDraw() {
        double idlePowerUse = getConnectedTiles().stream().mapToDouble(tile -> {
            int dx = this.xCoord - tile.xCoord;
            int dy = this.yCoord - tile.yCoord;
            int dz = this.zCoord - tile.zCoord;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return AEConfig.instance.getWirelessConnectorPowerBase()
                    + AEConfig.instance.getWirelessConnectorPowerDistanceMultiplier() * dist
                            * Math.log(dist * dist + 3);
        }).sum();
        this.setPowerDraw(idlePowerUse);
    }

    public void setPowerDraw(double d) {
        this.getProxy().setIdlePowerUsage(d);
    }

    public double getPowerUsage() {
        return PowerMultiplier.CONFIG.multiply(this.getProxy().getIdlePowerUsage());
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

    protected abstract void tryRestoreConnection(Iterable<DimensionalCoord> linkedTargets);

    @Nullable
    protected TileWirelessBase getTargetOrRemoveLink(DimensionalCoord target) {
        if (target.getDimension() != worldObj.provider.dimensionId) {
            removeLinkedTarget(target);
            return null;
        }

        if (!worldObj.blockExists(target.x, target.y, target.z)) return null;

        // ae2stuff persisted hub links only on the connector side.
        if (worldObj.getTileEntity(target.x, target.y, target.z) instanceof TileWirelessBase tile
                && (tile.isHub() || tile.hasLinkedTarget(getLocation()))) {
            return tile;
        }

        removeLinkedTarget(target);
        return null;
    }

    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (!Platform.isServer() || this.linkedTargets.isEmpty()) return;
        this.tryRestoreConnection(ImmutableList.copyOf(this.linkedTargets));
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSecurity(final ByteBuf data) {
        data.writeByte(this.color.ordinal());
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileWirelessConnector(final NBTTagCompound data) {
        data.setShort("Color", (short) color.ordinal());

        final NBTTagCompound nbt = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(nbt, new ArrayList<>(this.linkedTargets));
        data.setTag("connectedTargets", nbt);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileWirelessConnector(final NBTTagCompound data) {
        if (data.hasKey("Color")) {
            this.color = AEColor.values()[data.getShort("Color")];
            this.getProxy().setColor(this.color);
        }

        this.linkedTargets.clear();
        for (DimensionalCoord target : DimensionalCoord.readAsListFromNBT(data.getCompoundTag("connectedTargets"))) {
            if (this.isHub() || this.linkedTargets.isEmpty()) this.addLinkedTarget(target, false);
        }
    }

    protected boolean hasLinkedTarget(DimensionalCoord location) {
        return this.linkedTargets.contains(location);
    }

    protected void addLinkedTarget(DimensionalCoord location) {
        addLinkedTarget(location, true);
    }

    private void addLinkedTarget(DimensionalCoord location, boolean notifyDirty) {
        if (worldObj != null && location.isEqual(getLocation())) return;

        this.linkedTargets.add(new DimensionalCoord(location));
        if (notifyDirty) markDirty();
    }

    protected void removeLinkedTarget(DimensionalCoord location) {
        this.linkedTargets.remove(location);
        markDirty();
    }

    private void clearLinkedTargets() {
        if (this.linkedTargets.isEmpty()) return;
        this.linkedTargets.clear();
        markDirty();
    }

    @Override
    public void onChunkUnload() {
        breakAllActiveConnections();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        breakAllActiveConnections();
        super.invalidate();
    }

    @Override
    public AEColor getColor() {
        return this.color;
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

    protected void shareCustomName(TileWirelessBase other) {
        if (other.hasCustomName()) setCustomName(other.getCustomName());
        else if (hasCustomName()) other.setCustomName(getCustomName());
    }

    public void setCustomName(final String name) {
        super.setCustomName(name);
        for (TileWirelessBase tile : getConnectedTiles()) {
            if (name.isEmpty() && !tile.hasCustomName() || Objects.equals(tile.getCustomName(), name)) continue;
            tile.setCustomName(name);
        }
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

    public WirelessToolDataObject getDataForTool(DimensionalCoord network) {
        return new WirelessToolDataObject(
                network,
                this.hasCustomName() ? this.getCustomName() : this.getBlockType().getLocalizedName(),
                getLocation(),
                isLinked(),
                getConnectedCoords(),
                getColor(),
                getUsedChannels(),
                isHub(),
                getFreeSlots());
    }
}
