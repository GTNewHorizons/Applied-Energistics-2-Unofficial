package appeng.util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

public interface IItemTree extends Iterable<IAEItemStack> {

    /**
     * ItemStack display list of the items.
     */
    List<ItemStack> displayList();

    /**
     * IAEItemStack view list of the items.
     */
    List<IAEItemStack> viewList();

    /**
     * Resort the entire list using the new comparator and filter.
     */
    void refresh(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter);

    /**
     * Prune the sorted list using the new filter.
     */
    void refresh(Predicate<IAEItemStack> filter);

    /**
     * Resort the entire list using the new comparator with the cached filter.
     */
    void refresh(Comparator<IAEItemStack> comparator);

    /**
     * Update the list with the stack. Refreshes the displayed/view list.
     */
    void update(IAEItemStack stack);

    /**
     * Remove all elements from the backing list. Triggers a refresh.
     */
    void clear();
}
