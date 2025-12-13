/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.features.registries;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTermRegistry;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IMachineSet;
import appeng.api.util.DimensionalCoord;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.powered.ToolWirelessTerminal;
import appeng.tile.networking.TileWireless;
import appeng.util.Platform;

public final class WirelessRegistry implements IWirelessTermRegistry {

    private final List<IWirelessTermHandler> handlers;

    public WirelessRegistry() {
        this.handlers = new ArrayList<>();
    }

    @Override
    public void registerWirelessHandler(final IWirelessTermHandler handler) {
        if (handler != null) {
            this.handlers.add(handler);
        }
    }

    @Override
    public boolean isWirelessTerminal(final ItemStack is) {
        for (final IWirelessTermHandler h : this.handlers) {
            if (h.canHandle(is)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IWirelessTermHandler getWirelessTerminalHandler(final ItemStack is) {
        for (final IWirelessTermHandler h : this.handlers) {
            if (h.canHandle(is)) {
                return h;
            }
        }
        return null;
    }

    private static boolean testWap(final IWirelessAccessPoint wap, final EntityPlayer player) {
        double rangeLimit = wap.getRange();
        rangeLimit *= rangeLimit;

        final DimensionalCoord dc = wap.getLocation();

        if (dc.getWorld() == player.worldObj) {
            final double offX = dc.x - player.posX;
            final double offY = dc.y - player.posY;
            final double offZ = dc.z - player.posZ;

            final double r = offX * offX + offY * offY + offZ * offZ;
            return r < rangeLimit;
        }
        return false;
    }

    private static boolean checkRange(final EntityPlayer player, final ILocatable locatable,
            final boolean infiniteRange) {
        if (locatable instanceof IGridHost gridHost) {
            final IGridNode node = gridHost.getGridNode(ForgeDirection.UNKNOWN);
            if (node == null) return false;

            IGrid grid = node.getGrid();
            if (grid == null) return false;

            final IMachineSet tw = grid.getMachines(TileWireless.class);
            for (final IGridNode n : tw) {
                final IWirelessAccessPoint wap = (IWirelessAccessPoint) n.getMachine();
                if (wap.isActive() && (infiniteRange || testWap(wap, player))) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean performCheck(final ItemStack item, final EntityPlayer player) {
        if (Platform.isClient()) {
            return false;
        }

        final IWirelessTermHandler handler = this.getWirelessTerminalHandler(item);
        if (handler == null) {
            player.addChatMessage(PlayerMessages.DeviceNotWirelessTerminal.toChat());
            return false;
        }

        final String unparsedKey = handler.getEncryptionKey(item);
        if (unparsedKey.isEmpty()) {
            player.addChatMessage(PlayerMessages.DeviceNotLinked.toChat());
            return false;
        }

        final long parsedKey = Long.parseLong(unparsedKey);
        final ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(parsedKey);
        if (securityStation == null) {
            player.addChatMessage(PlayerMessages.StationCanNotBeLocated.toChat());
            return false;
        }

        if (!checkRange(player, securityStation, handler.hasInfinityRange(item))) {
            player.addChatMessage(PlayerMessages.OutOfRange.toChat());
            return false;
        }

        if (!handler.hasInfinityPower(item) && !handler.hasPower(player, 0.5, item)) {
            player.addChatMessage(PlayerMessages.DeviceNotPowered.toChat());
            return false;
        }

        return true;
    }

    @Override
    public void openWirelessTerminalGui(final ItemStack item, final World w, final EntityPlayer player) {
        if (performCheck(item, player)) ((ToolWirelessTerminal) item.getItem()).openGui(item, w, player, null);
    }
}
