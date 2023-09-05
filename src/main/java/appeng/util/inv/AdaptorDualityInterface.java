package appeng.util.inv;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.BlockingModeIgnoreList;
import appeng.util.item.AEItemStack;

public class AdaptorDualityInterface extends AdaptorIInventory {

    public final IInterfaceHost interfaceHost;

    public AdaptorDualityInterface(IInventory s, IInterfaceHost interfaceHost) {
        super(s);
        this.interfaceHost = interfaceHost;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded, InsertionMode insertionMode) {
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IMEMonitor<IAEItemStack> monitor = interfaceHost.getInterfaceDuality().getItemInventory();
        IAEItemStack result = monitor.injectItems(
                AEItemStack.create(toBeAdded),
                Actionable.MODULATE,
                interfaceHost.getInterfaceDuality().getActionSource());
        return result == null ? null : result.getItemStack();
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated, InsertionMode insertionMode) {
        // Ignore insertion mode since injecting into a ME system doesn't have this concept
        IMEMonitor<IAEItemStack> monitor = interfaceHost.getInterfaceDuality().getItemInventory();
        IAEItemStack result = monitor.injectItems(
                AEItemStack.create(toBeSimulated),
                Actionable.SIMULATE,
                interfaceHost.getInterfaceDuality().getActionSource());
        return result == null ? null : result.getItemStack();
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
                    if (!BlockingModeIgnoreList.isIgnored(stack.getItemStack())) {
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
