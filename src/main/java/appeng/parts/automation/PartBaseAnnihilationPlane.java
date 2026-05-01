package appeng.parts.automation;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartRenderHelper;
import appeng.client.texture.CableBusTextures;
import appeng.parts.PartBasicState;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class PartBaseAnnihilationPlane extends PartBasicState {

    private static final IIcon SIDE_ICON = CableBusTextures.PartPlaneSides.getIcon();
    private static final IIcon BACK_ICON = CableBusTextures.PartTransitionPlaneBack.getIcon();
    private static final IIcon STATUS_ICON = CableBusTextures.PartMonitorSidesStatus.getIcon();

    public PartBaseAnnihilationPlane(ItemStack is) {
        super(is);
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        int minX = 1;
        int minY = 1;
        int maxX = 15;
        int maxY = 15;

        final IPartHost host = this.getHost();
        if (host != null) {
            final TileEntity te = host.getTile();

            final int x = te.xCoord;
            final int y = te.yCoord;
            final int z = te.zCoord;

            final ForgeDirection e = bch.getWorldX();
            final ForgeDirection u = bch.getWorldY();

            if (this.isAnnihilationPlane(
                    te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                    this.getSide())) {
                minX = 0;
            }

            if (this.isAnnihilationPlane(
                    te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                    this.getSide())) {
                maxX = 16;
            }

            if (this.isAnnihilationPlane(
                    te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                    this.getSide())) {
                minY = 0;
            }

            if (this.isAnnihilationPlane(
                    te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                    this.getSide())) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(minX, minY, 15, maxX, maxY, bch.isBBCollision() ? 15 : 16);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setTexture(SIDE_ICON, SIDE_ICON, BACK_ICON, this.getItemStack().getIconIndex(), SIDE_ICON, SIDE_ICON);

        rh.setBounds(1, 1, 15, 15, 15, 16);
        rh.renderInventoryBox(renderer);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.renderStaticWithIcon(x, y, z, rh, renderer);
    }

    protected void renderStaticWithIcon(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        int minX = 1;

        final ForgeDirection e = rh.getWorldX();
        final ForgeDirection u = rh.getWorldY();

        final TileEntity te = this.getHost().getTile();

        if (this.isAnnihilationPlane(
                te.getWorldObj().getTileEntity(x - e.offsetX, y - e.offsetY, z - e.offsetZ),
                this.getSide())) {
            minX = 0;
        }

        int maxX = 15;
        if (this.isAnnihilationPlane(
                te.getWorldObj().getTileEntity(x + e.offsetX, y + e.offsetY, z + e.offsetZ),
                this.getSide())) {
            maxX = 16;
        }

        int minY = 1;
        if (this.isAnnihilationPlane(
                te.getWorldObj().getTileEntity(x - u.offsetX, y - u.offsetY, z - u.offsetZ),
                this.getSide())) {
            minY = 0;
        }

        int maxY = 15;
        if (this.isAnnihilationPlane(
                te.getWorldObj().getTileEntity(x + u.offsetX, y + u.offsetY, z + u.offsetZ),
                this.getSide())) {
            maxY = 16;
        }

        final boolean isActive = (this.getClientFlags() & (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG))
                == (PartBasicState.POWERED_FLAG | PartBasicState.CHANNEL_FLAG);

        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
        rh.setTexture(
                SIDE_ICON,
                SIDE_ICON,
                BACK_ICON,
                isActive ? this.getActiveIcon() : this.getItemStack().getIconIndex(),
                SIDE_ICON,
                SIDE_ICON);

        rh.setBounds(minX, minY, 15, maxX, maxY, 16);
        rh.renderBlock(x, y, z, renderer);

        rh.setTexture(
                STATUS_ICON,
                STATUS_ICON,
                BACK_ICON,
                isActive ? this.getActiveIcon() : this.getItemStack().getIconIndex(),
                STATUS_ICON,
                STATUS_ICON);

        rh.setBounds(5, 5, 14, 11, 11, 15);
        rh.renderBlock(x, y, z, renderer);

        this.renderLights(x, y, z, rh, renderer);
    }

    @Override
    public int cableConnectionRenderTo() {
        return 1;
    }

    protected boolean isAnnihilationPlane(final TileEntity blockTileEntity, final ForgeDirection side) {
        if (blockTileEntity instanceof IPartHost iph) {
            final IPart p = iph.getPart(side);
            return p instanceof PartBaseAnnihilationPlane;
        }
        return false;
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.onNeighborChanged();
        this.getHost().markForUpdate();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.onNeighborChanged();
        this.getHost().markForUpdate();
    }

    public abstract IIcon getActiveIcon();
}
