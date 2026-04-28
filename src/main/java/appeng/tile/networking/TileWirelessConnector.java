/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import static appeng.helpers.WireLessToolHelper.getAndCheckTile;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;

public class TileWirelessConnector extends TileWirelessBase {

    private IGridConnection connection;
    private TileWirelessBase target;

    public TileWirelessConnector() {
        super(1);
    }

    @Override
    protected void setDataConnections(TileWirelessBase other, IGridConnection connection) {
        if (this.connection != null) breakAllConnections();

        this.connection = connection;
        this.target = other;
    }

    @Override
    protected void removeDataConnections(TileWirelessBase other) {
        if (target == other) {
            connection = null;
            target = null;
        }
    }

    @Override
    public List<TileWirelessBase> getConnectedTiles() {
        return target == null ? ImmutableList.of() : ImmutableList.of(target);
    }

    @Override
    public List<IGridConnection> getAllConnections() {
        return connection == null ? ImmutableList.of() : ImmutableList.of(connection);
    }

    @Override
    public Map<TileWirelessBase, IGridConnection> getConnectionMap() {
        return target != null && connection != null ? ImmutableMap.of(target, connection) : ImmutableMap.of();
    }

    @Override
    public IGridConnection getConnection(TileWirelessBase other) {
        if (target == other) return connection;
        return null;
    }

    @Override
    public BindResult doLink(TileWirelessBase other) {
        this.doUnlink();
        return setupConnection(other);
    }

    @Override
    public boolean canAddLink() {
        return true;
    }

    @Override
    public void doUnlink(TileWirelessBase other) {
        if (target == other) this.breakConnection(other);
    }

    @Override
    public void doUnlink() {
        this.breakAllConnections();
    }

    @Override
    protected void tryRestoreConnection(Set<DimensionalCoord> locList) {
        final Iterator<DimensionalCoord> iterator = locList.iterator();
        if (iterator.hasNext()) {
            if (this.connection != null) {
                iterator.next();
                iterator.remove();
                return;
            }

            final TileWirelessBase tile = getAndCheckTile(iterator.next(), worldObj, null);
            if (tile == null) return;
            final BindResult result = WireLessToolHelper.performConnection(tile, this, new MachineSource(this));
            if (result == BindResult.SUCCESS || result == BindResult.ALREADY_BIND) iterator.remove();
        }
    }
}
