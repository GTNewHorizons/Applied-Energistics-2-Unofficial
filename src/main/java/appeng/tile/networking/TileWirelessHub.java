package appeng.tile.networking;

import static appeng.helpers.WireLessToolHelper.getAndCheckTile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;

public class TileWirelessHub extends TileWirelessBase {

    HashMap<TileWirelessBase, IGridConnection> connections = new HashMap<>();

    public TileWirelessHub() {
        super(32);
    }

    @Override
    protected void setDataConnections(TileWirelessBase target, IGridConnection connection) {
        if (connections.containsKey(target)) throw new IllegalStateException("Connection already set!");

        connections.put(target, connection);
    }

    @Override
    protected void removeDataConnections(TileWirelessBase target) {
        connections.remove(target);
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
    public IGridConnection getConnection(TileWirelessBase target) {
        return connections.get(target);
    }

    @Override
    public BindResult doLink(TileWirelessBase target) {
        return setupConnection(target);
    }

    @Override
    public void doUnlink(TileWirelessBase target) {
        breakConnection(target);
    }

    @Override
    public void doUnlink() {
        breakAllConnections();
    }

    @Override
    protected void tryRestoreConnection(List<DimensionalCoord> locList) {
        for (DimensionalCoord target : locList) {
            TileWirelessBase tile = getAndCheckTile(target, worldObj, null);
            if (tile == null) continue;
            if (WireLessToolHelper.performConnection(tile, this, new MachineSource(this)) == BindResult.SUCCESS)
                locList.remove(target);
        }
    }
}
