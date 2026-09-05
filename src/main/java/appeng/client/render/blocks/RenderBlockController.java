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
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;

import appeng.block.networking.BlockController;
import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.networking.TileController;

public class RenderBlockController extends BaseBlockRender<BlockController, TileController> {

    public RenderBlockController() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(final BlockController blk, final IBlockAccess world, final int x, final int y,
            final int z, final RenderBlocks renderer) {
        final TileController controller = blk.getTileEntity(world, x, y, z);
        if (controller == null) {
            return false;
        }

        final boolean xx = this.isConnectedController(controller, world, x - 1, y, z)
                && this.isConnectedController(controller, world, x + 1, y, z);
        final boolean yy = this.isConnectedController(controller, world, x, y - 1, z)
                && this.isConnectedController(controller, world, x, y + 1, z);
        final boolean zz = this.isConnectedController(controller, world, x, y, z - 1)
                && this.isConnectedController(controller, world, x, y, z + 1);

        final int meta = world.getBlockMetadata(x, y, z);
        final boolean hasPower = meta > 0;
        final boolean isConflict = meta == 2;

        ExtraBlockTextures lights = null;
        int textureId = -1;

        if (xx && !yy && !zz) {
            if (hasPower) {
                textureId = 1;
                if (isConflict) {
                    lights = ExtraBlockTextures.BlockControllerColumnConflict;
                } else {
                    lights = ExtraBlockTextures.BlockControllerColumnLights;
                }
            } else {
                textureId = 2;
            }

            renderer.uvRotateEast = 1;
            renderer.uvRotateWest = 1;
            renderer.uvRotateTop = 1;
            renderer.uvRotateBottom = 1;
        } else if (!xx && yy && !zz) {
            if (hasPower) {
                textureId = 1;
                if (isConflict) {
                    lights = ExtraBlockTextures.BlockControllerColumnConflict;
                } else {
                    lights = ExtraBlockTextures.BlockControllerColumnLights;
                }
            } else {
                textureId = 2;
            }

            renderer.uvRotateEast = 0;
            renderer.uvRotateNorth = 0;
        } else if (!xx && !yy && zz) {
            if (hasPower) {
                textureId = 1;
                if (isConflict) {
                    lights = ExtraBlockTextures.BlockControllerColumnConflict;
                } else {
                    lights = ExtraBlockTextures.BlockControllerColumnLights;
                }
            } else {
                textureId = 2;
            }

            renderer.uvRotateNorth = 1;
            renderer.uvRotateSouth = 1;
            renderer.uvRotateTop = 0;
        } else if ((xx ? 1 : 0) + (yy ? 1 : 0) + (zz ? 1 : 0) >= 2) {
            final int v = (Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2;

            renderer.uvRotateEast = renderer.uvRotateBottom = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = 0;

            if (v == 0) {
                textureId = 3;
            } else {
                textureId = 4;
            }
        } else {
            if (hasPower) {
                textureId = 0;

                if (isConflict) {
                    lights = ExtraBlockTextures.BlockControllerConflict;
                } else {
                    lights = ExtraBlockTextures.BlockControllerLights;
                }
            }
        }

        blk.getRendererInstance().setTemporaryRenderIcon(blk.getRenderTexture(textureId, controller.getColor()));
        final boolean out = renderer.renderStandardBlock(blk, x, y, z);

        if (lights != null) {
            final Tessellator tess = Tessellator.instance;
            tess.setColorOpaque_F(1.0f, 1.0f, 1.0f);
            tess.setBrightness(14 << 20 | 14 << 4);
            renderer.renderFaceXNeg(blk, x, y, z, lights.getIcon());
            renderer.renderFaceXPos(blk, x, y, z, lights.getIcon());
            renderer.renderFaceYNeg(blk, x, y, z, lights.getIcon());
            renderer.renderFaceYPos(blk, x, y, z, lights.getIcon());
            renderer.renderFaceZNeg(blk, x, y, z, lights.getIcon());
            renderer.renderFaceZPos(blk, x, y, z, lights.getIcon());
        }

        blk.getRendererInstance().setTemporaryRenderIcon(null);
        renderer.uvRotateEast = renderer.uvRotateBottom = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = 0;
        return out;
    }

    private boolean isConnectedController(final TileController controller, final IBlockAccess world, final int x,
            final int y, final int z) {
        final TileEntity tile = this.getTileEntity(world, x, y, z);
        return tile instanceof TileController other && controller.isColorCompatible(other);
    }

    private TileEntity getTileEntity(final IBlockAccess world, final int x, final int y, final int z) {
        if (y >= 0) {
            return world.getTileEntity(x, y, z);
        }
        return null;
    }
}
