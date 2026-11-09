package appeng.api.parts;

import appeng.api.features.ILevelViewable;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.tile.inventory.IIAEStackInventory;

public interface IAdvancedLevelEmitter
        extends IStackWatcherHost, IGridTickable, IUpgradeableHost, IIAEStackInventory, ILevelViewable {

    int SLOT_COUNT = 6;

    long getReportingValue(int slot);

    void setReportingValue(int slot, long v);

    /** Returns the amount of the slot's configured stack currently stored in the network. */
    long getStoredAmount(int slot);

    boolean isSlotActive(int slot);

    void setSlotActive(int slot, boolean active);

    boolean isSlotInverted(int slot);

    void setSlotInverted(int slot, boolean inverted);
}
