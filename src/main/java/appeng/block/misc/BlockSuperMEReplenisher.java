package appeng.block.misc;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.AEColor;
import appeng.block.AEBaseTileBlock;
import appeng.client.texture.ExtraBlockTextures;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSuperMEReplenisher extends AEBaseTileBlock {

    private static final int COLORED_TEXTURE_COUNT = 3;
    private static final String COLORED_TEXTURE_PATH = "appliedenergistics2:replenisher/";

    @SideOnly(Side.CLIENT)
    private IIcon[][] coloredTextures;

    public BlockSuperMEReplenisher() {
        super(Material.iron);

        this.setTileEntity(TileSuperMEReplenisher.class);
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setHardness(2.2f);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        super.registerBlockIcons(iconRegistry);
        this.coloredTextures = new IIcon[COLORED_TEXTURE_COUNT][AEColor.VALUES.length];

        for (final AEColor color : AEColor.VALID_COLORS) {
            this.coloredTextures[0][color.ordinal()] = iconRegistry
                    .registerIcon(this.getTextureName().replace(":", ":replenisher/") + "_" + color.name());
            this.coloredTextures[1][color.ordinal()] = iconRegistry.registerIcon(
                    COLORED_TEXTURE_PATH + ExtraBlockTextures.BlockSuperMEReplenisherDraw.getName()
                            + "_"
                            + color.name());
            this.coloredTextures[2][color.ordinal()] = iconRegistry.registerIcon(
                    COLORED_TEXTURE_PATH + ExtraBlockTextures.BlockSuperMEReplenisherExt.getName()
                            + "_"
                            + color.name());
        }
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

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        final TileSuperMEReplenisher tile = this.getTileEntity(w, x, y, z);
        if (tile != null) {
            if (tile.getColor() != AEColor.Transparent) {
                final int texture = switch (tile.getStatus()) {
                    case 1 -> 1;
                    case -1 -> 2;
                    default -> 0;
                };
                return this.coloredTextures[texture][tile.getColor().ordinal()];
            }
            return switch (tile.getStatus()) {
                case 1 -> ExtraBlockTextures.BlockSuperMEReplenisherDraw.getIcon();
                case -1 -> ExtraBlockTextures.BlockSuperMEReplenisherExt.getIcon();
                default -> super.getIcon(w, x, y, z, s);
            };
        }
        return super.getIcon(w, x, y, z, s);
    }

    @Override
    public void addInformation(ItemStack is, EntityPlayer player, List<String> lines, boolean advancedItemTooltips) {
        super.addInformation(is, player, lines, advancedItemTooltips);

        lines.addAll(
                Arrays.asList(
                        StatCollector.translateToLocal("gui.tooltips.appliedenergistics2.BlockSuperMEReplenisher")
                                .split("\\\\n")));
    }
}
