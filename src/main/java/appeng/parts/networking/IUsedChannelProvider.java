package appeng.parts.networking;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.GridNode;

/**
 * Extended part that provides info about channel capacity and usage to probes like HWYLA and TheOneProbe.
 */
public interface IUsedChannelProvider extends IPart {

    /**
     * @return The number of channels carried on this cable. Purely for informational purposes.
     */
    default int getUsedChannelsInfo() {
        int howMany = 0;
        IGridNode node = this.getGridNode();
        if (node != null && node.isActive()) {
            for (var gc : node.getConnections()) {
                howMany = Math.max(gc.getUsedChannels(), howMany);
            }
        }
        return howMany;
    }

    /**
     * @return The number of channels that can be carried at most. Purely for informational purposes. -1 indicates there
     *         is no limit to the number of carried channels.
     */
    default int getMaxChannelsInfo() {
        var node = this.getGridNode();
        if (node instanceof GridNode gridNode) {
            if (!AEConfig.instance.isFeatureEnabled(AEFeature.Channels)) {
                return -1;
            }
            return gridNode.getMaxChannels();
        }
        return 0;
    }

}
