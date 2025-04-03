package appeng.block.networking;

import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import appeng.client.texture.WirelessTextures;
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
            case 1 -> WirelessTextures.WirelessHubOnWhite.getIcon();
            case 2 -> WirelessTextures.WirelessHubOnOrange.getIcon();
            case 3 -> WirelessTextures.WirelessHubOnMagenta.getIcon();
            case 4 -> WirelessTextures.WirelessHubOnLightBlue.getIcon();
            case 5 -> WirelessTextures.WirelessHubOnYellow.getIcon();
            case 6 -> WirelessTextures.WirelessHubOnLime.getIcon();
            case 7 -> WirelessTextures.WirelessHubOnPink.getIcon();
            case 8 -> WirelessTextures.WirelessHubOnGrey.getIcon();
            case 9 -> WirelessTextures.WirelessHubOnLightGrey.getIcon();
            case 10 -> WirelessTextures.WirelessHubOnCyan.getIcon();
            case 11 -> WirelessTextures.WirelessHubOnPurple.getIcon();
            case 12 -> WirelessTextures.WirelessHubOnBlue.getIcon();
            case 13 -> WirelessTextures.WirelessHubOnBrown.getIcon();
            case 14 -> WirelessTextures.WirelessHubOnGreen.getIcon();
            case 15 -> WirelessTextures.WirelessHubOnRed.getIcon();
            case 16 -> WirelessTextures.WirelessHubOnBlack.getIcon();
            default -> WirelessTextures.WirelessHubOnTransparent.getIcon();
        };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(IBlockAccess w, int x, int y, int z, int s) {
        if (w.getBlockMetadata(x, y, z) == 0) {
            return switch (((TileWirelessHub) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessTextures.WirelessHubOffWhite.getIcon();
                case Orange -> WirelessTextures.WirelessHubOffOrange.getIcon();
                case Magenta -> WirelessTextures.WirelessHubOffMagenta.getIcon();
                case LightBlue -> WirelessTextures.WirelessHubOffLightBlue.getIcon();
                case Yellow -> WirelessTextures.WirelessHubOffYellow.getIcon();
                case Lime -> WirelessTextures.WirelessHubOffLime.getIcon();
                case Pink -> WirelessTextures.WirelessHubOffPink.getIcon();
                case Gray -> WirelessTextures.WirelessHubOffGrey.getIcon();
                case LightGray -> WirelessTextures.WirelessHubOffLightGrey.getIcon();
                case Cyan -> WirelessTextures.WirelessHubOffCyan.getIcon();
                case Purple -> WirelessTextures.WirelessHubOffPurple.getIcon();
                case Blue -> WirelessTextures.WirelessHubOffBlue.getIcon();
                case Brown -> WirelessTextures.WirelessHubOffBrown.getIcon();
                case Green -> WirelessTextures.WirelessHubOffGreen.getIcon();
                case Red -> WirelessTextures.WirelessHubOffRed.getIcon();
                case Black -> WirelessTextures.WirelessHubOffBlack.getIcon();
                default -> WirelessTextures.WirelessHubOffTransparent.getIcon();
            };
        } else {
            return switch (((TileWirelessHub) w.getTileEntity(x, y, z)).getColor()) {
                case White -> WirelessTextures.WirelessHubOnWhite.getIcon();
                case Orange -> WirelessTextures.WirelessHubOnOrange.getIcon();
                case Magenta -> WirelessTextures.WirelessHubOnMagenta.getIcon();
                case LightBlue -> WirelessTextures.WirelessHubOnLightBlue.getIcon();
                case Yellow -> WirelessTextures.WirelessHubOnYellow.getIcon();
                case Lime -> WirelessTextures.WirelessHubOnLime.getIcon();
                case Pink -> WirelessTextures.WirelessHubOnPink.getIcon();
                case Gray -> WirelessTextures.WirelessHubOnGrey.getIcon();
                case LightGray -> WirelessTextures.WirelessHubOnLightGrey.getIcon();
                case Cyan -> WirelessTextures.WirelessHubOnCyan.getIcon();
                case Purple -> WirelessTextures.WirelessHubOnPurple.getIcon();
                case Blue -> WirelessTextures.WirelessHubOnBlue.getIcon();
                case Brown -> WirelessTextures.WirelessHubOnBrown.getIcon();
                case Green -> WirelessTextures.WirelessHubOnGreen.getIcon();
                case Red -> WirelessTextures.WirelessHubOnRed.getIcon();
                case Black -> WirelessTextures.WirelessHubOnBlack.getIcon();
                default -> WirelessTextures.WirelessHubOnTransparent.getIcon();
            };
        }
    }
}
