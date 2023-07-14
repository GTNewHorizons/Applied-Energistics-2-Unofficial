/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync.packets;

import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.me.cache.CraftingGridCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftRequest extends AppEngPacket {

    private final long amount;
    private final boolean heldShift;

    private final CraftingMode craftingMode;

    // automatic.
    public PacketCraftRequest(final ByteBuf stream) {
        this.heldShift = stream.readBoolean();
        this.amount = stream.readLong();
        this.craftingMode = CraftingMode.values()[stream.readByte()];
    }

    public PacketCraftRequest(final int craftAmt, final boolean shift) {
        this(craftAmt, shift, CraftingMode.STANDARD);
    }

    public PacketCraftRequest(final int craftAmt, final boolean shift, final CraftingMode craftingMode) {
        this.amount = craftAmt;
        this.heldShift = shift;
        this.craftingMode = craftingMode;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeBoolean(shift);
        data.writeLong(this.amount);
        data.writeByte(craftingMode.ordinal());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftAmount cca) {
            final Object target = cca.getTarget();
            if (target instanceof IGridHost gh) {
                final IGridNode gn = gh.getGridNode(ForgeDirection.UNKNOWN);
                if (gn == null) {
                    return;
                }

                final IGrid g = gn.getGrid();
                if (g == null || cca.getItemToCraft() == null) {
                    return;
                }

                cca.getItemToCraft().setStackSize(this.amount);

                Future<ICraftingJob> futureJob = null;
                try {
                    final ICraftingGrid cg = g.getCache(ICraftingGrid.class);
                    if (cg instanceof CraftingGridCache cgc) {
                        futureJob = cgc.beginCraftingJob(
                                cca.getWorld(),
                                cca.getGrid(),
                                cca.getActionSrc(),
                                cca.getItemToCraft(),
                                this.craftingMode,
                                null);
                    } else {
                        futureJob = cg.beginCraftingJob(
                                cca.getWorld(),
                                cca.getGrid(),
                                cca.getActionSrc(),
                                cca.getItemToCraft(),
                                null);
                    }

                    final ContainerOpenContext context = cca.getOpenContext();
                    if (context != null) {
                        final TileEntity te = context.getTile();
                        cca.openConfirmationGUI(player, te);

                        if (player.openContainer instanceof ContainerCraftConfirm ccc) {
                            ccc.setAutoStart(this.heldShift);
                            ccc.setJob(futureJob);
                            cca.detectAndSendChanges();
                        }
                    }
                } catch (final Throwable e) {
                    if (futureJob != null) {
                        futureJob.cancel(true);
                    }
                    AELog.debug(e);
                }
            }
        }
    }
}
