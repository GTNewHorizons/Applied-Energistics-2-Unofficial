package appeng.test.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridConnectionVisitor;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridVisitor;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.test.mockme.MockGridBlock;

public class GridNodeTraversalFunctionalTest {

    private final List<GridNode> nodes = new ArrayList<>();
    private final Map<Object, String> names = new IdentityHashMap<>();
    private GridNode a;
    private GridNode e;

    @BeforeEach
    public void createGraph() throws Exception {
        // A-B-D-C-A is a cycle; B-E-F is a branch that can be pruned at E.
        a = node("A");
        GridNode b = node("B");
        GridNode c = node("C");
        GridNode d = node("D");
        e = node("E");
        GridNode f = node("F");
        connect(a, b, "AB");
        connect(a, c, "AC");
        connect(b, e, "BE");
        connect(b, d, "BD");
        connect(c, d, "CD");
        connect(e, f, "EF");
    }

    @AfterEach
    public void destroyGraph() {
        for (GridNode node : nodes) {
            node.destroy();
        }
    }

    @Test
    public void nodesAreVisitedOnceInBreadthFirstOrder() {
        assertTraversal(false, false, "A", "B", "C", "E", "D", "F");
    }

    @Test
    public void connectionsAreVisitedOnceBeforeTheFollowingNodeLayer() {
        assertTraversal(true, false, "A", "AB", "AC", "B", "C", "BE", "BD", "CD", "E", "D", "EF", "F");
    }

    @Test
    public void nodeVisitorPrunesOnlyTheRejectedBranch() {
        assertTraversal(false, true, "A", "B", "C", "E", "D");
    }

    @Test
    public void connectionVisitorPrunesOnlyTheRejectedBranch() {
        assertTraversal(true, true, "A", "AB", "AC", "B", "C", "BE", "BD", "CD", "E", "D");
    }

    private void assertTraversal(boolean visitConnections, boolean prune, String... expected) {
        List<String> events = new ArrayList<>();
        IGridVisitor nodeVisitor = node -> {
            events.add(names.get(node));
            return !prune || node != e;
        };
        IGridVisitor visitor = visitConnections ? new IGridConnectionVisitor() {

            @Override
            public boolean visitNode(IGridNode node) {
                return nodeVisitor.visitNode(node);
            }

            @Override
            public void visitConnection(IGridConnection connection) {
                events.add(names.get(connection));
            }
        } : nodeVisitor;

        // Reuse the graph and visitor to check that iteration markers are local to each traversal.
        for (int run = 0; run < 2; run++) {
            events.clear();
            a.beginVisit(visitor);
            // Assert after traversal so assertion failures cannot leave crafting rebuilds paused.
            assertEquals(Arrays.asList(expected), events, "Traversal " + run);
        }
    }

    private GridNode node(String name) {
        GridNode node = new GridNode(new MockGridBlock() {

            @Override
            public DimensionalCoord getLocation() {
                return new DimensionalCoord(0, 0, 0, 0);
            }
        });
        nodes.add(node);
        names.put(node, name);
        return node;
    }

    private void connect(GridNode from, GridNode to, String name) throws Exception {
        names.put(new GridConnection(from, to, null), name);
    }
}
