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

    public static GuiColors selectColorFromScheduledReason(ScheduledReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case SOMETHING_STUCK -> GuiColors.CraftingScheduledSomethingStuck;
            case BLOCKING_MODE -> GuiColors.CraftingScheduledBlockingMode;
            case LOCK_MODE -> GuiColors.CraftingScheduledLockMode;
            case NO_TARGET -> GuiColors.CraftingScheduledSomethingStuck;
            case NOT_ENOUGH_INGREDIENTS -> GuiColors.CraftingScheduledNotEnoughIngredients;
            case SAME_NETWORK -> GuiColors.CraftingScheduledSomethingStuck;
            case UNSUPPORTED_STACK -> GuiColors.CraftingScheduledSomethingStuck;
            case UNDEFINED -> null;
        };
    }
}
