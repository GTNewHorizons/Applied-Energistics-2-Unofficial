package appeng.helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableList;

import appeng.api.AEApi;
import appeng.api.config.PowerMultiplier;
import appeng.api.exceptions.ExistingConnectionException;
import appeng.api.exceptions.FailedConnection;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.MachineSource;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.helpers.WireLessToolHelper.BindResult;
import appeng.util.Platform;

/**
 * All wireless link state and behavior, host-agnostic. {@code linkedTargets} is the persisted wiring;
 * {@code activeConnections} are the live grid connections, torn down on unload and rebuilt from the wiring.
 */
public class WirelessLinkLogic {

    private final IWirelessLink owner;
    private final int maxConnections;

    private final Set<WirelessAnchor> linkedTargets = new LinkedHashSet<>();
    private final Map<WirelessAnchor, ActiveConnection> activeConnections = new LinkedHashMap<>();

    private AEColor color = AEColor.Transparent;

    public WirelessLinkLogic(final IWirelessLink owner, final int maxConnections) {
        this.owner = owner;
        this.maxConnections = maxConnections;
    }

    public boolean isHub() {
        return this.maxConnections > 1;
    }

    public int getMaxConnections() {
        return this.maxConnections;
    }

    public boolean isLinked() {
        return !this.linkedTargets.isEmpty();
    }

    public int getFreeSlots() {
        return this.maxConnections - this.linkedTargets.size();
    }

    public boolean canAddLink() {
        return !this.isHub() || this.getFreeSlots() > 0;
    }

    public boolean canAddLink(final IWirelessLink other) {
        return this.hasLinkedTarget(other.getAnchor()) || this.canAddLink();
    }

    public int getUsedChannels() {
        final IGridNode node = this.owner.getLinkProxy().getNode();
        if (node == null) return 0;

        int used = 0;
        for (final IGridConnection connection : node.getConnections()) {
            used = Math.max(used, connection.getUsedChannels());
        }
        return used;
    }

    public List<IWirelessLink> getConnectedLinks() {
        final List<IWirelessLink> links = new ArrayList<>(this.activeConnections.size());
        for (final ActiveConnection connection : this.activeConnections.values()) {
            links.add(connection.target);
        }
        return ImmutableList.copyOf(links);
    }

    public List<WirelessAnchor> getConnectedAnchors() {
        return ImmutableList.copyOf(this.linkedTargets);
    }

    public List<DimensionalCoord> getConnectedCoords() {
        final List<DimensionalCoord> coords = new ArrayList<>(this.linkedTargets.size());
        for (final WirelessAnchor anchor : this.linkedTargets) {
            coords.add(anchor.getCoord());
        }
        return ImmutableList.copyOf(coords);
    }

    @Nullable
    public IGridConnection getConnection(final IWirelessLink other) {
        final ActiveConnection connection = this.activeConnections.get(other.getAnchor());
        if (connection == null || connection.target != other) return null;
        return connection.connection;
    }

    public boolean isConnectedTo(final IWirelessLink other) {
        return this.getConnection(other) != null && other.getConnection(this.owner) != null;
    }

    public void addActiveConnection(final IWirelessLink other, final IGridConnection connection) {
        if (!this.isHub() && !this.activeConnections.isEmpty()) this.breakAllActiveConnections();

        final WirelessAnchor anchor = other.getAnchor();
        if (this.activeConnections.containsKey(anchor)) {
            throw new IllegalStateException("Active connection already registered");
        }

        this.activeConnections.put(anchor, new ActiveConnection(other, connection));
    }

    public void removeActiveConnection(final IWirelessLink other) {
        this.activeConnections.remove(other.getAnchor());
    }

    public void removeActiveConnectionTo(final WirelessAnchor anchor) {
        final ActiveConnection existing = this.activeConnections.get(anchor);
        if (existing != null) {
            this.breakActiveConnection(existing.target);
        }
    }

    public void breakActiveConnection(final IWirelessLink other) {
        final IGridConnection connection = this.getConnection(other);
        if (connection != null) connection.destroy();
        this.removeActiveConnection(other);
        other.getLinkLogic().removeActiveConnection(this.owner);
        this.updateActiveIfLoaded();
        other.getLinkLogic().updateActiveIfLoaded();
    }

    public void breakAllActiveConnections() {
        for (final IWirelessLink other : this.getConnectedLinks()) {
            this.breakActiveConnection(other);
        }
    }

    BindResult doLink(final IWirelessLink other) {
        if (!this.isHub()) this.breakAllLinks();
        return this.setupConnection(other, false);
    }

    BindResult restoreLink(final IWirelessLink other) {
        return this.setupConnection(other, true);
    }

    void unlink(final IWirelessLink other) {
        if (this.hasLinkedTarget(other.getAnchor())) {
            this.breakLink(other);
        }
    }

    public void unlinkAll() {
        this.breakAllLinks();
    }

