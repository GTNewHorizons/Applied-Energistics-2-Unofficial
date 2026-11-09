package appeng.api.storage;

import static org.junit.Assert.assertSame;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import appeng.api.config.AccessRestriction;
import appeng.api.networking.IGridNode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.me.storage.NullInventory;

public class ReshuffleAccessCompatibilityTest {

    @Test
    public void combinedConsumerInheritsDefaultWithoutConfiguration() {
        final TestCellContainer consumer = new TestCellContainer();

        assertSame(AccessRestriction.READ_WRITE, ((ICellContainer) consumer).getReshuffleAccess());
        assertSame(AccessRestriction.READ_WRITE, ((IMEInventoryHandler<IAEItemStack>) consumer).getReshuffleAccess());
    }

    @Test
    public void combinedConsumerOverrideAppliesThroughBothInterfaces() {
        for (final AccessRestriction access : AccessRestriction.values()) {
            final TestCellContainer consumer = new TestCellContainer() {

                @Override
                public AccessRestriction getReshuffleAccess() {
                    return access;
                }
            };

            assertSame(access, ((ICellContainer) consumer).getReshuffleAccess());
            assertSame(access, ((IMEInventoryHandler<IAEItemStack>) consumer).getReshuffleAccess());
        }
    }

    // Inherit IMEInventoryHandler through a superclass, as third-party cell containers may do.
    private static class TestCellContainer extends NullInventory<IAEItemStack> implements ICellContainer {

        @Override
        public IGridNode getActionableNode() {
            return null;
        }

        @Override
        public IGridNode getGridNode(ForgeDirection dir) {
            return null;
        }

        @Override
        public AECableType getCableConnectionType(ForgeDirection dir) {
            return AECableType.NONE;
        }

        @Override
        public void securityBreak() {}

        @Override
        public void saveChanges(IMEInventory cellInventory) {}
    }
}
