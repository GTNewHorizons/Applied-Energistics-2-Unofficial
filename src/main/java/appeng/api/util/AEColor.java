/*
 * The MIT License (MIT) Copyright (c) 2013 AlgorithmX2 Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.util;

import java.util.Arrays;
import java.util.List;

import net.minecraft.util.StatCollector;

import appeng.core.localization.ColorUtils;
import appeng.core.localization.Localization;

/**
 * List of all colors supported by AE, their names, and various colors for display.
 * <p>
 * Should be the same order as Dyes, excluding Transparent.
 */
public enum AEColor implements Localization {

    // spotless:off
    White("gui.appliedenergistics2.White",
        ColorUtils.aeColorWhite_blackVariant.getColor(),
        ColorUtils.aeColorWhite_mediumVariant.getColor(),
        ColorUtils.aeColorWhite_whiteVariant.getColor(),
        ColorUtils.aeColorWhite_driveVariant.getColor()
        ),

    Orange("gui.appliedenergistics2.Orange",
        ColorUtils.aeColorOrange_blackVariant.getColor(),
        ColorUtils.aeColorOrange_mediumVariant.getColor(),
        ColorUtils.aeColorOrange_whiteVariant.getColor(),
        ColorUtils.aeColorOrange_driveVariant.getColor()
        ),

    Magenta("gui.appliedenergistics2.Magenta",
        ColorUtils.aeColorMagenta_blackVariant.getColor(),
        ColorUtils.aeColorMagenta_mediumVariant.getColor(),
        ColorUtils.aeColorMagenta_whiteVariant.getColor(),
        ColorUtils.aeColorMagenta_driveVariant.getColor()
        ),

    LightBlue("gui.appliedenergistics2.LightBlue",
        ColorUtils.aeColorLightBlue_blackVariant.getColor(),
        ColorUtils.aeColorLightBlue_mediumVariant.getColor(),
        ColorUtils.aeColorLightBlue_whiteVariant.getColor(),
        ColorUtils.aeColorLightBlue_driveVariant.getColor()
        ),

    Yellow("gui.appliedenergistics2.Yellow",
        ColorUtils.aeColorYellow_blackVariant.getColor(),
        ColorUtils.aeColorYellow_mediumVariant.getColor(),
        ColorUtils.aeColorYellow_whiteVariant.getColor(),
        ColorUtils.aeColorYellow_driveVariant.getColor()
        ),

    Lime("gui.appliedenergistics2.Lime",
        ColorUtils.aeColorLime_blackVariant.getColor(),
        ColorUtils.aeColorLime_mediumVariant.getColor(),
        ColorUtils.aeColorLime_whiteVariant.getColor(),
        ColorUtils.aeColorLime_driveVariant.getColor()
        ),

    Pink("gui.appliedenergistics2.Pink",
        ColorUtils.aeColorPink_blackVariant.getColor(),
        ColorUtils.aeColorPink_mediumVariant.getColor(),
        ColorUtils.aeColorPink_whiteVariant.getColor(),
        ColorUtils.aeColorPink_driveVariant.getColor()
        ),

    Gray("gui.appliedenergistics2.Gray",
        ColorUtils.aeColorGray_blackVariant.getColor(),
        ColorUtils.aeColorGray_mediumVariant.getColor(),
        ColorUtils.aeColorGray_whiteVariant.getColor(),
        ColorUtils.aeColorGray_driveVariant.getColor()
        ),

    LightGray("gui.appliedenergistics2.LightGray",
        ColorUtils.aeColorLightGray_blackVariant.getColor(),
        ColorUtils.aeColorLightGray_mediumVariant.getColor(),
        ColorUtils.aeColorLightGray_whiteVariant.getColor(),
        ColorUtils.aeColorLightGray_driveVariant.getColor()
        ),

    Cyan("gui.appliedenergistics2.Cyan",
        ColorUtils.aeColorCyan_blackVariant.getColor(),
        ColorUtils.aeColorCyan_mediumVariant.getColor(),
        ColorUtils.aeColorCyan_whiteVariant.getColor(),
        ColorUtils.aeColorCyan_driveVariant.getColor()
        ),

