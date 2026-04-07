package appeng.util.inv;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.features.registries.BlockingModeIgnoreItemRegistry;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
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
    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        return addStackToMonitor(toBeAdded, Actionable.MODULATE);
    }

    @Override
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        return addStackToMonitor(toBeSimulated, Actionable.SIMULATE);
    }

    private IAEStack<?> addStackToMonitor(IAEStack<?> aes, Actionable act) {
        final DualityInterface dual = interfaceHost.getInterfaceDuality();
        final IMEMonitor monitor = dual.getMEMonitor(aes.getStackType());
        if (monitor == null) return aes;
        return monitor.injectItems(aes, act, dual.getActionSource());
    }

    @Override
    public boolean containsItems() {
        final DualityInterface dual = interfaceHost.getInterfaceDuality();
        if (dual.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0) {
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final IMEMonitor<?> monitor = dual.getMEMonitor(type);
                if (monitor != null) {
                    for (final IAEStack<?> aes : monitor.getStorageList()) {
                        if (aes instanceof AEItemStack ais) {
                            if (dual.getConfigManager().getSetting(Settings.ADVANCED_BLOCKING_MODE)
                                    == AdvancedBlockingMode.DEFAULT) {
                                if (!BlockingModeIgnoreItemRegistry.instance().isIgnored(ais.getItemStack())) {
                                    return true;
                                }
                            } else return true;
                        } else return true;
                    }
                }
            }
        }
        return super.containsItems();
    }
}
