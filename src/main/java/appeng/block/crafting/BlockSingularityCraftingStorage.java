package appeng.block.crafting;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.client.texture.ExtraBlockTextures;
import appeng.tile.crafting.TileCraftingStorageTile;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSingularityCraftingStorage extends BlockCraftingStorage {

    public BlockSingularityCraftingStorage() {
        this.setTileEntity(TileCraftingStorageTile.class);
    }

    @Override
    public IIcon getIcon(final int direction, final int metadata) {
        return switch (metadata & (~6)) {
            default -> super.getIcon(0, -1);
            case 0 -> ExtraBlockTextures.BlockCraftingStorageSingularity.getIcon();
            case FLAG_FORMED -> ExtraBlockTextures.BlockCraftingStorageSingularityFit.getIcon();
        };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getCheckedSubBlocks(final Item item, final CreativeTabs tabs, final List<ItemStack> itemStacks) {
        itemStacks.add(new ItemStack(this, 1, 0));
    }

    @Override
    public String getUnlocalizedName(final ItemStack is) {
        return "tile.appliedenergistics2.BlockCraftingStorageSingularity";
    }
}
