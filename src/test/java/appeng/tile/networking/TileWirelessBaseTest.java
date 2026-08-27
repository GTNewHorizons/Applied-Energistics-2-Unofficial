package appeng.tile.networking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import appeng.api.util.DimensionalCoord;

public class TileWirelessBaseTest {

    @Test
    public void onlyRestoresMissingConnections() {
        final TileWirelessHub hub = new TileWirelessHub();

        assertFalse(hub.shouldRestoreConnections(0));

        hub.addLinkedTarget(new DimensionalCoord(1, 2, 3, 0));

        assertTrue(hub.shouldRestoreConnections(0));
        assertFalse(hub.shouldRestoreConnections(1));
    }
}
