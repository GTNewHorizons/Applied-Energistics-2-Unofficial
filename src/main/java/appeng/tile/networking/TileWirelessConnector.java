/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import static appeng.helpers.WireLessToolHelper.getAndCheckTile;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import appeng.api.networking.IGridConnection;
import appeng.api.util.DimensionalCoord;

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
        if (target == other) {
            return connection;
        }
        return null;
    }

    @Override
    public boolean doLink(TileWirelessBase other) {
        if (!other.canAddLink()) return false;

        doUnlink();
        return setupConnection(other);
    }

    @Override
    public boolean canAddLink() {
        return true;
    }

    @Override
    public void doUnlink(TileWirelessBase other) {
        if (target == other) {
            breakConnection(other);
        }
    }

    @Override
    public void doUnlink() {
        breakAllConnections();
    }

    @Override
    protected void tryRestoreConnection(List<DimensionalCoord> locList) {
        if (connection == null) {
            TileWirelessBase tile = getAndCheckTile(locList.get(0), worldObj, null);
            if (tile == null) return;
            doLink(tile);
        }
    }
}
