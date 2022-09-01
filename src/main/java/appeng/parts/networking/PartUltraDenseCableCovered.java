package appeng.parts.networking;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.texture.CableBusTextures;
import appeng.helpers.Reflected;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

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
            return AEApi.instance()
                    .definitions()
                    .parts()
                    .cableUltraDenseCovered()
                    .stack(AEColor.Transparent, 1)
                    .getIconIndex();
        }

        return this.getDenseCoveredTexture(c);
    }

    @Override
    protected IIcon getDenseCoveredTexture(final AEColor c) {
        switch (c) {
            case Black:
                return CableBusTextures.MEUltraDenseCovered_Black.getIcon();
            case Blue:
                return CableBusTextures.MEUltraDenseCovered_Blue.getIcon();
            case Brown:
                return CableBusTextures.MEUltraDenseCovered_Brown.getIcon();
            case Cyan:
                return CableBusTextures.MEUltraDenseCovered_Cyan.getIcon();
            case Gray:
                return CableBusTextures.MEUltraDenseCovered_Gray.getIcon();
            case Green:
                return CableBusTextures.MEUltraDenseCovered_Green.getIcon();
            case LightBlue:
                return CableBusTextures.MEUltraDenseCovered_LightBlue.getIcon();
            case LightGray:
                return CableBusTextures.MEUltraDenseCovered_LightGrey.getIcon();
            case Lime:
                return CableBusTextures.MEUltraDenseCovered_Lime.getIcon();
            case Magenta:
                return CableBusTextures.MEUltraDenseCovered_Magenta.getIcon();
            case Orange:
                return CableBusTextures.MEUltraDenseCovered_Orange.getIcon();
            case Pink:
                return CableBusTextures.MEUltraDenseCovered_Pink.getIcon();
            case Purple:
                return CableBusTextures.MEUltraDenseCovered_Purple.getIcon();
            case Red:
                return CableBusTextures.MEUltraDenseCovered_Red.getIcon();
            case White:
                return CableBusTextures.MEUltraDenseCovered_White.getIcon();
            case Yellow:
                return CableBusTextures.MEUltraDenseCovered_Yellow.getIcon();
            default:
        }

        return this.getItemStack().getIconIndex();
    }
}
