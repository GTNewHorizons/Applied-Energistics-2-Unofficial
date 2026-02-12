package appeng.block.spatial;

import java.util.EnumSet;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseBlock;
import appeng.block.AEBaseTileBlock;
import appeng.client.render.BaseBlockRender;
import appeng.client.render.blocks.RenderSpatialLinkChamber;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.AEBaseTile;
import appeng.tile.spatial.TileSpatialLinkChamber;
import appeng.util.Platform;

/**
 * Overworld-side endpoint for linking into a Spatial Storage dimension. Similar in spirit to Quantum Link Chamber, but
 * pairs by Spatial Storage dimension.
 */
public class BlockSpatialLinkChamber extends AEBaseTileBlock {

    public BlockSpatialLinkChamber() {
        super(Material.iron);
        this.setTileEntity(TileSpatialLinkChamber.class);
        this.setFeature(EnumSet.of(AEFeature.SpatialIO));
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileSpatialLinkChamber te = this.getTileEntity(w, x, y, z);
        if (te != null) {
            if (Platform.isServer()) {
                Platform.openGUI(p, te, ForgeDirection.getOrientation(side), GuiBridge.GUI_SPATIAL_LINK_CHAMBER);
            }
            return true;
        }

        return false;
    }

    @Override
    protected BaseBlockRender<? extends AEBaseBlock, ? extends AEBaseTile> getRenderer() {
        return new RenderSpatialLinkChamber();
    }
}
