package appeng.api.config;

import appeng.core.localization.Localization;
import appeng.core.localization.WirelessMessages;

public enum AdvancedWirelessToolMode implements Localization {

    Binding,
    BindingLine,
    Queueing,
    QueueingLine;

    @Override
    public String getUnlocalized() {
        return switch (this) {
            case Binding -> WirelessMessages.mode_advanced_binding_activated.getUnlocalized();
            case Queueing -> WirelessMessages.mode_advanced_queueing_activated.getUnlocalized();
            case BindingLine -> WirelessMessages.mode_advanced_bindingLine_activated.getUnlocalized();
            case QueueingLine -> WirelessMessages.mode_advanced_queueingLine_activated.getUnlocalized();
        };
    }

    public boolean isQueueing() {
        return this == Queueing || this == QueueingLine;
    }

    public boolean isBinding() {
        return this == Binding || this == BindingLine;
    }

    /** @return "binding" or "queueing" */
    public String getMode() {
        return switch (this) {
            case Binding, BindingLine -> "binding";
            case Queueing, QueueingLine -> "queueing";
        };
    }
}
