package appeng.container.implementations;

import appeng.container.AEBaseContainer;
import appeng.tile.spatial.TileSpatialNetworkRelay;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSpatialNetworkRelay extends AEBaseContainer {

    public ContainerSpatialNetworkRelay(final InventoryPlayer ip, final TileSpatialNetworkRelay te) {
        super(ip, te);

        this.bindPlayerInventory(ip, 0, 166 - /* height of player inventory */ 82);
    }

}
