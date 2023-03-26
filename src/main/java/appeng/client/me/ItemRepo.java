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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import appeng.util.IItemTree;
import appeng.util.ItemTreeList;
import net.minecraft.item.ItemStack;

import appeng.api.config.*;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.item.OreHelper;
import appeng.util.item.OreReference;
import appeng.util.prioitylist.IPartitionList;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ItemRepo {

    private final IItemTree list;
    private final IScrollSource src;
    private final ISortSource sortSrc;
    private SortOrder cachedSortOrder;
    private SortDir cachedSortDir;
    private SearchMode cachedSearchMode;

    private int rowSize = 9;

    private String searchString = "";
    private Pattern cachedSearchPat;
    private String cachedSearchString;
    private IPartitionList<IAEItemStack> myPartitionList;
    private String NEIWord = null;
    private boolean hasPower;
    private static final ListMultimap<Enum<TypeFilter>, BiPredicate<IAEStack<?>, TypeFilter>> filters = ArrayListMultimap
            .create(4, 8);

    public static <StackType extends IAEStack<StackType>> void registerTypeHandler(
            BiPredicate<IAEStack<?>, TypeFilter> filter, TypeFilter type) {
        filters.put(type, filter);
    }

    public static ListMultimap<Enum<TypeFilter>, BiPredicate<IAEStack<?>, TypeFilter>> getFilter() {
        return filters;
    }

    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
        // AE2, did you really have to make this API so miserable? >;(
        this.list = new ItemTreeList(ItemSorters.CONFIG_BASED_SORT_BY_SIZE, this::filterView);
        this.cachedSearchPat = Pattern.compile(".+");
        this.cachedSearchString = this.searchString;
        this.cachedSearchMode = SearchMode.ITEM;
        this.cachedSortDir = SortDir.ASCENDING;
    }

    public IAEItemStack getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.list.viewList().size()) {
            return null;
        }
        return this.list.viewList().get(idx);
    }

    public ItemStack getItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.list.displayList().size()) {
            return null;
        }
        return this.list.displayList().get(idx);
    }

    void setSearch(final String search) {
        this.searchString = search == null ? "" : search;
    }

    public void postUpdate(final IAEItemStack is) {
        list.update(is);
    }

    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.updateView();
    }

    public void updateView() {
        final Enum searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
        if (searchMode == SearchBoxMode.NEI_AUTOSEARCH || searchMode == SearchBoxMode.NEI_MANUAL_SEARCH) {
            this.updateNEI(this.searchString);
        }
        boolean shouldRefresh = false;
        if (!searchString.equals(cachedSearchString)) {
            shouldRefresh = true;
            if (searchString.length() == 0) {
                cachedSearchMode = SearchMode.ITEM;
            } else {
                switch (searchString.charAt(0)) {
                    case '#':
                        cachedSearchMode = SearchMode.TOOLTIPS;
                        break;
                    case '@':
                        cachedSearchMode = SearchMode.MOD;
                        break;
                    case '$':
                        cachedSearchMode = SearchMode.ORE;
                        break;
                    default:
                        cachedSearchMode = SearchMode.ITEM;
                        break;
                }
                if (cachedSearchMode != SearchMode.ITEM) {
                    cachedSearchString = searchString;
                }
            }
            // TODO: We should measure the difference between using this pattern vs. contains. It's probably not
            // a big difference - contains looks cleaner
            if (!cachedSearchPat.pattern().equals(cachedSearchString)) {
                try {
                    cachedSearchPat = Pattern.compile(cachedSearchString.toLowerCase(), Pattern.CASE_INSENSITIVE);
                } catch (final Throwable ignore) {
                    try {
                        cachedSearchPat = Pattern.compile(Pattern.quote(cachedSearchString.toLowerCase()), Pattern.CASE_INSENSITIVE);
                    } catch (final Throwable __) {
                        // :{ no search for you
                        cachedSearchPat = Pattern.compile(".+");
                    }
                }
            }
        }
        final SortOrder sortOrder = (SortOrder) this.sortSrc.getSortBy();
        final SortDir sortDir = (SortDir) this.sortSrc.getSortDir();
        if (cachedSortDir != sortDir) {
            cachedSortDir = sortDir;
            ItemSorters.setDirection(sortDir);
            shouldRefresh = true;
        }
        if (cachedSortOrder != sortOrder) {
            cachedSortOrder = sortOrder;
            shouldRefresh = true;
        }
        if (shouldRefresh) {
            list.refresh(ItemSorters.getSorter(sortOrder));
        }
    }

    private boolean filterView(final IAEItemStack stack) {
        // Type filter check
        final TypeFilter typeFilter = (TypeFilter) this.sortSrc.getTypeFilter();
        for (final BiPredicate<IAEStack<?>, TypeFilter> filter : filters.values()) {
            if (!filter.test(stack, typeFilter)) {
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
            case CRAFTABLE:
                if (!stack.isCraftable()) return false;
            case STORED:
                if (stack.getStackSize() <= 0) return false;
        }
        // Search tag check
        String dspName;
        switch (cachedSearchMode) {
            case MOD:
                dspName = Platform.getModId(stack);
                break;
            case ORE:
                OreReference ore = OreHelper.INSTANCE.isOre(stack.getItemStack());
                if (ore != null) {
                    dspName = String.join(" ", ore.getEquivalents());
                } else {
                    return false;
                }
                break;
            case TOOLTIPS:
                dspName = String.join(" ", ((List<String>) Platform.getTooltip(stack)));
                break;
            default:
                dspName = Platform.getItemDisplayName(stack);
        }
        if (cachedSearchPat.matcher(dspName.toLowerCase()).find()) {
            return true;
        }
        // Tooltips
        if (cachedSearchMode == SearchMode.ITEM) {
            for (final Object lp : Platform.getTooltip(stack)) {
                if (lp instanceof String && cachedSearchPat.matcher((CharSequence) lp).find()) {
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

    public int size() {
        return this.list.viewList().size();
    }

    public void clear() {
        this.list.clear();
    }

    public boolean hasPower() {
        return this.hasPower;
    }

    public void setPower(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    public int getRowSize() {
        return this.rowSize;
    }

    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setSearchString(@Nonnull final String searchString) {
        this.searchString = searchString;
    }

    enum SearchMode {
        MOD,
        TOOLTIPS,
        ORE,
        ITEM
    }
}
