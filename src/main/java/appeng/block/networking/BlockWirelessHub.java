package appeng.block.networking;

import static appeng.client.texture.WirelessTextures.WirelessHubBlack;
import static appeng.client.texture.WirelessTextures.WirelessHubBlue;
import static appeng.client.texture.WirelessTextures.WirelessHubBrown;
import static appeng.client.texture.WirelessTextures.WirelessHubCyan;
import static appeng.client.texture.WirelessTextures.WirelessHubGreen;
import static appeng.client.texture.WirelessTextures.WirelessHubGrey;
import static appeng.client.texture.WirelessTextures.WirelessHubLightBlue;
import static appeng.client.texture.WirelessTextures.WirelessHubLightGrey;
import static appeng.client.texture.WirelessTextures.WirelessHubLime;
import static appeng.client.texture.WirelessTextures.WirelessHubMagenta;
import static appeng.client.texture.WirelessTextures.WirelessHubOrange;
import static appeng.client.texture.WirelessTextures.WirelessHubPink;
import static appeng.client.texture.WirelessTextures.WirelessHubPurple;
import static appeng.client.texture.WirelessTextures.WirelessHubRed;
import static appeng.client.texture.WirelessTextures.WirelessHubTransparent;
import static appeng.client.texture.WirelessTextures.WirelessHubWhite;
import static appeng.client.texture.WirelessTextures.WirelessHubYellow;

import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import appeng.tile.networking.TileWirelessHub;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockWirelessHub extends BlockWirelessConnector {

    public BlockWirelessHub() {
        super();
        this.setTileEntity(TileWirelessHub.class);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int direction, int metadata) {
        return switch (metadata) {
            case 1 -> WirelessHubWhite.getOnIcon();
            case 2 -> WirelessHubOrange.getOnIcon();
            case 3 -> WirelessHubMagenta.getOnIcon();
            case 4 -> WirelessHubLightBlue.getOnIcon();
            case 5 -> WirelessHubYellow.getOnIcon();
            case 6 -> WirelessHubLime.getOnIcon();
            case 7 -> WirelessHubPink.getOnIcon();
            case 8 -> WirelessHubGrey.getOnIcon();
            case 9 -> WirelessHubLightGrey.getOnIcon();
            case 10 -> WirelessHubCyan.getOnIcon();
            case 11 -> WirelessHubPurple.getOnIcon();
            case 12 -> WirelessHubBlue.getOnIcon();
            case 13 -> WirelessHubBrown.getOnIcon();
            case 14 -> WirelessHubGreen.getOnIcon();
            case 15 -> WirelessHubRed.getOnIcon();
            case 16 -> WirelessHubBlack.getOnIcon();
            default -> WirelessHubTransparent.getOnIcon();
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (w.getBlockMetadata(x, y, z) == 0) {
            return switch (((TileWirelessHub) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessHubWhite.getOffIcon();
                case Orange -> WirelessHubOrange.getOffIcon();
                case Magenta -> WirelessHubMagenta.getOffIcon();
                case LightBlue -> WirelessHubLightBlue.getOffIcon();
                case Yellow -> WirelessHubYellow.getOffIcon();
                case Lime -> WirelessHubLime.getOffIcon();
                case Pink -> WirelessHubPink.getOffIcon();
                case Gray -> WirelessHubGrey.getOffIcon();
                case LightGray -> WirelessHubLightGrey.getOffIcon();
                case Cyan -> WirelessHubCyan.getOffIcon();
                case Purple -> WirelessHubPurple.getOffIcon();
                case Blue -> WirelessHubBlue.getOffIcon();
                case Brown -> WirelessHubBrown.getOffIcon();
                case Green -> WirelessHubGreen.getOffIcon();
                case Red -> WirelessHubRed.getOffIcon();
                case Black -> WirelessHubBlack.getOffIcon();
                default -> WirelessHubTransparent.getOffIcon();
            };
        } else {
            return switch (((TileWirelessHub) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessHubWhite.getOnIcon();
                case Orange -> WirelessHubOrange.getOnIcon();
                case Magenta -> WirelessHubMagenta.getOnIcon();
                case LightBlue -> WirelessHubLightBlue.getOnIcon();
                case Yellow -> WirelessHubYellow.getOnIcon();
                case Lime -> WirelessHubLime.getOnIcon();
                case Pink -> WirelessHubPink.getOnIcon();
                case Gray -> WirelessHubGrey.getOnIcon();
                case LightGray -> WirelessHubLightGrey.getOnIcon();
                case Cyan -> WirelessHubCyan.getOnIcon();
                case Purple -> WirelessHubPurple.getOnIcon();
                case Blue -> WirelessHubBlue.getOnIcon();
                case Brown -> WirelessHubBrown.getOnIcon();
                case Green -> WirelessHubGreen.getOnIcon();
                case Red -> WirelessHubRed.getOnIcon();
                case Black -> WirelessHubBlack.getOnIcon();
                default -> WirelessHubTransparent.getOnIcon();
            };
        }
    }
}
