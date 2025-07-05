/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.IGridConnection;

public class TileWirelessConnector extends TileWirelessBase {

    private IGridConnection connection;
    private TileWirelessBase target;

    public TileWirelessConnector() {
        super(1);
    }

    @Override
    protected void setDataConnections(TileWirelessBase other, IGridConnection connection) {
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
    public Set<TileWirelessBase> getConnectedTiles() {
        return Set.of(target);
    }

    @Override
    public Set<IGridConnection> getAllConnections() {
        return Set.of(connection);
    }

    @Override
    public Map<TileWirelessBase, IGridConnection> getConnectionMap() {
        Map<TileWirelessBase, IGridConnection> map = new HashMap<>();
        if (target != null && connection != null) {
            map.put(target, connection);
        }
        return map;
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
        // this.customName = other.customName;
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
}
