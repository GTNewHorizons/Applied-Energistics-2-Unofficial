package appeng.block.misc;

import java.util.EnumSet;

import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.Platform;

public class BlockSuperMEReplenisher extends AEBaseTileBlock {

    public BlockSuperMEReplenisher() {
        super(Material.iron);

        this.setTileEntity(TileSuperMEReplenisher.class);
        this.setFeature(EnumSet.of(AEFeature.Channels));
        this.setHardness(2.2f);
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileSuperMEReplenisher tile = this.getTileEntity(w, x, y, z);
        if (tile != null) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(p, tile, ForgeDirection.getOrientation(side), GuiBridge.GUI_SUPER_ME_REPLENISHER);
            return true;
        }
        return false;
    }
}
