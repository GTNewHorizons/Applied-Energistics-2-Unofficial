package appeng.client.render.blocks;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import appeng.block.spatial.BlockSpatialNetworkRelay;
import appeng.client.render.BaseBlockRender;
import appeng.client.render.BlockRenderInfo;
import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.spatial.TileSpatialNetworkRelay;

public class RenderSpatialNetworkRelay extends BaseBlockRender<BlockSpatialNetworkRelay, TileSpatialNetworkRelay> {

    @Override
    public boolean renderInWorld(BlockSpatialNetworkRelay block, IBlockAccess world, int x, int y, int z,
            RenderBlocks renderer) {
        final TileSpatialNetworkRelay ti = block.getTileEntity(world, x, y, z);
        final BlockRenderInfo info = block.getRendererInstance();

        if (ti != null && ti.isConnected()) {
            info.setTemporaryRenderIcon(ExtraBlockTextures.BlockSpatialNetworkRelayConnected.getIcon());
        }

        final boolean fz = super.renderInWorld(block, world, x, y, z, renderer);

        info.setTemporaryRenderIcon(null);

        return fz;
    }
}
