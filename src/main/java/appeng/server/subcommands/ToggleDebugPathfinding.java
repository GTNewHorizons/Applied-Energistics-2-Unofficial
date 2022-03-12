package appeng.server.subcommands;

import appeng.core.AEConfig;
import appeng.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class ToggleDebugPathfinding implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.ToggleDebugPathfinding";
    }
    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        AEConfig.instance.debugPathFinding = !AEConfig.instance.debugPathFinding;
        sender.addChatMessage(new ChatComponentText("Logging pathfinding is now " + (AEConfig.instance.debugPathFinding? "on" : "off")));
    }
}
