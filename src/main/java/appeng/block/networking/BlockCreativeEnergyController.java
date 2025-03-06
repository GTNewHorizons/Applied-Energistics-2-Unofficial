package appeng.block.networking;

import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.networking.TileCreativeEnergyController;

public class BlockCreativeEnergyController extends BlockController {

    public BlockCreativeEnergyController() {
        super();
        this.setTileEntity(TileCreativeEnergyController.class);
    }

    @Override
    public ExtraBlockTextures getRenderTexture(int id) {
        return switch (id) {
            case 0 -> ExtraBlockTextures.BlockCreativeEnergyControllerPowered;
            case 1 -> ExtraBlockTextures.BlockCreativeEnergyControllerColumnPowered;
            case 2 -> ExtraBlockTextures.BlockCreativeEnergyControllerColumn;
            case 3 -> ExtraBlockTextures.BlockCreativeEnergyControllerInsideA;
            case 4 -> ExtraBlockTextures.BlockCreativeEnergyControllerInsideB;
            default -> throw new IllegalStateException("Unexpected value: " + id);
        };
    }
}
