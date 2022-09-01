package appeng.server.subcommands;

import appeng.api.networking.IGridHost;
import appeng.me.Grid;
import appeng.server.ISubCommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

public class Profile implements ISubCommand {
    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.Profiler";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        if (args.length < 4) sender.addChatMessage(new ChatComponentTranslation("commands.ae2.Profiler"));
        try {
            int x = Integer.decode(args[1]);
            int y = Integer.decode(args[2]);
            int z = Integer.decode(args[3]);
            TileEntity tile;
            if (args.length > 4) {
                int dim = Integer.decode(args[4]);
                WorldServer ws = srv.worldServerForDimension(dim);
                if (ws == null) {
                    sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailedDim"));
                    return;
                }
                tile = ws.getTileEntity(x, y, z);
            } else tile = sender.getEntityWorld().getTileEntity(x, y, z);

            if (!(tile instanceof IGridHost) || ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN) == null) {
                sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailed"));
                return;
            }
            Grid grid = (Grid)
                    ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN).getGrid();
            if (grid == null) {
                sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerGridDown"));
                return;
            }
            if (!grid.isProfiling()) {
                sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerStart"));
                grid.startProfiling();
            } else {
                IChatComponent message = new ChatComponentTranslation("commands.ae2.ProfilerStop");
                message.appendText(String.format(" %d", grid.stopProfiling()));
                sender.addChatMessage(message);
            }
        } catch (NumberFormatException ex) {
            sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailed"));
            return;
        }
    }
}
