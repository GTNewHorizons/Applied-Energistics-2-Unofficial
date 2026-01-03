package appeng.client.gui.widgets;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import appeng.core.localization.Localization;

/**
 * Button that stores the quantity it represents so parsing the display string is unnecessary. The display string can be
 * customized through localization overrides that may include color codes.
 */
public class GuiQuantityButton extends GuiButton {

    private final int quantity;

    public GuiQuantityButton(final int id, final int x, final int y, final int width, final int height,
            final Localization localization, final int quantity, final String fallbackFormat) {
        super(id, x, y, width, height, resolveDisplayString(localization, quantity, fallbackFormat));
        this.quantity = quantity;
    }

    public int getQuantity() {
        return this.quantity;
    }

    private static String resolveDisplayString(Localization localization, int quantity, String fallbackFormat) {
        if (localization != null && StatCollector.canTranslate(localization.getUnlocalized())) {
            return localization.getLocal(quantity);
        }

        return String.format(fallbackFormat, quantity);
    }
}