    Purple("gui.appliedenergistics2.Purple",
        ColorUtils.aeColorPurple_blackVariant.getColor(),
        ColorUtils.aeColorPurple_mediumVariant.getColor(),
        ColorUtils.aeColorPurple_whiteVariant.getColor(),
        ColorUtils.aeColorPurple_driveVariant.getColor()
        ),

    Blue("gui.appliedenergistics2.Blue",
        ColorUtils.aeColorBlue_blackVariant.getColor(),
        ColorUtils.aeColorBlue_mediumVariant.getColor(),
        ColorUtils.aeColorBlue_whiteVariant.getColor(),
        ColorUtils.aeColorBlue_driveVariant.getColor()
        ),

    Brown("gui.appliedenergistics2.Brown",
        ColorUtils.aeColorBrown_blackVariant.getColor(),
        ColorUtils.aeColorBrown_mediumVariant.getColor(),
        ColorUtils.aeColorBrown_whiteVariant.getColor(),
        ColorUtils.aeColorBrown_driveVariant.getColor()
        ),

    Green("gui.appliedenergistics2.Green",
        ColorUtils.aeColorGreen_blackVariant.getColor(),
        ColorUtils.aeColorGreen_mediumVariant.getColor(),
        ColorUtils.aeColorGreen_whiteVariant.getColor(),
        ColorUtils.aeColorGreen_driveVariant.getColor()
        ),

    Red("gui.appliedenergistics2.Red",
        ColorUtils.aeColorRed_blackVariant.getColor(),
        ColorUtils.aeColorRed_mediumVariant.getColor(),
        ColorUtils.aeColorRed_whiteVariant.getColor(),
        ColorUtils.aeColorRed_driveVariant.getColor()
        ),

    Black("gui.appliedenergistics2.Black",
        ColorUtils.aeColorBlack_blackVariant.getColor(),
        ColorUtils.aeColorBlack_mediumVariant.getColor(),
        ColorUtils.aeColorBlack_whiteVariant.getColor(),
        ColorUtils.aeColorBlack_driveVariant.getColor()
        ),

    Transparent("gui.appliedenergistics2.Fluix",
        ColorUtils.aeColorFluix_blackVariant.getColor(),
        ColorUtils.aeColorFluix_mediumVariant.getColor(),
        ColorUtils.aeColorFluix_whiteVariant.getColor(),
        ColorUtils.aeColorFluix_driveVariant.getColor()
        );
    // spotless:on
    public static final AEColor[] VALUES = values();

    public static final List<AEColor> VALID_COLORS = Arrays.asList(
            White,
            Orange,
            Magenta,
            LightBlue,
            Yellow,
            Lime,
            Pink,
            Gray,
            LightGray,
            Cyan,
            Purple,
            Blue,
            Brown,
            Green,
            Red,
            Black);

    /**
     * Unlocalized name for color.
     */
    public final String unlocalizedName;

    /**
     * Darkest Variant of the color, nearly black; as a RGB HEX Integer
     */
    public final int blackVariant;

    /**
     * The Variant of the color that is used to represent the color normally; as a RGB HEX Integer
     */
    public final int mediumVariant;

    /**
     * Lightest Variant of the color, nearly white; as a RGB HEX Integer
     */
    public final int whiteVariant;

    /**
     * Variant of the color used when rendering drives.
     */
    public final int driveVariant;

    AEColor(final String unlocalizedName, final int blackHex, final int medHex, final int whiteHex,
            final int driveHex) {
        this.unlocalizedName = unlocalizedName;
        this.blackVariant = blackHex;
        this.mediumVariant = medHex;
        this.whiteVariant = whiteHex;
        this.driveVariant = driveHex;
    }

    /**
     * Logic to see which colors match each other.. special handle for Transparent
     */
    public boolean matches(final AEColor color) {
        return this == Transparent || color == Transparent || this == color;
    }

    public String getUnlocalized() {
        return unlocalizedName;
    }

    @Override
    public String toString() {
        return StatCollector.translateToLocal(this.unlocalizedName);
    }

    public static AEColor fromOrdinal(int ordinal) {
        return AEColor.VALUES[ordinal];
    }
}
