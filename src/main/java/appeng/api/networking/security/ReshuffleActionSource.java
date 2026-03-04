/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.api.networking.security;

import net.minecraft.entity.player.EntityPlayer;

public class ReshuffleActionSource extends MachineSource {

    public final EntityPlayer player;

    public ReshuffleActionSource(final EntityPlayer player, final IActionHost via) {
        super(via);
        this.player = player;
    }

    @Override
    public String toString() {
        return "ReshuffleActionSource[player=" + (player != null ? player.getCommandSenderName() : "null")
                + ", via="
                + (via != null ? via.getClass().getSimpleName() : "null")
                + "]";
    }
}
