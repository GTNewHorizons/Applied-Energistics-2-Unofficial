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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.util.ItemSearchDTO;
import appeng.client.render.highlighter.StoragePosHighlighter;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketHighlightBlockStorage extends AppEngPacket {

    List<ItemSearchDTO> coords;

    // automatic.
    public PacketHighlightBlockStorage(final ByteBuf stream) throws IOException {
        final int size = stream.readInt();
        final byte[] tagBytes = new byte[size];
        stream.readBytes(tagBytes);
        final ByteArrayInputStream byteArray = new ByteArrayInputStream(tagBytes);
        NBTTagCompound nbt = CompressedStreamTools.read(new DataInputStream(byteArray));
        coords = ItemSearchDTO.readAsListFromNBT(nbt);
    }

    // api
    public PacketHighlightBlockStorage(List<ItemSearchDTO> coords) throws IOException {
        this.coords = coords;

        final ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(this.getPacketID());
        NBTTagCompound tag = new NBTTagCompound();
        ItemSearchDTO.writeListToNBT(tag, coords);

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream data = new DataOutputStream(bytes);
        CompressedStreamTools.write(tag, data);

        final byte[] tagBytes = bytes.toByteArray();
        final int size = tagBytes.length;

        buffer.writeInt(size);
        buffer.writeBytes(tagBytes);

        this.configureWrite(buffer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        try {
            if (Platform.isClient()) {
                StoragePosHighlighter.highlightStorage(
                        player,
                        coords,
                        PlayerMessages.StorageHighlighted.getUnlocalized(),
                        PlayerMessages.StorageInOtherDim.getUnlocalized());
            }
        } catch (final Exception ignored) {}
    }
}
