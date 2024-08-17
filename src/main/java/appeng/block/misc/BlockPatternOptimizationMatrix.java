package appeng.block.misc;

import java.util.EnumSet;

import net.minecraft.block.material.Material;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.tile.misc.TilePatternOptimizationMatrix;

public class BlockPatternOptimizationMatrix extends AEBaseTileBlock {

    public BlockPatternOptimizationMatrix() {
        super(Material.iron);
        this.setTileEntity(TilePatternOptimizationMatrix.class);
        this.setFeature(EnumSet.of(AEFeature.PatternsOptimizer));
    }

}
