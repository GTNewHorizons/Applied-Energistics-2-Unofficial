package appeng.api.parts;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IOreFilterable;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.integration.IntegrationType;
import appeng.me.storage.MEInventoryHandler;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.transformer.annotations.Integration.Interface;
import appeng.util.IConfigManagerHost;
import buildcraft.api.transport.IPipeConnection;

@Interface(iname = IntegrationType.BuildCraftTransport, iface = "buildcraft.api.transport.IPipeConnection")
public interface IStorageBus
        extends IGridTickable, ICellContainer, IMEMonitorHandlerReceiver<IAEStack<?>>, IPipeConnection, IPriorityHost,
        IOreFilterable, IConfigManagerHost, IUpgradeableHost, IIAEStackInventory, IPrimaryGuiIconProvider {

    boolean needSyncGUI();

    void setNeedSyncGUI(boolean needSyncGUI);

    MEInventoryHandler<?> getInternalHandler();

    IAEStackType<?> getStackType();

    default IItemList getItemList() {
        return getStackType().createList();
    }
}
