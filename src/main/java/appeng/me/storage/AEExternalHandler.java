/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.tiles.ITileStorageMonitorable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IExternalStorageHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.misc.TileCondenser;

public class AEExternalHandler implements IExternalStorageHandler {

    @Override
    public boolean canHandle(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource mySrc) {
        if (te instanceof ITileStorageMonitorable)
            return ((ITileStorageMonitorable) te).getMonitorable(d, mySrc) != null;
        else return te instanceof TileCondenser && (type == ITEM_STACK_TYPE || type == FLUID_STACK_TYPE);
    }

    public IMEInventory getInventory(TileEntity te, ForgeDirection d, IAEStackType<?> type, BaseActionSource src) {
        if (te instanceof TileCondenser) {
            if (type == ITEM_STACK_TYPE) {
                return new VoidItemInventory((TileCondenser) te);
            } else if (type == FLUID_STACK_TYPE) {
                return new VoidFluidInventory((TileCondenser) te);
            }
        }

        if (te instanceof ITileStorageMonitorable iface) {
            return iface.getMonitorable(d, src).getMEMonitor(type);
        }

        return null;
    }
}
