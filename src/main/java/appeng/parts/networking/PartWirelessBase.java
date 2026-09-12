package appeng.parts.networking;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.parts.BusSupport;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.client.texture.CableBusTextures;
import appeng.client.texture.WirelessTextures;
import appeng.helpers.IWirelessLink;
import appeng.helpers.WirelessAnchor;
import appeng.helpers.WirelessLinkLogic;
import appeng.parts.AEBasePart;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/** Inner fixture: the node joins the cable it is attached to, and the outward face is not connectable. */
public abstract class PartWirelessBase extends AEBasePart implements IWirelessLink {

    private final WirelessLinkLogic link;
    private final int textureBase;

    private WirelessAnchor anchor = null;
    private DimensionalCoord location = null;
    private boolean linked = false;

    PartWirelessBase(final ItemStack is, final int maxConnections, final int textureBase) {
        super(is);
        this.link = new WirelessLinkLogic(this, maxConnections);
        this.textureBase = textureBase;
        this.getProxy().setFlags(GridFlags.DENSE_CAPACITY);
    }

    @Override
    public WirelessLinkLogic getLinkLogic() {
        return this.link;
    }

    @Override
    public WirelessAnchor getAnchor() {
        // setPartHostInfo runs before readFromNBT and addToWorld, so side and tile are set by then
        if (this.anchor == null) this.anchor = new WirelessAnchor(this.getLocation(), this.getSide());
        return this.anchor;
    }

    @Override
    public DimensionalCoord getLocation() {
        if (this.location == null) this.location = super.getLocation();
        return this.location;
    }

    @Override
    public World getLinkWorld() {
        final TileEntity te = this.getTile();
        return te == null ? null : te.getWorldObj();
    }

    @Override
    public void markLinkDirty() {
        if (this.getHost() != null) this.saveChanges();
    }

    @Override
    public void onLinkedStateChanged(final boolean isLinked) {
        if (this.linked == isLinked) return;
        this.linked = isLinked;
        if (this.getHost() != null) this.getHost().markForUpdate();
    }

    @Override
    public String getDefaultDisplayName() {
        return this.getItemStack().getDisplayName();
    }

    @Override
    public AEColor getColor() {
        return super.getColor();
    }

    @Override
    public boolean recolourLink(final AEColor colour, final EntityPlayer who) {
        return false;
    }

    @Override
    public void madChameleonRecolor() {}

    @Override
    public void setCustomName(final String name) {
        super.setCustomName(name);
        this.link.propagateCustomName(name);
    }

    @Override
    public void removeFromWorld() {
        this.link.breakAllActiveConnections();
        super.removeFromWorld();
    }

    /** only reached when the part is actually destroyed, not on chunk unload */
    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        this.link.unlinkAll();
        super.getDrops(drops, wrenched);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.link.tick();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.link.writeToNBT(data);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.link.readFromNBT(data);
        this.linked = this.link.isLinked();
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.linked);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean changed = super.readFromStream(data);
        final boolean wasLinked = this.linked;
        this.linked = data.readBoolean();
        return changed || wasLinked != this.linked;
    }

    @Override
    public boolean canBePlacedOn(final BusSupport what) {
        return what == BusSupport.CABLE || what == BusSupport.DENSE_CABLE;
    }

    private static final float[][] TIERS = { { 2, 2, 13, 14, 14, 15 } };

    private static final float[] ANCHOR = { 7, 7, 15, 9, 9, 16 };

    protected float[][] getModelTiers() {
        return TIERS;
    }

    /** the side this fixture carries nothing on: outward for inner, cable side for outer */
    protected float[] getAnchorBox() {
        return ANCHOR;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        for (final float[] b : this.getModelTiers()) {
            bch.addBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        }

        final float[] a = this.getAnchorBox();
        bch.addBox(a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    @SideOnly(Side.CLIENT)
    private static IIcon anchorIcon() {
        for (final ItemStack anchor : AEApi.instance().definitions().parts().cableAnchor().maybeStack(1).asSet()) {
            return anchor.getIconIndex();
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    private IIcon getIcon() {
        final WirelessTextures tex = WirelessTextures.values()[this.textureBase + this.getColor().ordinal()];
        return this.linked ? tex.getFixtureOnIcon() : tex.getFixtureOffIcon();
    }

    @SideOnly(Side.CLIENT)
    private void renderModel(final IPartRenderHelper rh, final IIcon body, final Runnable emit) {
        rh.setTexture(anchorIcon());
        final float[] a = this.getAnchorBox();
        rh.setBounds(a[0], a[1], a[2], a[3], a[4], a[5]);
        emit.run();

        final IIcon sides = CableBusTextures.PartWirelessFixtureSides.getIcon();
        rh.setTexture(sides, sides, body, body, sides, sides);
        for (final float[] b : this.getModelTiers()) {
            rh.setBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
            emit.run();
        }

        rh.setTexture(null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        this.renderModel(rh, this.getItemStack().getIconIndex(), () -> rh.renderInventoryBox(renderer));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
            final RenderBlocks renderer) {
        this.renderModel(rh, this.getIcon(), () -> rh.renderBlock(x, y, z, renderer));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getBreakingTexture() {
        return this.getIcon();
    }

    /** 16 minus the body's innermost z, as PartExportBus does */
    @Override
    public int cableConnectionRenderTo() {
        return 3;
    }

    @Override
    public boolean isSolid() {
        return true;
    }
}
