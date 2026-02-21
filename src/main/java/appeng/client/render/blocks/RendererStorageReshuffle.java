/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.render.blocks;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.misc.BlockStorageReshuffle;
import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.misc.TileStorageReshuffle;

public class RendererStorageReshuffle extends BaseBlockRender<BlockStorageReshuffle, TileStorageReshuffle> {

    public RendererStorageReshuffle() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(final BlockStorageReshuffle block, final IBlockAccess world, final int x, final int y,
            final int z, final RenderBlocks renderer) {
        final TileStorageReshuffle tile = block.getTileEntity(world, x, y, z);

        if (tile == null) {
            return false;
        }

        this.preRenderInWorld(block, world, x, y, z, renderer);

        final ForgeDirection forward = tile.getForward();
        final ForgeDirection up = tile.getUp();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            IIcon icon = getIconForSide(tile, side, forward, up);
            if (icon != null) {
                renderer.overrideBlockTexture = icon;
                this.renderFace(x, y, z, block, icon, renderer, side);
            }
        }

        renderer.overrideBlockTexture = null;
        this.postRenderInWorld(renderer);

        return true;
    }

    private IIcon getIconForSide(TileStorageReshuffle tile, ForgeDirection side, ForgeDirection forward,
            ForgeDirection up) {
        if (side == forward) {
            return getFrontIcon(tile);
        }

        if (side == forward.getOpposite()) {
            return ExtraBlockTextures.BlockReshuffleBack.getIcon();
        }

        if (side == up) {
            return ExtraBlockTextures.BlockReshuffleTop.getIcon();
        }

        if (side == up.getOpposite()) {
            return ExtraBlockTextures.BlockReshuffleBottom.getIcon();
        }

        return ExtraBlockTextures.BlockReshuffleSide.getIcon();
    }

    private IIcon getFrontIcon(TileStorageReshuffle tile) {
        return tile.isReshuffleRunning() ? ExtraBlockTextures.BlockReshuffleFrontAllActive.getIcon()
                : ExtraBlockTextures.BlockReshuffleFrontAll.getIcon();
    }
}
