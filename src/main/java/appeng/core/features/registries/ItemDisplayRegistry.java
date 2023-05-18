package appeng.core.features.registries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import net.minecraft.item.Item;

import appeng.api.config.TypeFilter;
import appeng.api.storage.IItemDisplayRegistry;
import appeng.api.storage.data.IAEItemStack;

public final class ItemDisplayRegistry implements IItemDisplayRegistry {

    private final Set<Class<? extends Item>> blacklistedItemClasses;
    private final Set<Item> blacklistedItems;
    private final List<BiPredicate<TypeFilter, IAEItemStack>> itemFilters;

    public ItemDisplayRegistry() {
        this.blacklistedItemClasses = new HashSet<>();
        this.blacklistedItems = new HashSet<>();
        this.itemFilters = new ArrayList<>();
    }

    @Override
    public void blacklistItemDisplay(Item item) {
        this.blacklistedItems.add(item);
    }

    @Override
    public void blacklistItemDisplay(Class<? extends Item> item) {
        this.blacklistedItemClasses.add(item);
    }

    @Override
    public boolean isBlacklisted(Item item) {
        return blacklistedItems.contains(item);
    }

    @Override
    public boolean isBlacklisted(Class<? extends Item> item) {
        return blacklistedItemClasses.contains(item);
    }

    @Override
    public void addItemFilter(BiPredicate<TypeFilter, IAEItemStack> filter) {
        this.itemFilters.add(filter);
    }

    @Override
    public List<BiPredicate<TypeFilter, IAEItemStack>> getItemFilters() {
        return itemFilters;
    }
}
