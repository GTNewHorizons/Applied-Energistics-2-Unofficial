package appeng.util.inv;

import net.minecraft.inventory.IInventory;

import appeng.api.config.Upgrades;
import appeng.helpers.DualityInterface;
import appeng.tile.misc.TileInterface;

public class AdaptorDualityInterface extends AdaptorIInventory {

    private final TileInterface tileInterface;

    public AdaptorDualityInterface(IInventory s, TileInterface tileInterface) {
        super(s);
        this.tileInterface = tileInterface;
    }

    @Override
    public boolean containsItems() {
        DualityInterface dual = tileInterface.getInterfaceDuality();
        boolean hasMEItems = false;
        if (dual.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0) {
            hasMEItems = !dual.getItemInventory().getStorageList().isEmpty();
        }
        return hasMEItems || super.containsItems();
    }
}
