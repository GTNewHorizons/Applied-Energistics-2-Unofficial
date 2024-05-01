package appeng.parts.networking;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.texture.CableBusTextures;
import appeng.helpers.Reflected;

public class PartUltraDenseCableCovered extends PartDenseCableCovered {

    @Reflected
    public PartUltraDenseCableCovered(final ItemStack is) {
        super(is);
        this.getProxy().setFlags(GridFlags.ULTRA_DENSE_CAPACITY, GridFlags.PREFERRED);
    }

    @Override
    public AECableType getCableConnectionType() {
        return AECableType.ULTRA_DENSE;
    }

    @Override
    public IIcon getTexture(final AEColor c) {
        if (c == AEColor.Transparent) {
            return CableBusTextures.checkTexture(
                    AEApi.instance().definitions().parts().cableUltraDenseCovered().stack(AEColor.Transparent, 1)
                            .getIconIndex());
        }

        return this.getDenseCoveredTexture(c);
    }

    @Override
    protected IIcon getDenseCoveredTexture(final AEColor c) {
        return CableBusTextures.checkTexture(switch (c) {
            case Black -> CableBusTextures.MEUltraDenseCovered_Black.getIcon();
            case Blue -> CableBusTextures.MEUltraDenseCovered_Blue.getIcon();
            case Brown -> CableBusTextures.MEUltraDenseCovered_Brown.getIcon();
            case Cyan -> CableBusTextures.MEUltraDenseCovered_Cyan.getIcon();
            case Gray -> CableBusTextures.MEUltraDenseCovered_Gray.getIcon();
            case Green -> CableBusTextures.MEUltraDenseCovered_Green.getIcon();
            case LightBlue -> CableBusTextures.MEUltraDenseCovered_LightBlue.getIcon();
            case LightGray -> CableBusTextures.MEUltraDenseCovered_LightGrey.getIcon();
            case Lime -> CableBusTextures.MEUltraDenseCovered_Lime.getIcon();
            case Magenta -> CableBusTextures.MEUltraDenseCovered_Magenta.getIcon();
            case Orange -> CableBusTextures.MEUltraDenseCovered_Orange.getIcon();
            case Pink -> CableBusTextures.MEUltraDenseCovered_Pink.getIcon();
            case Purple -> CableBusTextures.MEUltraDenseCovered_Purple.getIcon();
            case Red -> CableBusTextures.MEUltraDenseCovered_Red.getIcon();
            case White -> CableBusTextures.MEUltraDenseCovered_White.getIcon();
            case Yellow -> CableBusTextures.MEUltraDenseCovered_Yellow.getIcon();
            default -> this.getItemStack().getIconIndex();
        });
    }
}
