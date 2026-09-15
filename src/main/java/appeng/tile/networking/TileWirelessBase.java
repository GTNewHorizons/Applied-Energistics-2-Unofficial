/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.tile.networking;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.GridFlags;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IWirelessLink;
import appeng.helpers.WirelessAnchor;
import appeng.helpers.WirelessLinkLogic;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import io.netty.buffer.ByteBuf;

public abstract class TileWirelessBase extends AENetworkTile implements IColorableTile, IWirelessLink {

    private final WirelessLinkLogic link;

    private DimensionalCoord location = null;
    private WirelessAnchor anchor = null;

    TileWirelessBase(final int maxConnections) {
        this.link = new WirelessLinkLogic(this, maxConnections);
    }

    @Override
    public WirelessLinkLogic getLinkLogic() {
        return this.link;
    }

    @Override
    public WirelessAnchor getAnchor() {
        if (this.anchor == null) this.anchor = new WirelessAnchor(this.getLocation(), ForgeDirection.UNKNOWN);
        return this.anchor;
    }

    @Override
    public World getLinkWorld() {
        return this.worldObj;
    }

    @Override
    public void markLinkDirty() {
        this.markDirty();
    }

    @Override
    public void onLinkedStateChanged(final boolean linked) {
        if (this.worldObj == null) return;
        this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, linked ? 1 : 0, 3);
    }

    @Override
    public String getDefaultDisplayName() {
        return this.getBlockType().getLocalizedName();
    }

    @Override
    public DimensionalCoord getLocation() {
        if (this.location == null) this.location = new DimensionalCoord(this);
        return this.location;
    }

    @Override
    protected AENetworkProxy createProxy() {
        final AENetworkProxy ae = super.createProxy();
        ae.setFlags(GridFlags.DENSE_CAPACITY);
        return ae;
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    // explicit: IColorableTile's abstract getColor() suppresses the IWirelessLink default
    @Override
    public AEColor getColor() {
        return this.link.getColor();
    }

    @Override
    public boolean recolourBlock(final ForgeDirection side, final AEColor colour, final EntityPlayer who) {
        return this.recolourLink(colour, who);
    }

    @Override
    public boolean recolourLink(final AEColor colour, final EntityPlayer who) {
        if (!this.link.applyColor(colour)) return false;

        this.markDirty();
        this.markForUpdate();
        return true;
    }

    @Override
    public void madChameleonRecolor() {
        if (!this.link.madChameleonRecolor()) return;

        this.markDirty();
        this.markForUpdate();
    }

    @Override
    public void setCustomName(final String name) {
        super.setCustomName(name);
        this.link.propagateCustomName(name);
    }

    @Override
    public void onReady() {
        super.onReady();
        this.link.tick();
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileSecurity(final ByteBuf data) {
        return this.link.setColorFromStream(AEColor.VALUES[data.readByte()]);
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileSecurity(final ByteBuf data) {
        data.writeByte(this.link.getColor().ordinal());
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileWirelessConnector(final NBTTagCompound data) {
        this.link.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileWirelessConnector(final NBTTagCompound data) {
        this.link.readFromNBT(data);
    }

    @Override
    public void onChunkUnload() {
        this.link.breakAllActiveConnections();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        this.link.breakAllActiveConnections();
        super.invalidate();
    }
}
