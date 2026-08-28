/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import java.util.List;

import com.google.common.collect.ImmutableList;

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
    protected void addActiveConnection(TileWirelessBase other, IGridConnection connection) {
        if (this.connection != null) breakAllActiveConnections();

        this.connection = connection;
        this.target = other;
    }

    @Override
    protected void removeActiveConnection(TileWirelessBase other) {
        if (target == other || target != null && target.getLocation().isEqual(other.getLocation())) {
            connection = null;
            target = null;
        }
    }

    @Override
    public List<TileWirelessBase> getConnectedTiles() {
        return target == null ? ImmutableList.of() : ImmutableList.of(target);
    }

    @Override
    public IGridConnection getConnection(TileWirelessBase other) {
        if (target == other) return connection;
        return null;
    }

    @Override
    public BindResult doLink(TileWirelessBase other) {
        this.unlinkAll();
        return setupConnection(other);
    }

    @Override
    public boolean canAddLink() {
        return true;
    }

    @Override
    public void unlink(TileWirelessBase other) {
        if (hasLinkedTarget(other.getLocation())) {
            this.breakLink(other);
        }
    }

    @Override
    public void unlinkAll() {
        this.breakAllLinks();
    }

    @Override
    protected void tryRestoreConnection(Iterable<DimensionalCoord> linkedTargets) {
        if (this.connection != null) return;

        for (DimensionalCoord target : linkedTargets) {
            final TileWirelessBase tile = getTargetOrRemoveLink(target);
            if (tile != null) WireLessToolHelper.restoreConnection(tile, this, new MachineSource(this));
            return;
        }
    }
}
