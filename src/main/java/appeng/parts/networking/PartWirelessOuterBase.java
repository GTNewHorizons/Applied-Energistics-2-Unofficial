package appeng.parts.networking;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.me.helpers.AENetworkProxy;

/**
 * Outer fixture: the link rides an outward facing node, so the host cable stays out of the remote network while
 * external cables can connect to the exposed face. Works with no host cable at all.
 */
public abstract class PartWirelessOuterBase extends PartWirelessBase {

    private final AENetworkProxy outerProxy = new AENetworkProxy(this, "outer", null, true);

    PartWirelessOuterBase(final ItemStack is, final int maxConnections, final int textureBase) {
        super(is, maxConnections, textureBase);
        this.outerProxy.setFlags(GridFlags.DENSE_CAPACITY);
        // the inner node carries nothing, so it must not bill the host grid the default 1 AE/t
        this.getProxy().setIdlePowerUsage(0);
    }

    @Override
    public boolean isExternalFixture() {
        return true;
    }

    @Override
    public AENetworkProxy getLinkProxy() {
        return this.outerProxy;
    }

    /** cosmetic tint read straight off whatever sits in front of this face, so nothing needs syncing */
    @Override
    public AEColor getColor() {
        final TileEntity te = this.getTile();
        final ForgeDirection s = this.getSide();
        if (te == null || s == null || te.getWorldObj() == null) return AEColor.Transparent;

        final TileEntity next = te.getWorldObj()
                .getTileEntity(te.xCoord + s.offsetX, te.yCoord + s.offsetY, te.zCoord + s.offsetZ);
        if (!(next instanceof IColorableTile colorable)) return AEColor.Transparent;

        // a part on the facing side takes that side out of the cable's valid sides, so its colour never reaches us
        if (next instanceof IPartHost host && host.getPart(s.getOpposite()) != null) return AEColor.Transparent;

        return colorable.getColor();
    }

    @Override
    public IGridNode getExternalFacingNode() {
        return this.outerProxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.DENSE;
    }

    /** >8 means no stub rendered; the host cable is not on this fixture's network */
    @Override
    public int cableConnectionRenderTo() {
        return 16;
    }

    private static final float[][] TIERS = { { 2, 2, 14, 14, 14, 16 } };

    private static final float[] ANCHOR = { 7, 7, 10, 9, 9, 14 };

    @Override
    protected float[][] getModelTiers() {
        return TIERS;
    }

    @Override
    protected float[] getAnchorBox() {
        return ANCHOR;
    }

    @Override
    public void setPartHostInfo(final ForgeDirection side, final IPartHost host, final TileEntity tile) {
        super.setPartHostInfo(side, host, tile);
        this.outerProxy.setValidSides(EnumSet.of(side));
    }

    @Override
    public void onPlacement(final EntityPlayer player, final ItemStack held, final ForgeDirection side) {
        super.onPlacement(player, held, side);
        this.outerProxy.setOwner(player);
    }

    @Override
    public void addToWorld() {
        this.outerProxy.onReady();
        super.addToWorld();
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        this.outerProxy.invalidate();
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.outerProxy.readFromNBT(data);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.outerProxy.writeToNBT(data);
    }
}
