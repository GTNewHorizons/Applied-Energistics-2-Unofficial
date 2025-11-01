package appeng.api.parts;

import com.glodblock.github.api.registries.ILevelViewable;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.energy.IEnergyWatcherHost;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IIAEStackInventory;

@cpw.mods.fml.common.Optional.Interface(iface = "com.glodblock.github.api.registries.ILevelViewable", modid = "ae2fc")
public interface ILevelEmitter
        extends IEnergyWatcherHost, IStackWatcherHost, ICraftingWatcherHost, IMEMonitorHandlerReceiver<IAEStack<?>>,
        ICraftingProvider, IGridTickable, IUpgradeableHost, IIAEStackInventory, ILevelViewable {

    void setReportingValue(final long v);

    long getReportingValue();
}
