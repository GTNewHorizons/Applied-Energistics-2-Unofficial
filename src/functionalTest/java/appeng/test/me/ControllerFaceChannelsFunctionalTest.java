package appeng.test.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.PathingCalculation;
import appeng.test.mockme.MockGridBlock;
import appeng.test.mockme.MockGridMachine;
import appeng.tile.networking.TileController;

public class ControllerFaceChannelsFunctionalTest {

    private final List<GridNode> nodes = new ArrayList<>();

    @AfterEach
    public void destroyGrid() {
        for (final GridNode node : this.nodes) {
            node.destroy();
        }
    }

    @Test
    public void connectionsOnTheSameControllerFaceShareItsChannelCapacity() throws Exception {
        final GridNode controller = this.node(new TileController(), GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);

        for (int i = 0; i < 33; i++) {
            new GridConnection(
                    controller,
                    this.node(new MockGridMachine(), GridFlags.REQUIRE_CHANNEL),
                    ForgeDirection.NORTH);
        }
        new GridConnection(
                controller,
                this.node(new MockGridMachine(), GridFlags.REQUIRE_CHANNEL),
                ForgeDirection.SOUTH);

        final PathingCalculation calculation = new PathingCalculation(controller.getGrid());
        calculation.compute();

        assertEquals(33, calculation.getChannelsInUse());
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
            public boolean hasFlag(final GridFlags flag) {
                return nodeFlags.contains(flag);
            }
        });
        node.updateState();
        this.nodes.add(node);
        return node;
    }
}
