/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.me;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.item.ItemTreeList;
import appeng.util.item.OreHelper;
import appeng.util.item.OreReference;
import appeng.util.prioitylist.IPartitionList;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ItemRepo implements IDisplayRepo {

    private final ItemTreeList list;
    private final IScrollSource src;
    private final ISortSource sortSrc;
    private SortOrder cachedSortOrder;
    private SortDir cachedSortDir;
    private SearchMode cachedSearchMode;
    private TypeFilter cachedTypeFilter;
    private int rowSize = 9;

    private String searchString;
    private String cachedSearchString;
    private String cachedSearchQuery;
    private IPartitionList<IAEItemStack> myPartitionList;
    private String NEIWord = null;
    private boolean hasPower;
    private boolean resort = false;

    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
        // AE2, did you really have to make this API so miserable? >;(
        this.list = new ItemTreeList(ItemSorters.CONFIG_BASED_SORT_BY_SIZE, this::filterView);
        // Explicit null, so we *know* it hasn't been set yet.
        this.cachedSearchString = null;
        this.cachedSearchQuery = null;
        this.cachedSearchMode = null;
        this.cachedSortDir = null;
        this.cachedSortOrder = null;
        this.cachedTypeFilter = null;
        this.searchString = "";
        ItemSorters.init();
    }

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.list.viewList().size()) {
            return null;
        }
        return this.list.viewList().get(idx);
    }

    @Override
    public ItemStack getItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.list.displayList().size()) {
            return null;
        }
        return this.list.displayList().get(idx);
    }

    @Override
    public void postUpdate(final IAEItemStack is) {
        final IAEItemStack st = this.list.findPrecise(is);

        if (st != null) {
            st.reset();
            st.add(is);
            this.list.updateItem(st);
        } else {
            this.list.add(is);
        }
    }

    @Override
    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.updateView();
    }

    @Override
    public void updateView() {
        final Enum<?> searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        if (searchMode == SearchBoxMode.NEI_AUTOSEARCH || searchMode == SearchBoxMode.NEI_MANUAL_SEARCH) {
            this.updateNEI(this.searchString);
        }
        boolean resortItems = false;
        if (!searchString.equals(cachedSearchString)) {
            cachedSearchString = searchString;
            resortItems = true;
            if (searchString.length() == 0) {
                cachedSearchMode = SearchMode.ITEM;
            } else {
                switch (searchString.charAt(0)) {
                    case '#' -> cachedSearchMode = SearchMode.TOOLTIPS;
                    case '@' -> cachedSearchMode = SearchMode.MOD;
                    case '$' -> cachedSearchMode = SearchMode.ORE;
                    default -> cachedSearchMode = SearchMode.ITEM;
                }
                if (cachedSearchMode != SearchMode.ITEM) {
                    cachedSearchQuery = searchString.substring(1);
                } else {
                    cachedSearchQuery = searchString;
                }
            }
        }
        final TypeFilter typeFilter = (TypeFilter) this.sortSrc.getTypeFilter();
        if (cachedTypeFilter != typeFilter) {
            cachedTypeFilter = typeFilter;
        }
        final SortOrder sortOrder = (SortOrder) this.sortSrc.getSortBy();
        final SortDir sortDir = (SortDir) this.sortSrc.getSortDir();
        if (cachedSortDir != sortDir) {
            cachedSortDir = sortDir;
            ItemSorters.setDirection(sortDir);
            resortItems = true;
        }
        if (cachedSortOrder != sortOrder) {
            cachedSortOrder = sortOrder;
            resortItems = true;
        }
        if (resortItems || resort) {
            list.refresh(ItemSorters.getSorter(sortOrder));
            this.resort = false;
        } else {
            list.refresh();
        }
    }

    private boolean filterView(final IAEItemStack stack) {
        // Type filter check
        final TypeFilter typeFilter = cachedTypeFilter;
        List<BiPredicate<TypeFilter, IAEItemStack>> filters = AEApi.instance().registries().itemDisplay()
                .getItemFilters();
        for (final BiPredicate<TypeFilter, IAEItemStack> f : filters) {
            if (!f.test(typeFilter, stack)) {
                return false;
            }
        }
        // Partition check
        if (this.myPartitionList != null) {
            if (!this.myPartitionList.isListed(stack)) {
                return false;
            }
        }
        // Search filter check
        ViewItems viewMode = (ViewItems) this.sortSrc.getSortDisplay();
        switch (viewMode) {
            case CRAFTABLE -> {
                if (!stack.isCraftable()) {
                    return false;
                }
            }
            case STORED -> {
                if (stack.getStackSize() <= 0) {
                    return false;
                }
            }
            case ALL -> {
                if (!stack.isCraftable() && stack.getStackSize() <= 0) {
                    return false;
                }
            }
        }
        // Search tag check
        String dspName;
        switch (cachedSearchMode) {
            case MOD -> dspName = Platform.getModId(stack);
            case ORE -> {
                OreReference ore = OreHelper.INSTANCE.isOre(stack.getItemStack());
                if (ore != null) {
                    dspName = String.join(" ", ore.getEquivalents());
                } else {
                    return false;
                }
            }
            case TOOLTIPS -> dspName = String.join(" ", (Platform.getTooltip(stack)));
            default -> dspName = Platform.getItemDisplayName(stack);
        }
        if (dspName.toLowerCase().contains(cachedSearchString)) {
            return true;
        }
        // Tooltips
        if (cachedSearchMode == SearchMode.ITEM) {
            for (final CharSequence lp : Platform.getTooltip(stack)) {
                if (lp instanceof String && cachedSearchString.contains(lp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateNEI(final String filter) {
        try {
            if (this.NEIWord == null || !this.NEIWord.equals(filter)) {
                final Class c = ReflectionHelper
                        .getClass(this.getClass().getClassLoader(), "codechicken.nei.LayoutManager");
                final Field fldSearchField = c.getField("searchField");
                final Object searchField = fldSearchField.get(c);

                final Method a = searchField.getClass().getMethod("setText", String.class);
                final Method b = searchField.getClass().getMethod("onTextChange", String.class);

                this.NEIWord = filter;
                a.invoke(searchField, filter);
                b.invoke(searchField, "");
            }
        } catch (final Throwable ignore) {

        }
    }

    @Override
    public int size() {
        return this.list.viewList().size();
    }

    @Override
    public void clear() {
        this.list.clear();
    }

    @Override
    public boolean hasPower() {
        return this.hasPower;
    }

    @Override
    public void setPowered(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    @Override
    public int getRowSize() {
        return this.rowSize;
    }

    @Override
    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    @Override
    public String getSearchString() {
        return this.searchString;
    }

    @Override
    public void setSearchString(@Nonnull final String searchString) {
        this.searchString = searchString;
    }

    @Override
    public void setShouldResort(boolean shouldResort) {
        this.list.shouldResort = shouldResort;
        this.resort = true;
    }

    @Override
    public boolean isResorting() {
        return this.list.shouldResort;
    }

    enum SearchMode {
        MOD,
        TOOLTIPS,
        ORE,
        ITEM
    }
}
