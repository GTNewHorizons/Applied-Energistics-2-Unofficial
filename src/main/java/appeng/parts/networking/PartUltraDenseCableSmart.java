package appeng.parts.networking;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.texture.CableBusTextures;
import appeng.client.texture.TextureUtils;
import appeng.helpers.Reflected;

public class PartUltraDenseCableSmart extends PartDenseCable {

    @Reflected
    public PartUltraDenseCableSmart(final ItemStack is) {
        super(is);
        this.getProxy().setFlags(GridFlags.ULTRA_DENSE_CAPACITY, GridFlags.PREFERRED);
    }

    @Override
    public AECableType getCableConnectionType() {
        return AECableType.ULTRA_DENSE_SMART;
    }

    @Override
    public IIcon getTexture(final AEColor c) {
        if (c == AEColor.Transparent) {
            return TextureUtils.checkTexture(
                    AEApi.instance().definitions().parts().cableUltraDenseSmart().stack(AEColor.Transparent, 1)
                            .getIconIndex());
        }

        return this.getDenseTexture(c);
    }

    @Override
    protected IIcon getDenseTexture(final AEColor c) {
        return TextureUtils.checkTexture(switch (c) {
            case Black -> CableBusTextures.MEUltraDense_Black.getIcon();
            case Blue -> CableBusTextures.MEUltraDense_Blue.getIcon();
            case Brown -> CableBusTextures.MEUltraDense_Brown.getIcon();
            case Cyan -> CableBusTextures.MEUltraDense_Cyan.getIcon();
            case Gray -> CableBusTextures.MEUltraDense_Gray.getIcon();
            case Green -> CableBusTextures.MEUltraDense_Green.getIcon();
            case LightBlue -> CableBusTextures.MEUltraDense_LightBlue.getIcon();
            case LightGray -> CableBusTextures.MEUltraDense_LightGrey.getIcon();
            case Lime -> CableBusTextures.MEUltraDense_Lime.getIcon();
            case Magenta -> CableBusTextures.MEUltraDense_Magenta.getIcon();
            case Orange -> CableBusTextures.MEUltraDense_Orange.getIcon();
            case Pink -> CableBusTextures.MEUltraDense_Pink.getIcon();
            case Purple -> CableBusTextures.MEUltraDense_Purple.getIcon();
            case Red -> CableBusTextures.MEUltraDense_Red.getIcon();
            case White -> CableBusTextures.MEUltraDense_White.getIcon();
            case Yellow -> CableBusTextures.MEUltraDense_Yellow.getIcon();
            default -> this.getItemStack().getIconIndex();
        });
    }
}
