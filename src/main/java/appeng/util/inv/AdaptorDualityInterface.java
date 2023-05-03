package appeng.util.inv;

import net.minecraft.inventory.IInventory;

import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;

public class AdaptorDualityInterface extends AdaptorIInventory {

    public final IInterfaceHost interfaceHost;

    public AdaptorDualityInterface(IInventory s, IInterfaceHost interfaceHost) {
        super(s);
        this.interfaceHost = interfaceHost;
    }

    @Override
    public boolean containsItems() {
        DualityInterface dual = interfaceHost.getInterfaceDuality();
        boolean hasMEItems = false;
        if (dual.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0) {
            if (dual.getConfigManager().getSetting(Settings.ADVANCED_BLOCKING_MODE) == AdvancedBlockingMode.DEFAULT) {
                IItemList<IAEItemStack> itemList = dual.getItemInventory().getStorageList();
                // This works okay, it'll loop as much as (or even less than) a normal inventory because the iterator
                // hides empty slots or stacks of size 0
                for (IAEItemStack stack : itemList) {
                    if (!stack.getItem().getUnlocalizedName().equals("gt.integrated_circuit")) {
                        hasMEItems = true;
                        break;
                    }
                }
            } else {
                hasMEItems = !dual.getItemInventory().getStorageList().isEmpty();
            }
        }
        return hasMEItems || super.containsItems();
    }
}
