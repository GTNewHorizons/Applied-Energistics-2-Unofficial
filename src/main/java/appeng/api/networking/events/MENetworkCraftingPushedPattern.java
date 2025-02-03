package appeng.api.networking.events;

import appeng.helpers.IInterfaceHost;

public class MENetworkCraftingPushedPattern extends MENetworkEvent {

    public final IInterfaceHost host;

    public MENetworkCraftingPushedPattern(final IInterfaceHost host) {
        this.host = host;
    }

}
