package appeng.tile.networking;

import static appeng.helpers.WireLessToolHelper.getAndCheckTile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import appeng.api.networking.IGridConnection;
import appeng.api.util.DimensionalCoord;

public class TileWirelessHub extends TileWirelessBase {

    HashMap<TileWirelessBase, IGridConnection> connections = new HashMap<>();

    public TileWirelessHub() {
        super(32);
    }

    @Override
    protected void setDataConnections(TileWirelessBase other, IGridConnection connection) {
        if (connections.containsKey(other)) throw new IllegalStateException("Connection already set!");

        connections.put(other, connection);
    }

    @Override
    protected void removeDataConnections(TileWirelessBase other) {
        connections.remove(other);
    }

    @Override
    public List<TileWirelessBase> getConnectedTiles() {
        return ImmutableList.copyOf(connections.keySet());
    }

    @Override
    public List<IGridConnection> getAllConnections() {
        return ImmutableList.copyOf(connections.values());
    }

    @Override
    public Map<TileWirelessBase, IGridConnection> getConnectionMap() {
        return ImmutableMap.copyOf(connections);
    }

    @Override
    public IGridConnection getConnection(TileWirelessBase other) {
        return connections.get(other);
    }

    @Override
    public boolean doLink(TileWirelessBase other) {
        if (isConnectedTo(other) || !other.canAddLink() || !canAddLink()) return false;
        return setupConnection(other);
    }

    @Override
    public void doUnlink(TileWirelessBase other) {
        breakConnection(other);
    }

    @Override
    public void doUnlink() {
        breakAllConnections();
    }

    @Override
    protected void tryRestoreConnection(List<DimensionalCoord> locList) {
        for (DimensionalCoord other : locList) {
            TileWirelessBase tile = getAndCheckTile(other, worldObj, null);
            if (tile == null) continue;
            doLink(tile);
        }
    }
}
