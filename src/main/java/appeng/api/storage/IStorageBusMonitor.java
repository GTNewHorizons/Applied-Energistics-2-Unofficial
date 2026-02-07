package appeng.api.storage;

import appeng.api.config.StorageFilter;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.data.IAEStack;

public interface IStorageBusMonitor<T extends IAEStack> extends IMEMonitor<T> {

    TickRateModulation onTick();

    // StorageFilter getMode();

    void setMode(final StorageFilter mode);

    void setActionSource(final BaseActionSource mySource);
}
