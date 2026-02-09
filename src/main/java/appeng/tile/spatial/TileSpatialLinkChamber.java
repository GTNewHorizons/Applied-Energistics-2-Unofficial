package appeng.tile.spatial;

import java.util.EnumSet;

import appeng.api.networking.IGridNode;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.items.ISpatialStorageCell;
import appeng.api.networking.GridFlags;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.spatial.SpatialEntangledRegistry;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

/**
 * Overworld-side endpoint. Holds a Spatial Storage Cell and exposes the owning storage dimension id.
 *
 * The actual node-to-node connection is created by the spatial-side anchor.
 */
public class TileSpatialLinkChamber extends AENetworkInvTile {

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 1);

    private int cachedStorageDim = 0;

    private final int[] accessibleSlots = { 0 };

    private boolean updateStatus = false;

    public TileSpatialLinkChamber() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY);
        this.getProxy().setIdlePowerUsage(2);
        this.getProxy().setValidSides(EnumSet.allOf(ForgeDirection.class));
        this.inv.setMaxStackSize(1);
    }

    @Override
    public IInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public boolean isItemValidForSlot(final int slot, final ItemStack itemstack) {
        return slot == 0 && itemstack != null && itemstack.getItem() instanceof ISpatialStorageCell;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        this.updateBinding();
    }

    @MENetworkEventSubscribe
    public void onPowerStatusChange(final MENetworkPowerStatusChange c) {
        updateStatus = true;
    }

    @Override
    public void onReady() {
        super.onReady();
        //updateBinding();
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileWireless(final ByteBuf data) {
        final int old = cachedStorageDim;
        cachedStorageDim = data.readInt();
        return old != cachedStorageDim;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileWireless(final ByteBuf data) {
        data.writeInt(cachedStorageDim);
    }

    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (!Platform.isServer()) {
            return;
        }

        if (updateStatus) {
            updateStatus = false;
            this.updateBinding();
        }
    }

    private int getStorageDimFromCell(final ItemStack is) {
        if (is == null || !(is.getItem() instanceof ISpatialStorageCell)) {
            return 0;
        }
        final NBTTagCompound tag = Platform.openNbtData(is);
        return tag != null ? tag.getInteger("StorageDim") : 0;
    }

    private void updateBinding() {
        if (!Platform.isServer()) {
            return;
        }

        final IGridNode myNode = this.getActionableNode();

        if (myNode == null) return;

        if (!myNode.isActive()) return;

        int newDim = 0;
        final ItemStack cell = this.inv.getStackInSlot(0);
        if (cell != null) {
            newDim = getStorageDimFromCell(cell);
        }

        // Update host binding if dimension changed.

        if (this.cachedStorageDim != newDim) {
            SpatialEntangledRegistry.unbindHost(this.cachedStorageDim);
            if (newDim != 0)
                SpatialEntangledRegistry.bindHost(newDim, this.getActionableNode());
            this.cachedStorageDim = newDim;
            this.markForUpdate();
        }
    }

    public boolean hasBoundStorage() {
        return this.cachedStorageDim != 0;
    }

    public void teleportInside(EntityPlayerMP playerMP){
        if (cachedStorageDim == 0) return;
        playerMP.mcServer.getConfigurationManager().transferPlayerToDimension(
                playerMP,
                cachedStorageDim);
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return AECableType.SMART;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public String getCustomName() {
        ItemStack s = this.getStackInSlot(0);
        return s != null ? s.getDisplayName() : super.getCustomName();
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return this.accessibleSlots;
    }

    @Override
    public boolean canInsertItem(final int slotIndex, final ItemStack insertingItem, final int side) {
        return this.isItemValidForSlot(slotIndex, insertingItem);
    }

    @Override
    public boolean canExtractItem(final int slotIndex, final ItemStack extractedItem, final int side) {
        return slotIndex == 0;
    }

    @Override
    public void invalidate() {
        if (cachedStorageDim != 0)
            SpatialEntangledRegistry.unbindHost(this.cachedStorageDim);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (cachedStorageDim != 0)
            SpatialEntangledRegistry.unbindHost(this.cachedStorageDim);
        super.onChunkUnload();
    }
}
