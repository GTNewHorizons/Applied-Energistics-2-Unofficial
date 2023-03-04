package appeng.block.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.client.texture.ExtraBlockTextures;

public class BlockAdvancedCraftingUnit extends BlockCraftingUnit {

    @Override
    public IIcon getIcon(final int direction, final int metadata) {
        switch (metadata) {
            default:
            case 0:
                return ExtraBlockTextures.BlockCraftingAccelerator64x.getIcon();
            case FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingAccelerator64xFit.getIcon();
            case 1:
                return ExtraBlockTextures.BlockCraftingAccelerator256x.getIcon();
            case 1 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingAccelerator256xFit.getIcon();
            case 2:
                return ExtraBlockTextures.BlockCraftingAccelerator1024x.getIcon();
            case 2 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingAccelerator1024xFit.getIcon();
            case 3:
                return ExtraBlockTextures.BlockCraftingAccelerator4096x.getIcon();
            case 3 | FLAG_FORMED:
                return ExtraBlockTextures.BlockCraftingAccelerator4096xFit.getIcon();
        }
    }

    @Override
    public String getUnlocalizedName(final ItemStack is) {
        if (is.getItemDamage() == 0) {
            return "tile.appliedenergistics2.BlockCraftingAccelerator64x";
        } else if (is.getItemDamage() == 1) {
            return "tile.appliedenergistics2.BlockCraftingAccelerator256x";
        } else if (is.getItemDamage() == 2) {
            return "tile.appliedenergistics2.BlockCraftingAccelerator1024x";
        } else if (is.getItemDamage() == 3) {
            return "tile.appliedenergistics2.BlockCraftingAccelerator4096x";
        }
        return this.getItemUnlocalizedName(is);
    }
}
