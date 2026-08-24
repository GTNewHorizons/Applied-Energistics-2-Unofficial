/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.server.subcommands;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.core.AELog;
import appeng.me.cache.GridStorageCache;
import appeng.server.ISubCommand;

/**
 * Compares the storage monitors' incrementally maintained lists against a fresh scan of every storage handler, and
 * reports any entry whose count disagrees. Exists to catch drift in the incremental path of
 * {@link appeng.me.cache.NetworkMonitor}; a healthy grid always reports zero.
 */
public class VerifyStorage implements ISubCommand {

    private static final int MAX_REPORTED_EXAMPLES = 5;

    @Override
    public String getHelp(MinecraftServer srv) {
        return "commands.ae2.VerifyStorage";
    }

    @Override
    public void call(MinecraftServer srv, String[] args, ICommandSender sender) {
        if (args.length < 4) {
            sender.addChatMessage(new ChatComponentTranslation("commands.ae2.VerifyStorage"));
            return;
        }

        final IGrid grid;
        try {
            final int x = Integer.decode(args[1]);
            final int y = Integer.decode(args[2]);
            final int z = Integer.decode(args[3]);

            final TileEntity tile;
            if (args.length > 4) {
                final WorldServer ws = srv.worldServerForDimension(Integer.decode(args[4]));
                if (ws == null) {
                    sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailedDim"));
                    return;
                }
                tile = ws.getTileEntity(x, y, z);
            } else {
                tile = sender.getEntityWorld().getTileEntity(x, y, z);
            }

            if (!(tile instanceof IGridHost) || ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN) == null) {
                sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailed"));
                return;
            }

            grid = ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN).getGrid();
        } catch (final NumberFormatException ex) {
            sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerFailed"));
            return;
        }

        if (grid == null) {
            sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerGridDown"));
            return;
        }

        final IStorageGrid storage = grid.getCache(IStorageGrid.class);
        if (!(storage instanceof GridStorageCache cache)) {
            sender.addChatMessage(new ChatComponentTranslation("commands.ae2.ProfilerGridDown"));
            return;
        }

        final List<String> drift = cache.findCacheDrift();

        for (int i = 0; i < drift.size() && i < MAX_REPORTED_EXAMPLES; i++) {
            sender.addChatMessage(new ChatComponentText("drift: " + drift.get(i)));
            AELog.warn("VerifyStorage drift: %s", drift.get(i));
        }

        sender.addChatMessage(
                new ChatComponentTranslation(
                        drift.isEmpty() ? "commands.ae2.VerifyStorageClean" : "commands.ae2.VerifyStorageDrift")
                                .appendText(String.format(" %d", drift.size())));
    }
}
