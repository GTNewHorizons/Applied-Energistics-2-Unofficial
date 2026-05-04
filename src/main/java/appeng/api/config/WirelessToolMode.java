package appeng.api.config;

import appeng.core.localization.Localization;
import appeng.core.localization.WirelessMessages;

public enum WirelessToolMode implements Localization {

    Simple,
    Advanced,
    Super;

    @Override
    public String getUnlocalized() {
        return switch (this) {
            case Simple -> WirelessMessages.Simple.getUnlocalized();
            case Advanced -> WirelessMessages.Advanced.getUnlocalized();
            case Super -> WirelessMessages.Super.getUnlocalized();
        };
    }
}
