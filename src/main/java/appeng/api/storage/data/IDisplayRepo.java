package appeng.api.storage.data;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

/**
 * Used to display and filter items shown.
 */
public interface IDisplayRepo {

    void postUpdate(final IAEItemStack stack);

    void setViewCell(final ItemStack[] filters);

    IAEItemStack getReferenceItem(int idx);

    ItemStack getItem(int idx);

    void updateView();

    int size();

    void clear();

    boolean hasPower();

    /**
     * Strictly for backwards compatibility. Use {@link #setPowered(boolean)} instead.
     */
    @Deprecated
    default void setPower(final boolean hasPower) {
        setPowered(hasPower);
    }

    void setPowered(final boolean hasPower);

    int getRowSize();

    void setRowSize(final int rowSize);

    String getSearchString();

    void setSearchString(@Nonnull final String searchString);

    /**
     * Set whether the display repo should resort the display on each update.
     * 
     * @param shouldResort if true, on each update the display resorts. Else the display does not sort on update. New
     *                     entries are simply added to the end, and 0-count entries are silently removed.
     */
    default void setShouldResort(boolean shouldResort) {}

    default boolean isResorting() {
        return true;
    }
}
