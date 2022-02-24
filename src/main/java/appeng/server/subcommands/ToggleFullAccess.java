package appeng.server.subcommands;

import appeng.me.cache.SecurityCache;
import appeng.server.ISubCommand;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class ToggleFullAccess  implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.ToggleFullAccess";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        if (sender instanceof EntityPlayerMP)
        {
            GameProfile profile = ((EntityPlayerMP) sender).getGameProfile();
            if (!SecurityCache.isPlayerOP(profile))
            {
                SecurityCache.registerOpPlayer(profile);
                sender.addChatMessage(new ChatComponentText("Player " + profile.getName() + " has full admin access now"));
            }
            else
            {
                SecurityCache.unregisterOpPlayer(profile);
                sender.addChatMessage(new ChatComponentText("Player " + profile.getName() + " has admin access revoked"));
            }
        }
        else
            sender.addChatMessage(new ChatComponentText("You are not on server?"));
    }
}
