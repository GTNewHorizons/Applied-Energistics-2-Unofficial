package appeng.api.config;

import appeng.core.localization.Localization;
import appeng.core.localization.WirelessMessages;

public enum WirelessToolType implements Localization {

    Simple,
    Advanced,
    Super;

    @Override
    public String getUnlocalized() {
        return switch (this) {
            case Simple -> WirelessMessages.mode_simple.getUnlocalized();
            case Advanced -> WirelessMessages.mode_advanced.getUnlocalized();
            case Super -> WirelessMessages.mode_super.getUnlocalized();
        };
    }
}
