/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.block.networking;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.helpers.AEGlassMaterial;
import appeng.tile.networking.TileCreativeEnergyCell;

public class BlockCreativeEnergyCell extends AEBaseTileBlock {

    public BlockCreativeEnergyCell() {
        super(AEGlassMaterial.INSTANCE);
        this.setTileEntity(TileCreativeEnergyCell.class);
        this.setFeature(EnumSet.of(AEFeature.Creative));
    }

    @Override
    public void addInformation(ItemStack is, EntityPlayer player, List<String> lines, boolean advancedItemTooltips) {
        super.addInformation(is, player, lines, advancedItemTooltips);
        lines.add(StatCollector.translateToLocal("gui.tooltips.appliedenergistics2.BlockCreativeEnergyCell"));
    }
}
