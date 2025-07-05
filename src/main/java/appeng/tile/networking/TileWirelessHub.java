package appeng.tile.networking;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.IGridConnection;

public class TileWirelessHub extends TileWirelessBase {

    HashMap<TileWirelessBase, IGridConnection> connections = new HashMap<>();

    TileWirelessHub() {
        super(32);
    }

    @Override
    protected void setDataConnections(TileWirelessBase other, IGridConnection connection) {
        connections.put(other, connection);
    }

    @Override
    protected void removeDataConnections(TileWirelessBase other) {
        connections.remove(other);
    }

    @Override
    public Set<TileWirelessBase> getConnectedTiles() {
        return connections.keySet();
    }

    @Override
    public Set<IGridConnection> getAllConnections() {
        return new HashSet<>(connections.values());
    }

    @Override
    public Map<TileWirelessBase, IGridConnection> getConnectionMap() {
        return Collections.unmodifiableMap(connections);
    }

    @Override
    public IGridConnection getConnection(TileWirelessBase other) {
        return connections.get(other);
    }

    @Override
    public boolean doLink(TileWirelessBase other) {
        if (!other.canAddLink() && !canAddLink()) return false;
        // this.customName = other.customName;
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
}
