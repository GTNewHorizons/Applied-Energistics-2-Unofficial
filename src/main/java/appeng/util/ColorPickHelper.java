package appeng.util;

import appeng.core.localization.GuiColors;

public class ColorPickHelper {

    public static GuiColors selectColorFromThreshold(float threshold) {
        GuiColors color = null;
        if (threshold <= 25) {
            color = GuiColors.CraftConfirmPercent25;
        } else if (threshold <= 50) {
            color = GuiColors.CraftConfirmPercent50;
        } else if (threshold <= 75) {
            color = GuiColors.CraftConfirmPercent75;
        } else {
            color = GuiColors.CraftConfirmPercent100;
        }
        return color;
    }
}
