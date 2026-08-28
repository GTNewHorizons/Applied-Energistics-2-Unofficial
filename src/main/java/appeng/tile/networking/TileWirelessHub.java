package appeng.tile.networking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableList;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;

public class TileWirelessHub extends TileWirelessBase {

    private final HashMap<DimensionalCoord, ActiveConnection> connections = new HashMap<>();

    public TileWirelessHub() {
        super(32);
    }

    @Override
    protected void addActiveConnection(TileWirelessBase target, IGridConnection connection) {
        final DimensionalCoord location = target.getLocation();
        if (connections.containsKey(location)) throw new IllegalStateException("Active connection already registered");

        connections.put(new DimensionalCoord(location), new ActiveConnection(target, connection));
    }

    @Override
    protected void removeActiveConnection(TileWirelessBase target) {
        connections.remove(target.getLocation());
    }

    @Override
    public List<TileWirelessBase> getConnectedTiles() {
        final List<TileWirelessBase> tiles = new ArrayList<>();
        for (ActiveConnection connection : connections.values()) {
            tiles.add(connection.target);
        }
        return ImmutableList.copyOf(tiles);
    }

    @Override
    public IGridConnection getConnection(TileWirelessBase target) {
        final ActiveConnection connection = connections.get(target.getLocation());
        if (connection == null || connection.target != target) return null;
        return connection.connection;
    }

    @Override
    public BindResult doLink(TileWirelessBase target) {
        return setupConnection(target);
    }

    @Override
    public void unlink(TileWirelessBase target) {
        if (hasLinkedTarget(target.getLocation())) {
            breakLink(target);
        }
    }

    @Override
    public void unlinkAll() {
        breakAllLinks();
    }

    @Override
    protected void tryRestoreConnection(Iterable<DimensionalCoord> linkedTargets) {
        for (DimensionalCoord target : linkedTargets) {
            TileWirelessBase tile = getTargetOrRemoveLink(target);
            if (tile == null) continue;
            if (!isConnectedTo(tile)) WireLessToolHelper.restoreConnection(tile, this, new MachineSource(this));
        }
    }

    private static class ActiveConnection {

        private final TileWirelessBase target;
        private final IGridConnection connection;

        private ActiveConnection(TileWirelessBase target, IGridConnection connection) {
            this.target = target;
            this.connection = connection;
        }
    }
}
