package appeng.util;

import appeng.core.localization.Localization;

public enum ScheduledReason implements Localization {

    UNDEFINED,
    SOMETHING_STUCK,
    BLOCKING_MODE,
    LOCK_MODE,
    NO_TARGET,
    NOT_ENOUGH_INGREDIENTS,
    SAME_NETWORK,
    UNSUPPORTED_STACK;

    @Override
    public String getUnlocalized() {
        return "gui.tooltips.appliedenergistics2.scheduledreason." + this;
    }
}