    private BindResult setupConnection(final IWirelessLink other, final boolean restoring) {
        // same-position fixtures on different faces are distinct endpoints and may link, e.g. as a relay
        if (this.owner == other || this.owner.getAnchor().equals(other.getAnchor())) return BindResult.INVALID_SOURCE;
        if (this.isConnectedTo(other)) return BindResult.ALREADY_BIND;

        this.removeActiveConnectionTo(other.getAnchor());
        other.getLinkLogic().removeActiveConnectionTo(this.owner.getAnchor());

        if (!this.canAddLink(other)) return BindResult.INVALID_SOURCE;

        try {
            final IGridNode selfNode = this.owner.getLinkProxy().getNode();
            final IGridNode targetNode = other.getLinkProxy().getNode();

            if (selfNode == null) return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.INVALID_SOURCE;
            if (targetNode == null) return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.INVALID_SOURCE;

            final IGridConnection connection = AEApi.instance().createGridConnection(selfNode, targetNode);

            this.addActiveConnection(other, connection);
            other.getLinkLogic().addActiveConnection(this.owner, connection);
            this.addLinkedTarget(other.getAnchor());
            other.addLinkedTarget(this.owner.getAnchor());
            this.updateActive();
            other.getLinkLogic().updateActive();
            this.shareCustomName(other);

            return BindResult.SUCCESS;
        } catch (final ExistingConnectionException e) {
            return BindResult.ALREADY_BIND;
        } catch (final SecurityConnectionException e) {
            return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.FAILED;
        } catch (final FailedConnection e) {
            return restoring ? BindResult.TEMPORARY_FAILURE : BindResult.FAILED;
        }
    }

    private void breakLink(final IWirelessLink other) {
        this.removeLinkedTarget(other.getAnchor());
        other.getLinkLogic().removeLinkedTarget(this.owner.getAnchor());
        this.breakActiveConnection(other);
    }

    private void breakAllLinks() {
        for (final IWirelessLink other : this.getConnectedLinks()) {
            other.getLinkLogic().removeLinkedTarget(this.owner.getAnchor());
        }

        this.clearLinkedTargets();

        for (final IWirelessLink other : this.getConnectedLinks()) {
            this.breakActiveConnection(other);
        }

        this.updateActiveIfLoaded();
    }

    public boolean hasLinkedTarget(final WirelessAnchor anchor) {
        return this.linkedTargets.contains(anchor);
    }

    public void addLinkedTarget(final WirelessAnchor anchor) {
        this.addLinkedTarget(anchor, true);
    }

    private void addLinkedTarget(final WirelessAnchor anchor, final boolean notifyDirty) {
        if (this.owner.getLinkWorld() != null && anchor.equals(this.owner.getAnchor())) return;

        if (this.linkedTargets.contains(anchor)) return;

        if (!this.isHub() && !this.linkedTargets.isEmpty()) return;
        if (this.linkedTargets.size() >= this.maxConnections) return;

        this.linkedTargets.add(anchor);
        if (notifyDirty) this.owner.markLinkDirty();
    }

    public void removeLinkedTarget(final WirelessAnchor anchor) {
        this.linkedTargets.remove(anchor);
        this.owner.markLinkDirty();
    }

    private void clearLinkedTargets() {
        if (this.linkedTargets.isEmpty()) return;
        this.linkedTargets.clear();
        this.owner.markLinkDirty();
    }

    public void tick() {
        if (!Platform.isServer() || this.linkedTargets.isEmpty()) return;
        this.tryRestoreConnection();
    }

    public boolean needsRestore() {
        return this.activeConnections.size() < this.linkedTargets.size();
    }

    private void tryRestoreConnection() {
        if (this.isHub()) {
            for (final WirelessAnchor target : ImmutableList.copyOf(this.linkedTargets)) {
                final IWirelessLink link = this.getTargetOrRemoveLink(target);
                if (link == null) continue;
                if (!this.isConnectedTo(link)) {
                    WireLessToolHelper.restoreConnection(link, this.owner, new MachineSource(this.owner));
                }
            }
        } else {
            if (!this.activeConnections.isEmpty()) return;

            for (final WirelessAnchor target : ImmutableList.copyOf(this.linkedTargets)) {
                final IWirelessLink link = this.getTargetOrRemoveLink(target);
                if (link != null) {
                    WireLessToolHelper.restoreConnection(link, this.owner, new MachineSource(this.owner));
                }
                return;
            }
        }
    }

    @Nullable
    private IWirelessLink getTargetOrRemoveLink(final WirelessAnchor target) {
        final World w = this.owner.getLinkWorld();
        if (w == null) return null;

        if (target.getDimension() != w.provider.dimensionId) {
            this.removeLinkedTarget(target);
            return null;
        }

        final DimensionalCoord coord = target.getCoord();
        if (!w.blockExists(coord.x, coord.y, coord.z)) return null;

        final IWirelessLink link = WireLessToolHelper.resolveLink(target, w);

        // ae2stuff persisted hub links only on the connector side.
        if (link != null && (link.isHub() || link.getLinkLogic().hasLinkedTarget(this.owner.getAnchor()))) {
            return link;
        }

        this.removeLinkedTarget(target);
        return null;
    }

