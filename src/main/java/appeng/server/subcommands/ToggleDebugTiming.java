package appeng.server.subcommands;

import appeng.core.AEConfig;
import appeng.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class ToggleDebugTiming  implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.ToggleDebugTiming";
    }
    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
         AEConfig.instance.debugLogTiming = !AEConfig.instance.debugLogTiming;
         sender.addChatMessage(new ChatComponentText("Debug timing is now " + (AEConfig.instance.debugLogTiming? "on" : "off")));
    }
}
