package appeng.block.networking;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

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

    @Override
    public void addInformation(ItemStack is, EntityPlayer player, List<String> lines, boolean advancedItemTooltips) {
        super.addInformation(is, player, lines, advancedItemTooltips);
        lines.add(StatCollector.translateToLocal("gui.tooltips.appliedenergistics2.BlockCreativeEnergyController"));
    }
}
