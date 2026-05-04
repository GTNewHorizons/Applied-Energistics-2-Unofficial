package appeng.api.config;

import appeng.core.localization.Localization;
import appeng.core.localization.WirelessMessages;

public enum AdvancedWirelessToolMode implements Localization {

    Queueing,
    Binding,
    QueueingLine,
    BindingLine;

    @Override
    public String getUnlocalized() {
        return switch (this) {
            case Binding -> WirelessMessages.AdvancedBinding.getUnlocalized();
            case Queueing -> WirelessMessages.AdvancedQueueing.getUnlocalized();
            case BindingLine -> WirelessMessages.AdvancedBindingLine.getUnlocalized();
            case QueueingLine -> WirelessMessages.AdvancedQueueingLine.getUnlocalized();
        };
    }
}
