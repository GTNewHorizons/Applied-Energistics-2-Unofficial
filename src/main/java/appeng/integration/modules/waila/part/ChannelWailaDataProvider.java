/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.integration.modules.waila.part;

import appeng.api.parts.IPart;
import appeng.core.localization.WailaText;
import appeng.parts.networking.PartCableSmart;
import appeng.parts.networking.PartDenseCable;
import appeng.parts.networking.PartUltraDenseCableSmart;
import gnu.trove.map.TObjectShortMap;
import gnu.trove.map.hash.TObjectShortHashMap;
import java.util.List;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Channel-information provider for WAILA
 *
 * @author thatsIch
 * @version rv2
 * @since rv2
 */
public final class ChannelWailaDataProvider extends BasePartWailaDataProvider {
    /**
     * Channel key used for the transferred {@link net.minecraft.nbt.NBTTagCompound}
     */
    private static final String ID_USED_CHANNELS = "usedChannels";

    /**
     * Used cache for channels if the channel was not transmitted through the server.
     * <p/>
     * This is useful, when a player just started to look at a tile and thus just requested the new information from the
     * server.
     * <p/>
     * The cache will be updated from the server.
     */
    private final TObjectShortMap<IPart> cache = new TObjectShortHashMap<>();

    /**
     * Adds the used and max channel to the tool tip
     *
     * @param part           being looked at part
     * @param currentToolTip current tool tip
     * @param accessor       wrapper for various world information
     * @param config         config to react to various settings
     * @return modified tool tip
     */
    @Override
    public List<String> getWailaBody(
            final IPart part,
            final List<String> currentToolTip,
            final IWailaDataAccessor accessor,
            final IWailaConfigHandler config) {
        if (part instanceof PartCableSmart || part instanceof PartDenseCable) {
            final short usedChannels = this.getUsedChannels(part, accessor.getNBTData());
            final int maxChannels =
                    ((part instanceof PartUltraDenseCableSmart) ? 128 : ((part instanceof PartDenseCable) ? 32 : 8));

            final String formattedToolTip = String.format(WailaText.Channels.getLocal(), usedChannels, maxChannels);
            currentToolTip.add(formattedToolTip);
        }

        return currentToolTip;
    }

    /**
     * Determines the source of the channel.
     * <p/>
     * If the client received information of the channels on the server, they are used, else if the cache contains a
     * previous stored value, this will be used. Default value is 0.
     *
     * @param part  part to be looked at
     * @param tag   tag maybe containing the channel information
     * @return used channels on the cable
     */
    private short getUsedChannels(final IPart part, final NBTTagCompound tag) {
        final short usedChannels;

        if (tag.hasKey(ID_USED_CHANNELS)) {
            usedChannels = tag.getShort(ID_USED_CHANNELS);
            this.cache.put(part, usedChannels);
        } else if (this.cache.containsKey(part)) {
            usedChannels = this.cache.get(part);
        } else {
            usedChannels = 0;
        }

        return usedChannels;
    }

    /**
     * Called on server to transfer information from server to client.
     * <p/>
     * If the part is a cable, it writes the channel information in the {@code #tag} using the {@code ID_USED_CHANNELS}
     * key.
     *
     * @param player player looking at the part
     * @param part   part being looked at
     * @param te     host of the part
     * @param tag    transferred tag which is send to the client
     * @param world  world of the part
     * @param x      x pos of the part
     * @param y      y pos of the part
     * @param z      z pos of the part
     * @return tag send to the client
     */
    @Override
    public NBTTagCompound getNBTData(
            final EntityPlayerMP player,
            final IPart part,
            final TileEntity te,
            final NBTTagCompound tag,
            final World world,
            final int x,
            final int y,
            final int z) {
        if (part instanceof PartCableSmart || part instanceof PartDenseCable) {
            final NBTTagCompound tempTag = new NBTTagCompound();

            part.writeToNBT(tempTag);

            if (tempTag.hasKey(ID_USED_CHANNELS)) {
                final short usedChannels = tempTag.getShort(ID_USED_CHANNELS);

                tag.setShort(ID_USED_CHANNELS, usedChannels);
            }
        }

        return tag;
    }
}
