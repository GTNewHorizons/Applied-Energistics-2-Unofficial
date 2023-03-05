package appeng.parts.networking;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.texture.CableBusTextures;
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
            return AEApi.instance().definitions().parts().cableUltraDenseSmart().stack(AEColor.Transparent, 1)
                    .getIconIndex();
        }

        return this.getDenseTexture(c);
    }

    @Override
    protected IIcon getDenseTexture(final AEColor c) {
        switch (c) {
            case Black:
                return CableBusTextures.MEUltraDense_Black.getIcon();
            case Blue:
                return CableBusTextures.MEUltraDense_Blue.getIcon();
            case Brown:
                return CableBusTextures.MEUltraDense_Brown.getIcon();
            case Cyan:
                return CableBusTextures.MEUltraDense_Cyan.getIcon();
            case Gray:
                return CableBusTextures.MEUltraDense_Gray.getIcon();
            case Green:
                return CableBusTextures.MEUltraDense_Green.getIcon();
            case LightBlue:
                return CableBusTextures.MEUltraDense_LightBlue.getIcon();
            case LightGray:
                return CableBusTextures.MEUltraDense_LightGrey.getIcon();
            case Lime:
                return CableBusTextures.MEUltraDense_Lime.getIcon();
            case Magenta:
                return CableBusTextures.MEUltraDense_Magenta.getIcon();
            case Orange:
                return CableBusTextures.MEUltraDense_Orange.getIcon();
            case Pink:
                return CableBusTextures.MEUltraDense_Pink.getIcon();
            case Purple:
                return CableBusTextures.MEUltraDense_Purple.getIcon();
            case Red:
                return CableBusTextures.MEUltraDense_Red.getIcon();
            case White:
                return CableBusTextures.MEUltraDense_White.getIcon();
            case Yellow:
                return CableBusTextures.MEUltraDense_Yellow.getIcon();
            default:
        }

        return this.getItemStack().getIconIndex();
    }
}
