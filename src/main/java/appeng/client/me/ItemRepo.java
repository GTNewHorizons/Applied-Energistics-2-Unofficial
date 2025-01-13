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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.TypeFilter;
import appeng.api.config.ViewItems;
import appeng.api.storage.IItemDisplayRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.integration.modules.NEI;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.item.OreHelper;
import appeng.util.item.OreReference;
import appeng.util.prioitylist.IPartitionList;

public class ItemRepo implements IDisplayRepo {

    private final IItemList<IAEItemStack> list = AEApi.instance().storage().createItemList();
    private final ArrayList<IAEItemStack> view = new ArrayList<>();
    private final ArrayList<ItemStack> dsp = new ArrayList<>();
    private final IScrollSource src;
    private final ISortSource sortSrc;

    private int rowSize = 9;

    private String searchString = "";
    private Map<IAEItemStack, Boolean> searchCache = new WeakHashMap<>();
    private IPartitionList<IAEItemStack> myPartitionList;
    private boolean hasPower;
    private boolean paused = false;

    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    @Override
    public IAEItemStack getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    @Override
    public ItemStack getItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.dsp.size()) {
            return null;
        }
        return this.dsp.get(idx);
    }

    @Override
    public void postUpdate(final IAEItemStack is) {
        final IAEItemStack st = this.list.findPrecise(is);

        if (st != null) {
            st.reset();
            st.add(is);
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
        if (this.paused) {
            // Update existing view with new data
            for (int i = 0; i < this.view.size(); i++) {
                IAEItemStack entry = this.view.get(i);
                IAEItemStack serverEntry = this.list.findPrecise(entry);
                if (serverEntry == null) {
                    entry.setStackSize(0);
                } else {
                    this.view.set(i, serverEntry);
                }
            }

            // Append newly added item stacks to the end of the view
            Set<IAEItemStack> viewSet = new HashSet<>(this.view);
            ArrayList<IAEItemStack> entriesToAdd = new ArrayList<>();
            for (IAEItemStack serverEntry : this.list) {
                if (!viewSet.contains(serverEntry)) {
                    entriesToAdd.add(serverEntry);
                }
            }
            addEntriesToView(entriesToAdd);
        } else {
            this.view.clear();
            this.view.ensureCapacity(this.list.size());
            addEntriesToView(this.list);
        }

        // Don't sort the view if paused.
        if (!this.paused) {
            final Enum SortBy = this.sortSrc.getSortBy();
            final Enum SortDir = this.sortSrc.getSortDir();

            ItemSorters.setDirection((appeng.api.config.SortDir) SortDir);
            ItemSorters.init();

            if (SortBy == SortOrder.MOD) {
                this.view.sort(ItemSorters.CONFIG_BASED_SORT_BY_MOD);
            } else if (SortBy == SortOrder.AMOUNT) {
                this.view.sort(ItemSorters.CONFIG_BASED_SORT_BY_SIZE);
            } else if (SortBy == SortOrder.INVTWEAKS) {
                this.view.sort(ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS);
            } else {
                this.view.sort(ItemSorters.CONFIG_BASED_SORT_BY_NAME);
            }
        }

        this.dsp.clear();
        this.dsp.ensureCapacity(this.list.size());
        for (final IAEItemStack is : this.view) {
            this.dsp.add(is.getItemStack());
        }
    }

    private void addEntriesToView(Iterable<IAEItemStack> entries) {
        final Enum viewMode = this.sortSrc.getSortDisplay();
        final Enum typeFilter = this.sortSrc.getTypeFilter();
        Predicate<IAEItemStack> itemFilter = null;

        if (!this.searchString.trim().isEmpty()) {
            if (NEI.searchField.existsSearchField()) {
                final Predicate<ItemStack> neiFilter = NEI.searchField.getFilter(this.searchString);
                itemFilter = is -> neiFilter.test(is.getItemStack());
            } else {
                itemFilter = getFilter(this.searchString);
            }
        }

        IItemDisplayRegistry registry = AEApi.instance().registries().itemDisplay();

        out: for (IAEItemStack is : entries) {
            if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
                continue;
            }

            if (viewMode == ViewItems.STORED && is.getStackSize() == 0) {
                continue;
            }

            if (this.myPartitionList != null && !this.myPartitionList.isListed(is)) {
                continue;
            }

            if (registry.isBlacklisted(is.getItem()) || registry.isBlacklisted(is.getItem().getClass())) {
                continue;
            }

            for (final BiPredicate<TypeFilter, IAEItemStack> filter : registry.getItemFilters()) {
                if (!filter.test((TypeFilter) typeFilter, is)) continue out;
            }

            if (itemFilter == null || Boolean.TRUE.equals(this.searchCache.computeIfAbsent(is, itemFilter::test))) {

                if (viewMode == ViewItems.CRAFTABLE) {
                    is = is.copy();
                    is.setStackSize(0);
                }

                this.view.add(is);
            }
        }
    }

    private Predicate<IAEItemStack> getFilter(String innerSearch) {

        if (innerSearch.isEmpty()) {
            return stack -> true;
        }

        final String prefix = innerSearch.substring(0, 1);

        if ("#".equals(prefix)) {
            final Pattern pattern = getPattern(innerSearch.substring(1));
            return stack -> {
                String tooltip = String.join("\n", Platform.getTooltip(stack));
                return pattern.matcher(tooltip).find();
            };
        } else if ("@".equals(prefix)) {
            final Pattern pattern = getPattern(innerSearch.substring(1));
            return stack -> {
                String mod = Platform.getModId(stack);
                return pattern.matcher(mod).find();
            };
        } else if ("$".equals(prefix)) {
            final Pattern pattern = getPattern(innerSearch.substring(1));
            return stack -> {
                OreReference ores = OreHelper.INSTANCE.isOre(stack.getItemStack());
                return ores != null && pattern.matcher(String.join("\n", ores.getEquivalents())).find();
            };
        } else {
            final Pattern pattern = getPattern(innerSearch);
            return stack -> {
                String name = Platform.getItemDisplayName(stack);

                if (pattern.matcher(name).find()) {
                    return true;
                }

                String tooltip = String.join("\n", Platform.getTooltip(stack));
                return pattern.matcher(tooltip).find();
            };
        }
    }

    private static Pattern getPattern(String search) {
        final int flags = Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return Pattern.compile(search, flags);
        } catch (PatternSyntaxException __) {
            return Pattern.compile(Pattern.quote(search), flags);
        }
    }

    @Override
    public int size() {
        return this.view.size();
    }

    @Override
    public void clear() {
        this.list.resetStatus();
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
        if (!searchString.equals(this.searchString)) {
            this.searchString = searchString;
            this.searchCache.clear();

            if (NEI.searchField.existsSearchField()) {
                final Enum searchMode = AEConfig.instance.settings.getSetting(Settings.SEARCH_MODE);
                if (searchMode == SearchBoxMode.NEI_AUTOSEARCH || searchMode == SearchBoxMode.NEI_MANUAL_SEARCH) {
                    NEI.searchField.setText(this.searchString);
                }
            }
        }

    }

    @Override
    public boolean isPaused() {
        return this.paused;
    }

    @Override
    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;

            // Update view when un-paused
            if (!paused) {
                updateView();
            }
        }
    }
}
