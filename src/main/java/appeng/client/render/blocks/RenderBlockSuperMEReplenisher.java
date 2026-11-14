package appeng.client.render.blocks;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.misc.BlockSuperMEReplenisher;
import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import appeng.client.texture.FlippableIcon;
import appeng.tile.misc.TileSuperMEReplenisher;

public class RenderBlockSuperMEReplenisher extends BaseBlockRender<BlockSuperMEReplenisher, TileSuperMEReplenisher> {

    @Override
    public boolean renderInWorld(final BlockSuperMEReplenisher block, final IBlockAccess world, final int x,
            final int y, final int z, final RenderBlocks renderer) {
        final boolean rendered = super.renderInWorld(block, world, x, y, z, renderer);
        final TileSuperMEReplenisher tile = block.getTileEntity(world, x, y, z);
        final int status = tile == null ? 0 : tile.getStatus();
        if (renderer.hasOverrideBlockTexture() || status != 1 && status != -1) {
            return rendered;
        }

        final boolean enableAO = renderer.enableAO;
        final Tessellator tess = Tessellator.instance;
        final FlippableIcon icon = new FlippableIcon(ExtraBlockTextures.BlockSuperMEReplenisherStatus.getIcon());

        try {
            this.preRenderInWorld(block, world, x, y, z, renderer);
            renderer.enableAO = false;
            for (final ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                final int adjacentX = x + side.offsetX;
                final int adjacentY = y + side.offsetY;
                final int adjacentZ = z + side.offsetZ;
                if (!renderer.renderAllFaces
                        && !block.shouldSideBeRendered(world, adjacentX, adjacentY, adjacentZ, side.ordinal())) {
                    continue;
                }

                final float shade = switch (side) {
                    case DOWN -> 0.5f;
                    case NORTH, SOUTH -> 0.8f;
                    case WEST, EAST -> 0.6f;
                    default -> 1.0f;
                };
                tess.setBrightness(block.getMixedBrightnessForBlock(world, adjacentX, adjacentY, adjacentZ));
                tess.setColorOpaque_F(status == -1 ? shade : 0.0f, status == 1 ? shade : 0.0f, 0.0f);

                // Keep the overlay aligned with the base texture after rotation.
                final IIcon base = block.getIcon(world, x, y, z, side.ordinal());
                icon.setFlip(base.getMinU() > base.getMaxU(), base.getMinV() > base.getMaxV());
                this.renderFace(x, y, z, block, icon, renderer, side);
            }
        } finally {
            this.postRenderInWorld(renderer);
            renderer.enableAO = enableAO;
            tess.setColorOpaque_F(1.0f, 1.0f, 1.0f);
        }

        return rendered;
    }
}
