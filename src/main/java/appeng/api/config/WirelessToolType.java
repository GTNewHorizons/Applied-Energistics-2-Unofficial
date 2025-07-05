package appeng.api.config;

import appeng.core.localization.WirelessToolMessages;

public enum WirelessToolType {

    Simple,
    Advanced,
    Super;

    public String getLocal() {
        switch (this) {
            case Simple -> {
                return WirelessToolMessages.mode_simple.getLocal();
            }
            case Advanced -> {
                return WirelessToolMessages.mode_advanced.getLocal();
            }
            case Super -> {
                return WirelessToolMessages.mode_super.getLocal();
            }
        }
        return "";
    }
}
