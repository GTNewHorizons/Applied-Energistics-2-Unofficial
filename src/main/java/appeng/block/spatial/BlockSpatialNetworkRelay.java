package appeng.block.spatial;

import java.util.EnumSet;

import appeng.block.AEBaseBlock;
import appeng.client.render.BaseBlockRender;
import appeng.client.render.blocks.RenderSpatialNetworkRelay;
import appeng.core.sync.GuiBridge;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;
import net.minecraft.block.material.Material;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.tile.spatial.TileSpatialNetworkRelay;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

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
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
                               final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileSpatialNetworkRelay te = this.getTileEntity(w, x, y, z);
        if (te != null) {
            if (Platform.isServer()) {
                Platform.openGUI(p, te, ForgeDirection.getOrientation(side), GuiBridge.GUI_SPATIAL_NETWORK_RELAY);
            }
            return true;
        }

        return false;
    }

    @Override
    protected BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> getRenderer() {
        return new RenderSpatialNetworkRelay();
    }
}
