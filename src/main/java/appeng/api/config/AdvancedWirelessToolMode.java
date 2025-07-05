package appeng.api.config;

import appeng.core.localization.WirelessToolMessages;

public enum AdvancedWirelessToolMode {

    Binding,
    Queueing;

    public String getLocal() {
        switch (this) {
            case Binding -> {
                return WirelessToolMessages.mode_advanced_binding_activated.getLocal();
            }
            case Queueing -> {
                return WirelessToolMessages.mode_advanced_queueing_activated.getLocal();
            }
        }
        return "";
    }
}
