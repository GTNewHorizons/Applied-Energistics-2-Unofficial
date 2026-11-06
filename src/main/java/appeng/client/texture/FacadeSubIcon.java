package appeng.client.texture;

import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.client.render.RenderBlocksWorkaround;

/** Maps absolute block UVs into a Chisel CTM quadrant without stretching a clipped facade segment. */
public final class FacadeSubIcon implements IIcon {

    private final IIcon icon;
    private final ForgeDirection face;
    private final double minU;
    private final double minV;

    public FacadeSubIcon(final IIcon icon, final ForgeDirection face, final double minU, final double minV) {
        this.icon = icon;
        this.face = face;
        this.minU = minU;
        this.minV = minV;
    }

    @Override
    public float getInterpolatedU(final double u) {
        final double blockU = this.face == ForgeDirection.DOWN && RenderBlocksWorkaround.fixedBottomFaceUV ? 16 - u : u;
        return this.icon.getInterpolatedU((blockU - this.minU * 16) * 2);
    }

    @Override
    public float getInterpolatedV(final double v) {
        final double blockV = this.face == ForgeDirection.DOWN ? 16 - v : v;
        return this.icon.getInterpolatedV((blockV - this.minV * 16) * 2);
    }

    @Override
    public int getIconWidth() {
        return this.icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return this.icon.getIconHeight();
    }

    @Override
    public float getMinU() {
        return this.icon.getMinU();
    }

    @Override
    public float getMaxU() {
        return this.icon.getMaxU();
    }

    @Override
    public float getMinV() {
        return this.icon.getMinV();
    }

    @Override
    public float getMaxV() {
        return this.icon.getMaxV();
    }

    @Override
    public String getIconName() {
        return this.icon.getIconName();
    }
}
