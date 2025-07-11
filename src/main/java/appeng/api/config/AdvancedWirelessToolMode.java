package appeng.api.config;

import appeng.core.localization.WirelessToolMessages;

public enum AdvancedWirelessToolMode {

    Binding,
    BindingLine,
    Queueing,
    QueueingLine;

    public String getLocal() {
        switch (this) {
            case Binding -> {
                return WirelessToolMessages.mode_advanced_binding_activated.getLocal();
            }
            case Queueing -> {
                return WirelessToolMessages.mode_advanced_queueing_activated.getLocal();
            }
            case BindingLine -> {
                return WirelessToolMessages.mode_advanced_bindingLine_activated.getLocal();
            }
            case QueueingLine -> {
                return WirelessToolMessages.mode_advanced_queueingLine_activated.getLocal();
            }
        }
        return "";
    }

    public boolean isQueueing() {
        return this == Queueing || this == QueueingLine;
    }

    public boolean isBinding() {
        return this == Binding || this == BindingLine;
    }
}
