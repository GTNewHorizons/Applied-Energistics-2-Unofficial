package appeng.block.misc;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.block.AEBaseTileBlock;
import appeng.client.render.blocks.RendererStorageReshuffle;
import appeng.client.texture.FlippableIcon;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockStorageReshuffle extends AEBaseTileBlock {

    public BlockStorageReshuffle() {
        super(Material.iron);

        this.setTileEntity(TileStorageReshuffle.class);
        this.setFeature(EnumSet.of(AEFeature.Channels));
        this.setHardness(2.2f);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RendererStorageReshuffle getRenderer() {
        return new RendererStorageReshuffle();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        final FlippableIcon topIcon = new FlippableIcon(
                iconRegistry.registerIcon("appliedenergistics2:BlockReshuffleTop"));
        final FlippableIcon bottomIcon = new FlippableIcon(
                iconRegistry.registerIcon("appliedenergistics2:BlockReshuffleBottom"));
        final FlippableIcon frontIcon = new FlippableIcon(
                iconRegistry.registerIcon("appliedenergistics2:BlockReshuffleFrontAll"));
        final FlippableIcon backIcon = new FlippableIcon(
                iconRegistry.registerIcon("appliedenergistics2:BlockReshuffleBack"));
        final FlippableIcon sideIcon = new FlippableIcon(
                iconRegistry.registerIcon("appliedenergistics2:BlockReshuffleSide"));

        this.blockIcon = topIcon.getOriginal();
        this.getRendererInstance().updateIcons(bottomIcon, topIcon, backIcon, frontIcon, sideIcon, sideIcon);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean advancedItemTooltips) {
        lines.add(GuiText.ReshuffleTooltipDesc1.getLocal());
        lines.add(GuiText.ReshuffleTooltipDesc2.getLocal());
        lines.add(GuiText.ReshuffleTooltipDesc3.getLocal());
    }

    @Override
    public boolean onActivated(final World w, final int x, final int y, final int z, final EntityPlayer p,
            final int side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) {
            return false;
        }

        final TileStorageReshuffle tile = this.getTileEntity(w, x, y, z);
        if (tile != null) {
            if (Platform.isClient()) {
                return true;
            }

            Platform.openGUI(p, tile, ForgeDirection.getOrientation(side), GuiBridge.GUI_STORAGE_RESHUFFLE);
            return true;
        }
        return false;
    }
}
