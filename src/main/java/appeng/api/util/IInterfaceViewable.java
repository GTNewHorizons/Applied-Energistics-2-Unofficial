package appeng.api.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;

/**
 * Replacement for {@code IInterfaceTerminalSupport} in API.
 */
public interface IInterfaceViewable extends IGridHost {

    DimensionalCoord getLocation();

    /**
     * Number of rows to expect. This is used with {@link #rowSize()} to determine how to render the slots.
     */
    int rows();

    /**
     * Number of slots per row.
     */
    int rowSize();

    /**
     * Get the patterns. If multiple rows are supported, this is assumed to be tightly packed. Use {@link #rowSize()}
     * with {@link #rows()} to determine where a row starts.
     */
    IInventory getPatterns();

    String getName();

    TileEntity getTileEntity();

    boolean shouldDisplay();

    /**
     * Self representation
     */
    default ItemStack getSelfRep() {
        return null;
    }

    /**
     * "Target" Display representation
     */
    default ItemStack getDisplayRep() {
        return null;
    }
}
