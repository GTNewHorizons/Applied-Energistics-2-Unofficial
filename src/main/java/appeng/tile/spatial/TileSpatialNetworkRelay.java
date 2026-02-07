package appeng.tile.spatial;

import java.util.EnumSet;

import appeng.me.GridAccessException;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.exceptions.FailedConnection;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.spatial.SpatialEntangledRegistry;
import appeng.spatial.StorageWorldProvider;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkTile;
import appeng.util.Platform;

/**
 * Network anchor tile used inside spatial storage dimension.
 *
 * It is a normal AE network tile, but it only activates in {@link StorageWorldProvider}. When active, it behaves like a
 * regular AE device so AE cables can connect to it.
 *
 * The actual joining of the "owning" overworld grid is performed by the Grid's spatial-dimension bridge logic (see
 * {@code appeng.me.Grid#add}).
 */
public class TileSpatialNetworkRelay extends AENetworkTile {

    private IGridConnection linkConnection;
    private boolean updateStatus = false;

    private boolean hasConnection = false;

    public TileSpatialNetworkRelay() {
        this.getProxy().setFlags(GridFlags.DENSE_CAPACITY);
        this.getProxy().setValidSides(EnumSet.allOf(ForgeDirection.class));
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileWireless(final ByteBuf data) {
        final boolean old = hasConnection;
        hasConnection = data.readBoolean();
        return old != hasConnection;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileWireless(final ByteBuf data) {
        data.writeBoolean(isConnected());
    }


    @TileEvent(TileEventType.TICK)
    public void onTick() {
        if (this.worldObj != null && !(this.worldObj.provider instanceof StorageWorldProvider)) {
            return;
        }

        if (!Platform.isServer()) {
            return;
        }

        if (updateStatus) {
            updateStatus = false;
            this.updateStatus();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.worldObj != null && Platform.isServer() && this.worldObj.provider instanceof StorageWorldProvider)
            SpatialEntangledRegistry.registerSlave(this.worldObj.provider.dimensionId, this.getActionableNode());
        updateStatus = true;
    }

    public void updateStatus() {
        if (this.worldObj == null) {
            return;
        }

        if (!(this.worldObj.provider instanceof StorageWorldProvider)) return;

        final int storageDim = this.worldObj.provider.dimensionId;
        final IGridNode host = SpatialEntangledRegistry.findHostNode(storageDim);
        final IGridNode myNode = this.getActionableNode();
        if (host == null || myNode == null) {
            this.destroyLink();
            return;
        }

        // If still connected to correct nodes, keep it.
        if (this.linkConnection != null) {
            final IGridNode a = this.linkConnection.a();
            final IGridNode b = this.linkConnection.b();
            if ((a == myNode && b == host) || (a == host && b == myNode)) {
                return;
            }
        }

        this.destroyLink();

        try {
            this.linkConnection = appeng.api.AEApi.instance().createGridConnection(myNode, host);
            this.markForUpdate();
        } catch (final FailedConnection ignored) {
            // we'll retry
        }
    }

    private void destroyLink() {
        if (this.linkConnection != null) {
            this.linkConnection.destroy();
            this.linkConnection = null;
            this.markForUpdate();
        }
    }

    @Override
    public void invalidate() {
        this.destroyLink();
        if (this.worldObj != null && Platform.isServer() && this.worldObj.provider instanceof StorageWorldProvider)
            SpatialEntangledRegistry.unregisterSlave(this.worldObj.provider.dimensionId, this.getActionableNode());
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        this.destroyLink();
        if (this.worldObj != null && Platform.isServer() && this.worldObj.provider instanceof StorageWorldProvider)
            SpatialEntangledRegistry.unregisterSlave(this.worldObj.provider.dimensionId, this.getActionableNode());
        super.onChunkUnload();
    }

    public boolean isConnected(){
        if (Platform.isClient())
            return hasConnection;
        return this.linkConnection != null;
    }

}
