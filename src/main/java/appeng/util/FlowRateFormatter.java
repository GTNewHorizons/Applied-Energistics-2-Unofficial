package appeng.util;

import net.minecraft.util.EnumChatFormatting;

import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.me.cache.ItemFlowGridCache.FlowRate;

public final class FlowRateFormatter {

    private FlowRateFormatter() {}

    private static String formatTotalMagnitude(final long value) {
        final long magnitude = Math.abs(value);

        if (magnitude < 1000) {
            return String.valueOf(magnitude);
        }

        return ReadableNumberConverter.INSTANCE.toWideReadableForm(magnitude);
    }

    public static String formatTotal(final long total) {
        if (total == 0) return "0";
        return (total < 0 ? "-" : "+") + formatTotalMagnitude(total);
    }

    private static String formatWindow(final int minutes) {
        return Math.max(1, minutes) + "m";
    }

    private static String formatInRate(final long in) {
        return EnumChatFormatting.GREEN + "+" + formatTotalMagnitude(in) + EnumChatFormatting.RESET;
    }

    private static String formatOutRate(final long out) {
        return EnumChatFormatting.RED + "-" + formatTotalMagnitude(out) + EnumChatFormatting.RESET;
    }

    public static String formatTotalForChat(final long total) {
        return (total >= 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.RED) + formatTotal(total)
                + EnumChatFormatting.RESET;
    }

    public static String formatTooltipLine(final FlowRate rate) {
        final String window = formatWindow(AEConfig.instance.itemFlowTrackingWindowMinutes);

        if (rate.in() == 0) {
            return ButtonToolTips.FlowRateTooltipShort.getLocal(window, formatOutRate(rate.out()));
        } else if (rate.out() == 0) {
            return ButtonToolTips.FlowRateTooltipShort.getLocal(window, formatInRate(rate.in()));
        } else {
            return ButtonToolTips.FlowRateTooltipFull.getLocal(
                    window,
                    formatInRate(rate.in()),
                    formatOutRate(rate.out()),
                    formatTotalForChat(rate.net()));
        }
    }
}
