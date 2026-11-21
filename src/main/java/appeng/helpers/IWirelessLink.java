package appeng.helpers;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;

/** A wireless link endpoint, block or fixture form. All state lives in {@link WirelessLinkLogic}. */
public interface IWirelessLink extends IGridProxyable, IActionHost, ICustomNameObject, IGridTickable {

    WirelessLinkLogic getLinkLogic();

    WirelessAnchor getAnchor();

    default boolean isExternalFixture() {
        return false;
    }

    default AENetworkProxy getLinkProxy() {
        return this.getProxy();
    }

    /** hubs never tick: every link has a connector end that persists both sides and restores on load */
    @Override
    default TickingRequest getTickingRequest(final IGridNode node) {
        // the outer fixture has two nodes; only the one carrying the link drives restoration
        if (node != this.getLinkProxy().getNode()) return null;
        if (this.isHub() || !this.getLinkLogic().needsRestore()) return null;
        return new TickingRequest(TickRates.WirelessLink.getMin(), TickRates.WirelessLink.getMax(), false, false);
    }

    @Override
    default TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        this.getLinkLogic().tick();
        return this.getLinkLogic().needsRestore() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    World getLinkWorld();

    void markLinkDirty();

    void onLinkedStateChanged(boolean linked);

    String getDefaultDisplayName();

    boolean recolourLink(AEColor colour, EntityPlayer who);

    void madChameleonRecolor();

    default AEColor getColor() {
        return this.getLinkLogic().getColor();
    }

    default boolean isHub() {
        return this.getLinkLogic().isHub();
    }

    default boolean isLinked() {
        return this.getLinkLogic().isLinked();
    }

    default int getFreeSlots() {
        return this.getLinkLogic().getFreeSlots();
    }

    default boolean canAddLink() {
        return this.getLinkLogic().canAddLink();
    }

    default boolean canAddLink(final IWirelessLink other) {
        return this.getLinkLogic().canAddLink(other);
    }

    default int getUsedChannels() {
        return this.getLinkLogic().getUsedChannels();
    }

    default double getPowerUsage() {
        return this.getLinkLogic().getPowerUsage();
    }

    default List<IWirelessLink> getConnectedLinks() {
        return this.getLinkLogic().getConnectedLinks();
    }

    default List<DimensionalCoord> getConnectedCoords() {
        return this.getLinkLogic().getConnectedCoords();
    }

    default List<WirelessAnchor> getConnectedAnchors() {
        return this.getLinkLogic().getConnectedAnchors();
    }

    default IGridConnection getConnection(final IWirelessLink other) {
        return this.getLinkLogic().getConnection(other);
    }

    default boolean isConnectedTo(final IWirelessLink other) {
        return this.getLinkLogic().isConnectedTo(other);
    }

    default void addLinkedTarget(final WirelessAnchor anchor) {
        this.getLinkLogic().addLinkedTarget(anchor);
    }

    default void addLinkedTarget(final DimensionalCoord location) {
        this.getLinkLogic().addLinkedTarget(new WirelessAnchor(location, ForgeDirection.UNKNOWN));
    }

    default WirelessToolDataObject getDataForTool(final WirelessAnchor network) {
        return this.getLinkLogic().getDataForTool(network);
    }

    /** teardown for a dying host; player breaks go through {@link WireLessToolHelper#breakConnection} */
    default void unlinkAll() {
        this.getLinkLogic().unlinkAll();
    }
}
