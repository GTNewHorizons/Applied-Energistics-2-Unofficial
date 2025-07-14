package appeng.block.networking;

import static appeng.client.texture.WirelessTextures.WirelessConnectorBlack;
import static appeng.client.texture.WirelessTextures.WirelessConnectorBlue;
import static appeng.client.texture.WirelessTextures.WirelessConnectorBrown;
import static appeng.client.texture.WirelessTextures.WirelessConnectorCyan;
import static appeng.client.texture.WirelessTextures.WirelessConnectorGreen;
import static appeng.client.texture.WirelessTextures.WirelessConnectorGrey;
import static appeng.client.texture.WirelessTextures.WirelessConnectorLightBlue;
import static appeng.client.texture.WirelessTextures.WirelessConnectorLightGrey;
import static appeng.client.texture.WirelessTextures.WirelessConnectorLime;
import static appeng.client.texture.WirelessTextures.WirelessConnectorMagenta;
import static appeng.client.texture.WirelessTextures.WirelessConnectorOrange;
import static appeng.client.texture.WirelessTextures.WirelessConnectorPink;
import static appeng.client.texture.WirelessTextures.WirelessConnectorPurple;
import static appeng.client.texture.WirelessTextures.WirelessConnectorRed;
import static appeng.client.texture.WirelessTextures.WirelessConnectorTransparent;
import static appeng.client.texture.WirelessTextures.WirelessConnectorWhite;
import static appeng.client.texture.WirelessTextures.WirelessConnectorYellow;

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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.AEColor;
import appeng.api.util.IOrientable;
import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.helpers.NullRotation;
import appeng.tile.networking.TileWirelessBase;
import appeng.tile.networking.TileWirelessConnector;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockWirelessConnector extends AEBaseTileBlock {

    public BlockWirelessConnector() {
        super(Material.iron);
        this.hasSubtypes = true;
        this.setTileEntity(TileWirelessConnector.class);
        this.setFeature(EnumSet.of(AEFeature.Channels));
        setHardness(1);
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
            lines.add(AEColor.Transparent.toString());
        } else {
            lines.add(AEColor.values()[is.getItemDamage() - 1].toString());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getCheckedSubBlocks(Item item, CreativeTabs tabs, List<ItemStack> itemStacks) {
        for (int i = 0; i < AEColor.values().length; i++) {
            itemStacks.add(new ItemStack(this, 1, i));
        }
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
        ItemStack is = new ItemStack(this);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileWirelessConnector twc) {
            AEColor color = twc.getColor();
            if (color != AEColor.Transparent) {
                is.setItemDamage(color.ordinal() + 1);
            }
        }
        return is;
    }

    @Override
    public void onBlockPlacedBy(World w, int x, int y, int z, EntityLivingBase player, ItemStack is) {
        int damage = is.getItemDamage();
        if (damage > 0) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof TileWirelessConnector twc) {
                twc.recolourBlock(ForgeDirection.UNKNOWN, AEColor.values()[damage - 1], (EntityPlayer) player);
                w.setBlockMetadataWithNotify(x, y, z, 0, 3);
            }
        }

        super.onBlockPlacedBy(w, x, y, z, player, is);
    }

    @Override
    public void breakBlock(World w, int x, int y, int z, Block a, int b) {
        ((TileWirelessBase) w.getTileEntity(x, y, z)).doUnlink();
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
            case 1 -> WirelessConnectorWhite.getOnIcon();
            case 2 -> WirelessConnectorOrange.getOnIcon();
            case 3 -> WirelessConnectorMagenta.getOnIcon();
            case 4 -> WirelessConnectorLightBlue.getOnIcon();
            case 5 -> WirelessConnectorYellow.getOnIcon();
            case 6 -> WirelessConnectorLime.getOnIcon();
            case 7 -> WirelessConnectorPink.getOnIcon();
            case 8 -> WirelessConnectorGrey.getOnIcon();
            case 9 -> WirelessConnectorLightGrey.getOnIcon();
            case 10 -> WirelessConnectorCyan.getOnIcon();
            case 11 -> WirelessConnectorPurple.getOnIcon();
            case 12 -> WirelessConnectorBlue.getOnIcon();
            case 13 -> WirelessConnectorBrown.getOnIcon();
            case 14 -> WirelessConnectorGreen.getOnIcon();
            case 15 -> WirelessConnectorRed.getOnIcon();
            case 16 -> WirelessConnectorBlack.getOnIcon();
            default -> WirelessConnectorTransparent.getOnIcon();
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (w.getBlockMetadata(x, y, z) == 0) {
            return switch (((TileWirelessConnector) Objects.requireNonNull(this.getTileEntity(w, x, y, z)))
                    .getColor()) {
                case White -> WirelessConnectorWhite.getOffIcon();
                case Orange -> WirelessConnectorOrange.getOffIcon();
                case Magenta -> WirelessConnectorMagenta.getOffIcon();
                case LightBlue -> WirelessConnectorLightBlue.getOffIcon();
                case Yellow -> WirelessConnectorYellow.getOffIcon();
                case Lime -> WirelessConnectorLime.getOffIcon();
                case Pink -> WirelessConnectorPink.getOffIcon();
                case Gray -> WirelessConnectorGrey.getOffIcon();
                case LightGray -> WirelessConnectorLightGrey.getOffIcon();
                case Cyan -> WirelessConnectorCyan.getOffIcon();
                case Purple -> WirelessConnectorPurple.getOffIcon();
                case Blue -> WirelessConnectorBlue.getOffIcon();
                case Brown -> WirelessConnectorBrown.getOffIcon();
                case Green -> WirelessConnectorGreen.getOffIcon();
                case Red -> WirelessConnectorRed.getOffIcon();
                case Black -> WirelessConnectorBlack.getOffIcon();
                default -> WirelessConnectorTransparent.getOffIcon();
            };
        } else {
            return switch (((TileWirelessConnector) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessConnectorWhite.getOnIcon();
                case Orange -> WirelessConnectorOrange.getOnIcon();
                case Magenta -> WirelessConnectorMagenta.getOnIcon();
                case LightBlue -> WirelessConnectorLightBlue.getOnIcon();
                case Yellow -> WirelessConnectorYellow.getOnIcon();
                case Lime -> WirelessConnectorLime.getOnIcon();
                case Pink -> WirelessConnectorPink.getOnIcon();
                case Gray -> WirelessConnectorGrey.getOnIcon();
                case LightGray -> WirelessConnectorLightGrey.getOnIcon();
                case Cyan -> WirelessConnectorCyan.getOnIcon();
                case Purple -> WirelessConnectorPurple.getOnIcon();
                case Blue -> WirelessConnectorBlue.getOnIcon();
                case Brown -> WirelessConnectorBrown.getOnIcon();
                case Green -> WirelessConnectorGreen.getOnIcon();
                case Red -> WirelessConnectorRed.getOnIcon();
                case Black -> WirelessConnectorBlack.getOnIcon();
                default -> WirelessConnectorTransparent.getOnIcon();
            };
        }
    }
}
