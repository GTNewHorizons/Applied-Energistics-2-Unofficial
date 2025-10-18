package appeng.helpers;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.util.AECableType;

public class WirelessInterfaceTerminalGuiObject implements IInterfaceTerminal, IGuiItemObject {

    private boolean needsUpdate;
    private final IGridNode node;
    private final ItemStack is;

    public WirelessInterfaceTerminalGuiObject(final IWirelessTermHandler wh, final ItemStack is) {
        this.is = is;
        String encryptionKey = wh.getEncryptionKey(is);
        ILocatable obj = null;

        try {
            final long encKey = Long.parseLong(encryptionKey);
            obj = AEApi.instance().registries().locatable().getLocatableBy(encKey);
        } catch (final NumberFormatException err) {
            // :P
        }

        if (obj instanceof IGridHost gh) {
            this.node = gh.getGridNode(ForgeDirection.UNKNOWN);
        } else {
            this.node = null;
        }
    }

    @Override
    public IGridNode getActionableNode() {
        return this.node;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.node;
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return null;
    }

    @Override
    public void securityBreak() {}

    @Override
    public boolean needsUpdate() {
        boolean ret = needsUpdate;

        needsUpdate = false;
        return ret;
    }

    @MENetworkEventSubscribe
    public void onNetworkBootingChanged(MENetworkBootingStatusChange event) {
        if (!event.isBooting) {
            this.needsUpdate = true;
        }
    }

    @Override
    public ItemStack getItemStack() {
        return this.is;
    }
}
