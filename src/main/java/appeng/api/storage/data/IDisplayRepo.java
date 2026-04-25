package appeng.api.storage.data;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.helpers.IPinsHandler;

/**
 * Used to display and filter items shown.
 */
public interface IDisplayRepo extends IPinsHandler {

    /**
     * Set how many pin rows are currently visible (crafting and player). Only pins in these rows hide items from the
     * main inventory; pins in reduced/hidden rows show in the main list again.
     */
    default void setVisiblePinRows(int craftingRows, int playerRows) {}

    @Deprecated
    void postUpdate(final IAEItemStack stack);

    void postUpdate(final IAEStack<?> stack);

    void setViewCell(final ItemStack[] filters);

    @Deprecated
    IAEItemStack getReferenceItem(int idx);

    IAEStack<?> getReferenceStack(int idx);

    ItemStack getItem(int idx);

    void updateView();

    int size();

    void clear();

    boolean hasPower();

    void setPowered(final boolean hasPower);

    int getRowSize();

    void setRowSize(final int rowSize);

    String getSearchString();

    void setSearchString(@Nonnull final String searchString);

    boolean isPaused();

    void setPaused(boolean paused);
}
