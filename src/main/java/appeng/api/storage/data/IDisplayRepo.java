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

    void setPowered(final boolean hasPower);

    int getRowSize();

    void setRowSize(final int rowSize);

    String getSearchString();

    void setSearchString(@Nonnull final String searchString);
}
