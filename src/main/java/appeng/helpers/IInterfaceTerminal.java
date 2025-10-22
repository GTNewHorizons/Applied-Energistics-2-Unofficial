package appeng.helpers;

import appeng.api.networking.security.IActionHost;
import appeng.core.localization.GuiText;

public interface IInterfaceTerminal extends IActionHost {

    boolean needsUpdate();

    default GuiText getName() {
        return GuiText.InterfaceTerminal;
    }
}
