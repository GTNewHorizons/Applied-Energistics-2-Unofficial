package appeng.block.misc;

import java.util.EnumSet;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileGrower;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockGrower extends AEBaseTileBlock {

    @SideOnly(Side.CLIENT)
    private IIcon off;

    public BlockGrower() {
        super(Material.iron);
        setTileEntity(TileGrower.class);
        setFeature(EnumSet.of(AEFeature.Core));
        setHardness(1);
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileGrower tg = getTileEntity(w, x, y, z);
        if (tg != null) {
            if (Platform.isServer()) {
                Platform.openGUI(p, tg, ForgeDirection.getOrientation(side), GuiBridge.GUI_GROWER);
            }
            return true;
        }
        return false;
    }

    public void registerBlockIcons(final IIconRegister ir) {
        super.registerBlockIcons(ir);
        off = ir.registerIcon("appliedenergistics2:Grower/main_off");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int direction, int metadata) {
        return blockIcon;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (w.getBlockMetadata(x, y, z) == 0) {
            return off;
        }
        return blockIcon;
    }

    @Override
    protected String getTextureName() {
        return "appliedenergistics2:Grower/main_on";
    }
}
