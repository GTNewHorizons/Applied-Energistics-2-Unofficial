package appeng.helpers;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.energy.IEnergyWatcherHost;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.storage.IMEMonitorHandlerReceiver;

public interface ILevelEmitter extends IEnergyWatcherHost, IStackWatcherHost, ICraftingWatcherHost,
        IMEMonitorHandlerReceiver, ICraftingProvider, IGridTickable, IUpgradeableHost {

    void setReportingValue(final long v);

    long getReportingValue();
}