    public void setConnectionsPowerDraw() {
        final DimensionalCoord self = this.owner.getLocation();
        final double idlePowerUse = this.getConnectedLinks().stream().mapToDouble(link -> {
            final DimensionalCoord loc = link.getLocation();
            final int dx = self.x - loc.x;
            final int dy = self.y - loc.y;
            final int dz = self.z - loc.z;
            final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return AEConfig.instance.getWirelessConnectorPowerBase()
                    + AEConfig.instance.getWirelessConnectorPowerDistanceMultiplier() * dist
                            * Math.log(dist * dist + 3);
        }).sum();
        this.owner.getLinkProxy().setIdlePowerUsage(idlePowerUse);
    }

    public double getPowerUsage() {
        return PowerMultiplier.CONFIG.multiply(this.owner.getLinkProxy().getIdlePowerUsage());
    }

    public void updateActive() {
        this.setConnectionsPowerDraw();
        this.owner.onLinkedStateChanged(this.isLinked());
    }

    public void updateActiveIfLoaded() {
        final World w = this.owner.getLinkWorld();
        if (w == null) return;

        final DimensionalCoord loc = this.owner.getLocation();
        if (w.blockExists(loc.x, loc.y, loc.z)) this.updateActive();
    }

    public AEColor getColor() {
        return this.color;
    }

    public boolean applyColor(final AEColor colour) {
        if (this.color == colour) return false;

        this.color = colour;
        this.owner.getLinkProxy().setColor(this.color);

        final IGridNode node = this.owner.getLinkProxy().getNode();
        if (node != null) node.updateState();

        return true;
    }

    public boolean setColorFromStream(final AEColor colour) {
        final AEColor old = this.color;
        this.color = colour;
        return old != this.color;
    }

    public boolean madChameleonRecolor() {
        final World w = this.owner.getLinkWorld();
        if (w == null) return false;

        final DimensionalCoord dc = this.owner.getLocation();
        final List<Integer> used = new ArrayList<>();
        int i = 0;

        for (final ForgeDirection fd : ForgeDirection.VALID_DIRECTIONS) {
            final TileEntity te = w.getTileEntity(dc.x + fd.offsetX, dc.y + fd.offsetY, dc.z + fd.offsetZ);
            if (te instanceof IWirelessLink link) {
                used.add(link.getColor().ordinal());
                while (used.contains(i)) {
                    i++;
                }
            }
        }

        return this.applyColor(AEColor.VALUES[i]);
    }

    private void shareCustomName(final IWirelessLink other) {
        if (other.hasCustomName()) this.owner.setCustomName(other.getCustomName());
        else if (this.owner.hasCustomName()) other.setCustomName(this.owner.getCustomName());
    }

    public void propagateCustomName(final String name) {
        for (final IWirelessLink other : this.getConnectedLinks()) {
            if ((name == null || name.isEmpty()) && !other.hasCustomName()
                    || Objects.equals(other.getCustomName(), name))
                continue;
            other.setCustomName(name);
        }
    }

    public void writeToNBT(final NBTTagCompound data) {
        data.setShort("Color", (short) this.color.ordinal());

        final NBTTagCompound nbt = new NBTTagCompound();
        WirelessAnchor.writeListToNBT(nbt, new ArrayList<>(this.linkedTargets));
        data.setTag("connectedTargets", nbt);
    }

    public void readFromNBT(final NBTTagCompound data) {
        if (data.hasKey("Color")) {
            this.color = AEColor.VALUES[data.getShort("Color")];
            this.owner.getLinkProxy().setColor(this.color);
        }

        this.linkedTargets.clear();
        for (final WirelessAnchor target : WirelessAnchor.readAsListFromNBT(data.getCompoundTag("connectedTargets"))) {
            if (this.isHub() || this.linkedTargets.isEmpty()) this.addLinkedTarget(target, false);
        }
    }

    public WirelessToolDataObject getDataForTool(final WirelessAnchor network) {
        return new WirelessToolDataObject(
                network,
                this.owner.hasCustomName() ? this.owner.getCustomName() : this.owner.getDefaultDisplayName(),
                this.owner.getAnchor(),
                this.isLinked(),
                this.getConnectedAnchors(),
                this.owner.getColor(),
                this.getUsedChannels(),
                this.isHub(),
                this.owner.isExternalFixture(),
                this.getFreeSlots());
    }

    private static final class ActiveConnection {

        private final IWirelessLink target;
        private final IGridConnection connection;

        private ActiveConnection(final IWirelessLink target, final IGridConnection connection) {
            this.target = target;
            this.connection = connection;
        }
    }
}
