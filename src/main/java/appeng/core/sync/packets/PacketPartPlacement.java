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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPartHost;
import appeng.api.util.DimensionalCoord;
import appeng.core.CommonHelper;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.parts.PartPlacement;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPartPlacement extends AppEngPacket {

    private int x;
    private int y;
    private int z;
    private ForgeDirection face;
    private boolean isFacade;

    // automatic.
    public PacketPartPlacement(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
        this.face = ForgeDirection.getOrientation(stream.readByte());
        this.isFacade = stream.readBoolean();
    }

    // api
    public PacketPartPlacement(final int x, final int y, final int z, final ForgeDirection face,
            final boolean isFacade) {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeByte(face.ordinal());
        data.writeBoolean(isFacade);

        this.configureWrite(data);
    }

    private PacketPartPlacement(DimensionalCoord loc, final ForgeDirection face, final boolean isFacade) {
        this(loc.x, loc.y, loc.z, face, isFacade);
    }

    public PacketPartPlacement(IPartHost host, final ForgeDirection face, final boolean isFacade) {
        this(host.getLocation(), face, isFacade);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final EntityPlayerMP sender = (EntityPlayerMP) player;
        CommonHelper.proxy.updateRenderMode(sender);
        process(sender);
        CommonHelper.proxy.updateRenderMode(null);
    }

    private void process(final EntityPlayerMP player) {
        ItemStack held = player.getHeldItem();
        if (held == null) {
            resync(player);
            return;
        }
        World world = player.worldObj;
        TileEntity te = world.getTileEntity(x, y, z);
        if (isFacade) {
            IPartHost host = PartPlacement.getExistingHost(te);
            if (!PartPlacement.facadeLogic(held, player, face, host)) {
                resync(player);
            }
        } else {
            IPartHost host = PartPlacement.getOrCreateHost(te, player, face.ordinal());
            if (!PartPlacement.tryPlace(held, player, world, x, y, z, face, host)) {
                resync(player);
            }
        }
    }

    private void resync(final EntityPlayerMP player) {
        player.worldObj.markBlockForUpdate(x, y, z);
        player.worldObj.markBlockForUpdate(x + face.offsetX, y + face.offsetY, z + face.offsetZ);
        player.sendContainerToPlayer(player.inventoryContainer);
    }
}
