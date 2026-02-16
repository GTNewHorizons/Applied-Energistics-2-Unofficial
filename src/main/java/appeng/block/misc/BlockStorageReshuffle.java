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
import appeng.core.sync.GuiBridge;
import appeng.tile.misc.TileStorageReshuffle;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Block for the Storage Reshuffle system. Allows players to redistribute items across storage cells based on priority.
 * Similar to Security Terminal but for storage management.
 */
public class BlockStorageReshuffle extends AEBaseTileBlock {

    public BlockStorageReshuffle() {
        super(Material.iron);

        this.setTileEntity(TileStorageReshuffle.class);
        this.setFeature(EnumSet.of(AEFeature.Channels, AEFeature.PoweredTools));
        this.setHardness(2.2f);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected RendererStorageReshuffle getRenderer() {
        return new RendererStorageReshuffle();
    }

    /**
     * Register block icons and initialize the BlockRenderInfo with proper textures. This is critical for NEI and
     * inventory rendering to work correctly.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(final IIconRegister iconRegistry) {
        // Register icons using the iconRegistry (which registers them with Minecraft)
        // Note: ExtraBlockTextures also registers these, but we need FlippableIcon wrappers here
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

        // Set the main block icon (used as default)
        this.blockIcon = topIcon.getOriginal();

        // Update the renderer's BlockRenderInfo with these icons
        // This is what makes NEI and inventory rendering work!
        // Order: bottom, top, north(back), south(front), east(side), west(side)
        this.getRendererInstance().updateIcons(bottomIcon, topIcon, backIcon, frontIcon, sideIcon, sideIcon);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean advancedItemTooltips) {
        lines.add("§7Reorganizes ME storage based on priority");
        lines.add("§7Extracts and re-inserts items to optimize");
        lines.add("§7storage cell usage and reduce fragmentation");
        lines.add("");
        lines.add("§eFeatures:");
        lines.add("  §7• §aFilter modes§7: All, Items, Fluids");
        lines.add("  §7• §aVoid protection§7: Prevents item loss");
        lines.add("  §7• §cOverwrite protection§7: Preserves data");
        lines.add("  §7• §6Real-time progress§7 tracking");
        lines.add("  §7• §bDetailed reports§7 with statistics");
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
