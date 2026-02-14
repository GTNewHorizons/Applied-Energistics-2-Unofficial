package appeng.client.render.blocks;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import appeng.block.spatial.BlockSpatialLinkChamber;
import appeng.client.render.BaseBlockRender;
import appeng.client.render.BlockRenderInfo;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.spatial.TileSpatialLinkChamber;

public class RenderSpatialLinkChamber extends BaseBlockRender<BlockSpatialLinkChamber, TileSpatialLinkChamber> {

    @Override
    public boolean renderInWorld(BlockSpatialLinkChamber block, IBlockAccess world, int x, int y, int z,
            RenderBlocks renderer) {
        final TileSpatialLinkChamber ti = block.getTileEntity(world, x, y, z);
        final BlockRenderInfo info = block.getRendererInstance();

        if (ti != null && ti.hasBoundStorage()) {
            info.setTemporaryRenderIcons(
                    null,
                    null,
                    ExtraBlockTextures.BlockSpatialLinkChamberFrontFull.getIcon(),
                    null,
                    null,
                    null);
        }

        final boolean fz = super.renderInWorld(block, world, x, y, z, renderer);

        info.setTemporaryRenderIcon(null);

        return fz;
    }
}
