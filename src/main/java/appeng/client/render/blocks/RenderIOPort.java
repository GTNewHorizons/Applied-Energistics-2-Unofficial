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

import java.util.EnumSet;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.AEColor;
import appeng.block.storage.BlockIOPort;
import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.storage.TileIOPort;
import appeng.util.Platform;

public class RenderIOPort extends BaseBlockRender<BlockIOPort, TileIOPort> {

    public RenderIOPort() {
        super(false, 0);
    }

    @Override
    public void renderInventory(final BlockIOPort block, final ItemStack is, final RenderBlocks renderer,
            final ItemRenderType type, final Object[] obj) {
        renderer.overrideBlockTexture = ExtraBlockTextures.getMissing();
        this.renderInvBlock(EnumSet.of(ForgeDirection.SOUTH), block, is, Tessellator.instance, 0x000000, renderer);

        renderer.overrideBlockTexture = null;
        super.renderInventory(block, is, renderer, type, obj);
    }

    @Override
    public boolean renderInWorld(final BlockIOPort imb, final IBlockAccess world, final int x, final int y, final int z,
            final RenderBlocks renderer) {
        final TileIOPort sp = imb.getTileEntity(world, x, y, z);
        if (sp == null) {
            return false;
        }
        final ForgeDirection up = sp.getUp();
        final ForgeDirection forward = sp.getForward();
        final ForgeDirection west = Platform.crossProduct(forward, up);
        final ForgeDirection east = Platform.crossProduct(up, forward);
        final ForgeDirection backward = Platform.crossProduct(up, east);

        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);

        int iopColor = sp.getColor().driveVariant;
        float iopRed = (float) (iopColor >> 16 & 255) / 255.0F;
        float iopGreen = (float) (iopColor >> 8 & 255) / 255.0F;
        float iopBlue = (float) (iopColor & 255) / 255.0F;

        this.preRenderInWorld(imb, world, x, y, z, renderer);

        final boolean result = renderer.renderStandardBlockWithColorMultiplier(imb, x, y, z, iopRed, iopGreen, iopBlue);

        if (sp.getColor() != AEColor.Transparent) {

            final Tessellator tess = Tessellator.instance;

            tess.setColorOpaque_I(sp.getColor().whiteVariant);
            IIcon ico = ExtraBlockTextures.BlockIOPortSideLights.getIcon();

            this.renderFace(x, y, z, imb, ico, renderer, west);
            this.renderFace(x, y, z, imb, ico, renderer, forward);
            this.renderFace(x, y, z, imb, ico, renderer, east);
            this.renderFace(x, y, z, imb, ico, renderer, backward);

            ico = ExtraBlockTextures.BlockIOPortTopLights.getIcon();
            this.renderFace(x, y, z, imb, ico, renderer, up);
        }

        this.postRenderInWorld(renderer);

        renderer.overrideBlockTexture = null;

        return result;
    }
}
