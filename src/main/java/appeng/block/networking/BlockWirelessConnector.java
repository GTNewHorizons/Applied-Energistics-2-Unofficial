package appeng.block.networking;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.AEColor;
import appeng.api.util.IOrientable;
import appeng.block.AEBaseTileBlock;
import appeng.client.texture.WirelessTextures;
import appeng.core.features.AEFeature;
import appeng.helpers.NullRotation;
import appeng.tile.networking.TileWirelessConnector;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockWirelessConnector extends AEBaseTileBlock {

    public BlockWirelessConnector() {
        super(Material.iron);
        this.setTileEntity(TileWirelessConnector.class);
        this.setFeature(EnumSet.of(AEFeature.Channels));
    }

    @Override
    public void setHasSubtypes(boolean b) {
        super.setHasSubtypes(true);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ItemStack is = new ItemStack(this);
        TileWirelessConnector te = (TileWirelessConnector) world.getTileEntity(x, y, z);
        if (te != null) {
            if (te.getColor() != AEColor.Transparent) {
                is.setItemDamage(te.getColor().ordinal() + 1);
            }
        }
        ArrayList<ItemStack> arr = new ArrayList<>();
        arr.add(is);
        return arr;
    }

    @Override
    public void addInformation(ItemStack is, EntityPlayer p, List<String> lines, boolean advancedItemTooltips) {
        if (is.getItemDamage() == 0) {
            lines.add(AEColor.values()[16].toString());
        } else {
            lines.add(AEColor.values()[is.getItemDamage() - 1].toString());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getCheckedSubBlocks(Item item, CreativeTabs tabs, List<ItemStack> itemStacks) {
        for (int i = 0; i < 17; i++) {
            itemStacks.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ItemStack is = new ItemStack(this);
        TileWirelessConnector te = (TileWirelessConnector) world.getTileEntity(x, y, z);
        if (te != null) {
            if (te.getColor() != AEColor.Transparent) {
                is.setItemDamage(te.getColor().ordinal() + 1);
            }
        }
        return is;
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase player, ItemStack is) {
        TileWirelessConnector te = (TileWirelessConnector) w.getTileEntity(x, y, z);
        if (is.getItemDamage() > 0) {
            te.recolourBlock(ForgeDirection.UNKNOWN, AEColor.values()[is.getItemDamage() - 1], (EntityPlayer) player);
            w.setBlockMetadataWithNotify(x, y, z, 0, 3);
        }

        super.onBlockPlacedBy(w, x, y, z, player, is);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block a, int b) {
        ((TileWirelessConnector) w.getTileEntity(x, y, z)).breakConnection();
        super.breakBlock(w, x, y, z, a, b);
    }

    @Override
    public IOrientable getOrientable(final IBlockAccess w, final int x, final int y, final int z) {
        return new NullRotation();
    }

    @Override
    public void setRenderStateByMeta(final int metadata) {
        this.getRendererInstance().setTemporaryRenderIcon(this.getIcon(0, metadata));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int direction, int metadata) {
        return switch (metadata) {
            case 1 -> WirelessTextures.WirelessConnectorOnWhite.getIcon();
            case 2 -> WirelessTextures.WirelessConnectorOnOrange.getIcon();
            case 3 -> WirelessTextures.WirelessConnectorOnMagenta.getIcon();
            case 4 -> WirelessTextures.WirelessConnectorOnLightBlue.getIcon();
            case 5 -> WirelessTextures.WirelessConnectorOnYellow.getIcon();
            case 6 -> WirelessTextures.WirelessConnectorOnLime.getIcon();
            case 7 -> WirelessTextures.WirelessConnectorOnPink.getIcon();
            case 8 -> WirelessTextures.WirelessConnectorOnGrey.getIcon();
            case 9 -> WirelessTextures.WirelessConnectorOnLightGrey.getIcon();
            case 10 -> WirelessTextures.WirelessConnectorOnCyan.getIcon();
            case 11 -> WirelessTextures.WirelessConnectorOnPurple.getIcon();
            case 12 -> WirelessTextures.WirelessConnectorOnBlue.getIcon();
            case 13 -> WirelessTextures.WirelessConnectorOnBrown.getIcon();
            case 14 -> WirelessTextures.WirelessConnectorOnGreen.getIcon();
            case 15 -> WirelessTextures.WirelessConnectorOnRed.getIcon();
            case 16 -> WirelessTextures.WirelessConnectorOnBlack.getIcon();
            default -> WirelessTextures.WirelessConnectorOnTransparent.getIcon();
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (w.getBlockMetadata(x, y, z) == 0) {
            return switch (((TileWirelessConnector) Objects.requireNonNull(this.getTileEntity(w, x, y, z)))
                    .getColor()) {
                case White -> WirelessTextures.WirelessConnectorOffWhite.getIcon();
                case Orange -> WirelessTextures.WirelessConnectorOffOrange.getIcon();
                case Magenta -> WirelessTextures.WirelessConnectorOffMagenta.getIcon();
                case LightBlue -> WirelessTextures.WirelessConnectorOffLightBlue.getIcon();
                case Yellow -> WirelessTextures.WirelessConnectorOffYellow.getIcon();
                case Lime -> WirelessTextures.WirelessConnectorOffLime.getIcon();
                case Pink -> WirelessTextures.WirelessConnectorOffPink.getIcon();
                case Gray -> WirelessTextures.WirelessConnectorOffGrey.getIcon();
                case LightGray -> WirelessTextures.WirelessConnectorOffLightGrey.getIcon();
                case Cyan -> WirelessTextures.WirelessConnectorOffCyan.getIcon();
                case Purple -> WirelessTextures.WirelessConnectorOffPurple.getIcon();
                case Blue -> WirelessTextures.WirelessConnectorOffBlue.getIcon();
                case Brown -> WirelessTextures.WirelessConnectorOffBrown.getIcon();
                case Green -> WirelessTextures.WirelessConnectorOffGreen.getIcon();
                case Red -> WirelessTextures.WirelessConnectorOffRed.getIcon();
                case Black -> WirelessTextures.WirelessConnectorOffBlack.getIcon();
                default -> WirelessTextures.WirelessConnectorOffTransparent.getIcon();
            };
        } else {
            return switch (((TileWirelessConnector) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessTextures.WirelessConnectorOnWhite.getIcon();
                case Orange -> WirelessTextures.WirelessConnectorOnOrange.getIcon();
                case Magenta -> WirelessTextures.WirelessConnectorOnMagenta.getIcon();
                case LightBlue -> WirelessTextures.WirelessConnectorOnLightBlue.getIcon();
                case Yellow -> WirelessTextures.WirelessConnectorOnYellow.getIcon();
                case Lime -> WirelessTextures.WirelessConnectorOnLime.getIcon();
                case Pink -> WirelessTextures.WirelessConnectorOnPink.getIcon();
                case Gray -> WirelessTextures.WirelessConnectorOnGrey.getIcon();
                case LightGray -> WirelessTextures.WirelessConnectorOnLightGrey.getIcon();
                case Cyan -> WirelessTextures.WirelessConnectorOnCyan.getIcon();
                case Purple -> WirelessTextures.WirelessConnectorOnPurple.getIcon();
                case Blue -> WirelessTextures.WirelessConnectorOnBlue.getIcon();
                case Brown -> WirelessTextures.WirelessConnectorOnBrown.getIcon();
                case Green -> WirelessTextures.WirelessConnectorOnGreen.getIcon();
                case Red -> WirelessTextures.WirelessConnectorOnRed.getIcon();
                case Black -> WirelessTextures.WirelessConnectorOnBlack.getIcon();
                default -> WirelessTextures.WirelessConnectorOnTransparent.getIcon();
            };
        }
    }
}
