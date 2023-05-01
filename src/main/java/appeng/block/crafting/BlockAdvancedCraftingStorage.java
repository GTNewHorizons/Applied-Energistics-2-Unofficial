package appeng.block.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.crafting.TileCraftingStorageTile;

public class BlockAdvancedCraftingStorage extends BlockCraftingStorage {

    public BlockAdvancedCraftingStorage() {
        this.setTileEntity(TileCraftingStorageTile.class);
    }

    @Override
    public IIcon getIcon(final int direction, final int metadata) {
        return switch (metadata & (~4)) {
            default -> super.getIcon(0, -1);
            case 0 -> ExtraBlockTextures.BlockCraftingStorage256k.getIcon();
            case 1 -> ExtraBlockTextures.BlockCraftingStorage1024k.getIcon();
            case 2 -> ExtraBlockTextures.BlockCraftingStorage4096k.getIcon();
            case 3 -> ExtraBlockTextures.BlockCraftingStorage16384k.getIcon();
            case FLAG_FORMED -> ExtraBlockTextures.BlockCraftingStorage256kFit.getIcon();
            case 1 | FLAG_FORMED -> ExtraBlockTextures.BlockCraftingStorage1024kFit.getIcon();
            case 2 | FLAG_FORMED -> ExtraBlockTextures.BlockCraftingStorage4096kFit.getIcon();
            case 3 | FLAG_FORMED -> ExtraBlockTextures.BlockCraftingStorage16384kFit.getIcon();
        };
    }

    @Override
    public String getUnlocalizedName(final ItemStack is) {
        return switch (is.getItemDamage()) {
            case 0 -> "tile.appliedenergistics2.BlockCraftingStorage256k";
            case 1 -> "tile.appliedenergistics2.BlockCraftingStorage1024k";
            case 2 -> "tile.appliedenergistics2.BlockCraftingStorage4096k";
            case 3 -> "tile.appliedenergistics2.BlockCraftingStorage16384k";
            default -> this.getItemUnlocalizedName(is);
        };
    }
}
