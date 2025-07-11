package appeng.core.localization;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

public enum WirelessToolMessages {

    name,
    security_player,
    dimensionMismatch,
    clear,
    bound,
    bound_advanced_filled,
    bound_super,
    bound_super_failed,
    connected,
    disconnected,
    invalidTarget,
    failed,
    empty,
    set,
    mode,
    mode_simple,
    mode_simple_empty,
    mode_simple_bound,
    targethubfull,
    otherhubfull,
    mode_advanced,
    mode_advanced_next,
    mode_advanced_extra,
    mode_advanced_queueing,
    mode_advanced_binding,
    mode_advanced_queueing_activated,
    mode_advanced_queueingLine_activated,
    mode_advanced_binding_activated,
    mode_advanced_bindingLine_activated,
    mode_advanced_queueing_empty,
    mode_advanced_binding_empty,
    mode_advanced_queueing_notempty,
    mode_advanced_binding_notempty,
    mode_advanced_noconnectors,
    mode_advanced_queued,
    mode_advanced_binding_hubqols,
    mode_advanced_queueing_hubqols,
    mode_advanced_queueing_hub,
    mode_advanced_binding_hub,
    mode_advanced_queueing_targethubfull,
    mode_super,
    mode_super_networklist,
    mode_super_network,
    mode_super_networklistempty;

    private final String formatedName;

    WirelessToolMessages() {
        this.formatedName = this.name().replace("_", ".");
    }

    public IChatComponent toChat() {
        return new ChatComponentTranslation(this.getUnlocalized());
    }

    public IChatComponent toChat(Object... args) {
        return new ChatComponentTranslation(this.getUnlocalized(), args);
    }

    public String getLocal() {
        return StatCollector.translateToLocal(this.getUnlocalized());
    }

    public String getLocal(Object... args) {
        return StatCollector.translateToLocalFormatted(this.getUnlocalized(), args);
    }

    public String getUnlocalized() {
        return "item.appliedenergistics2.ToolSuperWirelessKit." + this.formatedName;
    }
}
