package appeng.util;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

import appeng.core.localization.ColorUtils;

public class ColorPickHelper {

    public static ColorResource selectColorFromThreshold(float threshold) {
        ColorResource color = null;
        if (threshold <= 25) {
            color = ColorUtils.craftConfirmPercent25;
        } else if (threshold <= 50) {
            color = ColorUtils.craftConfirmPercent50;
        } else if (threshold <= 75) {
            color = ColorUtils.craftConfirmPercent75;
        } else {
            color = ColorUtils.craftConfirmPercent100;
        }
        return color;
    }
}
