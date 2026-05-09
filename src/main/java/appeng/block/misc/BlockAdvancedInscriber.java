package appeng.block.misc;

import java.util.EnumSet;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseTileBlock;
import appeng.client.texture.FlippableIcon;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileAdvancedInscriber;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockAdvancedInscriber extends AEBaseTileBlock {

    @SideOnly(Side.CLIENT)
    private IIcon topIcon;

    @SideOnly(Side.CLIENT)
    private IIcon sideIconOn;

    @SideOnly(Side.CLIENT)
    private IIcon sideIconOff;

    public BlockAdvancedInscriber() {
        super(Material.iron);

        this.setTileEntity(TileAdvancedInscriber.class);
        this.setFeature(EnumSet.of(AEFeature.Inscriber));
        this.setHardness(1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        this.topIcon = iconRegistry.registerIcon("appliedenergistics2:BlockAdvancedInscriberTop");
        this.sideIconOn = iconRegistry.registerIcon("appliedenergistics2:BlockAdvancedInscriberSideOn");
        this.sideIconOff = iconRegistry.registerIcon("appliedenergistics2:BlockAdvancedInscriberSideOff");

        final FlippableIcon top = new FlippableIcon(this.topIcon);
        final FlippableIcon side = new FlippableIcon(this.sideIconOff);
        this.blockIcon = this.topIcon;
        this.getRendererInstance().updateIcons(top, top, side, side, side, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(final int direction, final int metadata) {
        if (direction == ForgeDirection.UP.ordinal() || direction == ForgeDirection.DOWN.ordinal()) {
            return this.topIcon;
        }
        return metadata == 1 ? this.sideIconOn : this.sideIconOff;
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileAdvancedInscriber tile = this.getTileEntity(w, x, y, z);
        if (tile != null) {
            if (Platform.isServer()) {
                Platform.openGUI(p, tile, ForgeDirection.getOrientation(side), GuiBridge.GUI_ADVANCED_INSCRIBER);
            }
            return true;
        }
        return false;
    }
}
