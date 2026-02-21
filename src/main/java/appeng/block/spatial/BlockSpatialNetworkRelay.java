package appeng.block.spatial;

import java.util.EnumSet;

import net.minecraft.block.material.Material;

import appeng.block.AEBaseBlock;
import appeng.block.AEBaseTileBlock;
import appeng.client.render.BaseBlockRender;
import appeng.client.render.blocks.RenderSpatialNetworkRelay;
import appeng.core.features.AEFeature;
import appeng.tile.AEBaseTile;
import appeng.tile.spatial.TileSpatialNetworkRelay;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A simple in-dimension AE network anchor for Spatial Storage dimensions.
 *
 * When placed inside a spatial storage dimension, it can join the AE grid which owns that dimension (the grid that
 * currently has the corresponding spatial cell in Spatial Link Chamber).
 */
public class BlockSpatialNetworkRelay extends AEBaseTileBlock {

    public BlockSpatialNetworkRelay() {
        super(Material.iron);
        this.setTileEntity(TileSpatialNetworkRelay.class);
        this.setFeature(EnumSet.of(AEFeature.SpatialIO));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> getRenderer() {
        return new RenderSpatialNetworkRelay();
    }
}
