package appeng.api.config;

import appeng.core.localization.Localization;
import appeng.core.localization.WirelessMessages;

public enum AdvancedWirelessToolMode implements Localization {

    Queueing,
    Binding;

    @Override
    public String getUnlocalized() {
        return switch (this) {
            case Binding -> WirelessMessages.AdvancedBinding.getUnlocalized();
            case Queueing -> WirelessMessages.AdvancedQueueing.getUnlocalized();
        };
    }
}
