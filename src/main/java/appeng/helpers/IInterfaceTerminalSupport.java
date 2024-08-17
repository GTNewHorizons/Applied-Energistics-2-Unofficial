package appeng.helpers;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;

/**
 * Refactoring this class into API, and renaming.
 *
 * @see IInterfaceViewable
 */
@Deprecated
public interface IInterfaceTerminalSupport extends IGridHost {

    class PatternsConfiguration {

        /** This property should be used in the terminal level and items tightly packed. */
        @Deprecated
        public int offset;
        public int size;

        public PatternsConfiguration(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }

    DimensionalCoord getLocation();

    PatternsConfiguration[] getPatternsConfigurations();

    @Deprecated
    IInventory getPatterns(int index);

    String getName();

    TileEntity getTileEntity();

    @Deprecated
    default long getSortValue() {
        var te = getTileEntity();
        return ((long) te.zCoord << 24) ^ ((long) te.xCoord << 8) ^ te.yCoord;
    }

    default boolean shouldDisplay() {
        return true;
    }
}
