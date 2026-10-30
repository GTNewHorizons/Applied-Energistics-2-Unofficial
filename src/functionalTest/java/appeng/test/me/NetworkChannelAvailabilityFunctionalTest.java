package appeng.test.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.AdHocChannelUpdater;
import appeng.me.pathfinding.PathingCalculation;
import appeng.test.mockme.MockGridBlock;
import appeng.test.mockme.MockGridMachine;
import appeng.tile.networking.TileController;
import appeng.util.NetworkChannelAvailability;

public class NetworkChannelAvailabilityFunctionalTest {

    private final List<GridNode> nodes = new ArrayList<>();

    @AfterEach
    public void destroyGrid() {
        for (GridNode node : this.nodes) {
            node.destroy();
        }
    }

    @Test
    public void normalCableReportsRemainingChannels() throws Exception {
        GridNode controller = controller();
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridConnection link = connect(controller, cable);
        addDevices(cable, 3);
        calculate(controller);

        assertEquals(5, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(link));
    }

    @Test
    public void denseCableReportsRemainingChannelsOnControllerFace() throws Exception {
        GridNode controller = controller();
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridConnection link = connect(controller, cable);
        addDevices(cable, 12);
        calculate(controller);

        assertEquals(20, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(link));
    }

    @Test
    public void connectionsShareTheRemainingControllerFaceCapacity() throws Exception {
        GridNode controller = controller();
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridConnection link = connect(controller, cable);
        addDevices(controller, 20);
        addDevices(cable, 5);
        calculate(controller);

        assertEquals(7, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(link));
    }

    @Test
    public void denseCableRespectsUpstreamNormalCableBottleneck() throws Exception {
        GridNode controller = controller();
        GridNode normal = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridNode dense = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridNode downstream = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        connect(controller, normal);
        connect(normal, dense);
        GridConnection link = connect(dense, downstream);
        addDevices(downstream, 3);
        calculate(controller);

        assertEquals(5, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(link));
    }

    @Test
    public void quartzFiberCannotSupplyChannels() throws Exception {
        GridNode controller = controller();
        GridNode fiber = node(new MockGridMachine(), GridFlags.CANNOT_CARRY);
        GridConnection link = connect(controller, fiber);
        calculate(controller);

        assertEquals(0, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(link));
    }

    @Test
    public void overloadedAdHocNetworkHasNoFreeChannels() throws Exception {
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridNode downstream = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridConnection link = connect(cable, downstream);
        addDevices(downstream, 9);
        cable.beginVisit(new AdHocChannelUpdater(0));
        finalizeChannels();

        assertEquals(0, link.getUsedChannels());
        assertEquals(0, availability(cable, ControllerState.NO_CONTROLLER).getFreeChannels(link));
    }

    @Test
    public void healthyAdHocNetworkReportsItsSharedRemainingChannels() throws Exception {
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridNode downstream = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridConnection link = connect(cable, downstream);
        addDevices(downstream, 3);
        cable.beginVisit(new AdHocChannelUpdater(3));
        finalizeChannels();

        assertEquals(5, availability(cable, ControllerState.NO_CONTROLLER).getFreeChannels(link));
    }

    @Test
    public void emptyAdHocNetworkStillHasEightChannels() throws Exception {
        GridNode cable = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridNode downstream = node(new MockGridMachine(), GridFlags.PREFERRED, GridFlags.DENSE_CAPACITY);
        GridConnection link = connect(cable, downstream);

        assertEquals(8, availability(cable, ControllerState.NO_CONTROLLER).getFreeChannels(link));
    }

    @Test
    @Timeout(2)
    public void removedParentRouteIsUnavailableUntilRepath() throws Exception {
        GridNode controller = controller();
        GridNode a = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridNode b = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridNode d = node(new MockGridMachine(), GridFlags.PREFERRED);
        GridNode alternate = node(new MockGridMachine(), GridFlags.PREFERRED);
        connect(controller, a);
        GridConnection removed = connect(a, b);
        GridConnection downstream = connect(b, d);
        connect(a, alternate);
        connect(alternate, b);
        calculate(controller);

        removed.destroy();
        assertSame(controller.getGrid(), b.getGrid(), "Alternate route keeps the grid connected");
        assertSame(downstream, b.getControllerRoute(), "Removing the parent exposes a stale child route");
        assertEquals(-1, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(downstream));

        calculate(controller);
        assertEquals(8, availability(controller, ControllerState.CONTROLLER_ONLINE).getFreeChannels(downstream));
    }

    private GridNode controller() {
        return node(new TileController(), GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
    }

    private void addDevices(GridNode cable, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            connect(cable, node(new MockGridMachine(), GridFlags.REQUIRE_CHANNEL));
        }
    }

    private GridConnection connect(GridNode a, GridNode b) throws Exception {
        return new GridConnection(a, b, ForgeDirection.NORTH);
    }

    private void calculate(GridNode root) {
        new PathingCalculation(root.getGrid()).compute();
        finalizeChannels();
    }

    private void finalizeChannels() {
        for (GridNode node : this.nodes) {
            node.finalizeChannels();
            for (var connection : node.getConnections()) {
                ((GridConnection) connection).finalizeChannels();
            }
        }
    }

    private NetworkChannelAvailability availability(GridNode root, ControllerState state) {
        // Use real nodes and channel assignment, without requiring a formed controller tile in a world.
        IPathingGrid pathing = (IPathingGrid) Proxy.newProxyInstance(
                IPathingGrid.class.getClassLoader(),
                new Class<?>[] { IPathingGrid.class },
                (proxy, method, args) -> switch (method.getName()) {
                case "getControllerState" -> state;
                case "isNetworkBooting" -> false;
                default -> throw new AssertionError(method.getName());
                });
        IGrid grid = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(),
                new Class<?>[] { IGrid.class },
                (proxy, method, args) -> switch (method.getName()) {
                case "getCache" -> pathing;
                case "getNodes" -> root.getGrid().getNodes();
                default -> throw new AssertionError(method.getName());
                });
        return new NetworkChannelAvailability(grid);
    }

    private GridNode node(final IGridHost machine, final GridFlags... flags) {
        final EnumSet<GridFlags> nodeFlags = flags.length == 0 ? EnumSet.noneOf(GridFlags.class)
                : EnumSet.copyOf(Arrays.asList(flags));
        final GridNode node = new GridNode(new MockGridBlock() {

            @Override
            public IGridHost getMachine() {
                return machine;
            }

            @Override
            public EnumSet<GridFlags> getFlags() {
                return nodeFlags;
            }

            @Override
            public boolean hasFlag(GridFlags flag) {
                return nodeFlags.contains(flag);
            }
        });
        node.updateState();
        this.nodes.add(node);
        return node;
    }
}
