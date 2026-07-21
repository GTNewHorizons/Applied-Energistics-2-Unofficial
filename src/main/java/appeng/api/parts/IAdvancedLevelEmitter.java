package appeng.api.parts;

import appeng.api.features.ILevelViewable;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IIAEStackInventory;

public interface IAdvancedLevelEmitter extends IStackWatcherHost, IMEMonitorHandlerReceiver<IAEStack<?>>, IGridTickable,
        IUpgradeableHost, IIAEStackInventory, ILevelViewable {

    int SLOT_COUNT = 6;

    long getReportingValue(int slot);

    void setReportingValue(int slot, long v);

    boolean isSlotActive(int slot);

    void setSlotActive(int slot, boolean active);

    boolean isSlotInverted(int slot);

    void setSlotInverted(int slot, boolean inverted);
}
