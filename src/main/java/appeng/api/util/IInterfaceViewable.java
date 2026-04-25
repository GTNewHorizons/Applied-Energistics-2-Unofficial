package appeng.api.util;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridHost;
import appeng.api.storage.data.IAEStackType;
import appeng.helpers.IPriorityHost;

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
     * The total number of slots of the interface. This allows the final row to be a partial row. MUST be less than or
     * equal to {@link #rows()} times {@link #rowSize()}. Defaults to rows * rowSize, most users do not need to override
     * this.
     */
    default int numSlots() {
        return rows() * rowSize();
    }

    /**
     * Get the patterns. If multiple rows are supported, this is assumed to be tightly packed. Use {@link #rowSize()}
     * with {@link #rows()} to determine where a row starts.
     */
    IInventory getPatterns();

    /**
     * Returns the display name. For interfaces this may be a translated name with suffix already appended. Prefer
     * {@link #getRawName()} + {@link #getNameSuffix()} when sending to client.
     */
    String getName();

    /**
     * Returns the raw (untranslated) name. Should be sent to the client separately from {@link #getNameSuffix()} so
     * that translation happens client-side. Defaults to {@link #getName()} for backwards compatibility with non-AE2
     * implementations.
     */
    default String getRawName() {
        return getName();
    }

    /**
     * Returns the optional suffix to append after the name has been translated on the client. May be null if there is
     * no suffix.
     */
    default String getNameSuffix() {
        return null;
    }

    TileEntity getTileEntity();

    boolean shouldDisplay();

    default boolean allowsPatternOptimization() {
        return true;
    }

    default IAEStackType<?>[] getSupportedStackTypes() {
        return new IAEStackType<?>[] { ITEM_STACK_TYPE };
    }

    /**
     * Returns the priority of this interface. Used for display in the Interface Terminal.
     */
    default int getPriority() {
        if (this instanceof IPriorityHost ph) {
            return ph.getPriority();
        }

        TileEntity te = getTileEntity();
        if (te instanceof IPriorityHost ph) {
            return ph.getPriority();
        }

        return 0;
    }

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
