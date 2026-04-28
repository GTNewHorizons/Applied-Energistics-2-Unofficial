package appeng.items.tools;

import appeng.api.config.WirelessToolType;

public class ToolSimpleWirelessKit extends ToolSuperWirelessKit {

    @Override
    public WirelessToolType getIdentity() {
        return WirelessToolType.Simple;
    }
}
