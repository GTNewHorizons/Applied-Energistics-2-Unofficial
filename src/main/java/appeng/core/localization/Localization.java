package appeng.core.localization;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

public interface Localization {

    default IChatComponent toChat() {
        return new ChatComponentTranslation(getUnlocalized());
    }

    default IChatComponent toChat(Object... args) {
        return new ChatComponentTranslation(getUnlocalized(), args);
    }

    default IChatComponent toChat(EnumChatFormatting format, Object... args) {
        return new ChatComponentTranslation(getUnlocalized(), args).setChatStyle(new ChatStyle().setColor(format));
    }

    default String getLocal() {
        return StatCollector.translateToLocal(this.getUnlocalized());
    }

    default String getLocal(EnumChatFormatting format) {
        return format + StatCollector.translateToLocal(this.getUnlocalized());
    }

    default String getLocal(Object... args) {
        return StatCollector.translateToLocalFormatted(getUnlocalized(), args);
    }

    default String getLocal(EnumChatFormatting format, Object... args) {
        return format + StatCollector.translateToLocalFormatted(getUnlocalized(), args);
    }

    String getUnlocalized();
}
