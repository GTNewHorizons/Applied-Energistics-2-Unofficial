package appeng.api.storage;

import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.item.Item;

import appeng.api.config.TypeFilter;
import appeng.api.storage.data.IAEItemStack;

/**
 * API for item display repo.
 */
public interface IItemDisplayRegistry {

    /**
     * Blacklist the specified item from the item terminals from being displayed in item terminals.
     */
    void blacklistItemDisplay(Item item);

    /**
     * Blacklist the specified item class from being displayed in item terminals. Note that all instances of this class
     * are blacklisted.
     */
    void blacklistItemDisplay(Class<? extends Item> item);

    boolean isBlacklisted(Item item);

    boolean isBlacklisted(Class<? extends Item> item);

    /**
     * Adds a filter option. This is primarily for ae2fc's item drops, but can be expanded.
     */
    void addItemFilter(BiPredicate<TypeFilter, IAEItemStack> filter);

    List<BiPredicate<TypeFilter, IAEItemStack>> getItemFilters();
}
