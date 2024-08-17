package appeng.tile.misc;

import appeng.api.networking.GridFlags;
import appeng.tile.grid.AENetworkTile;

public class TilePatternOptimizationMatrix extends AENetworkTile {

    public TilePatternOptimizationMatrix() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

}
