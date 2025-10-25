/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.item;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.annotation.Nullable;

import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class ItemList implements IItemList<IAEItemStack> {

    public ItemList() {
        this(false);
    }

    public ItemList(boolean sorted) {
        if (sorted) initNavigableSet();
    }

    @Nullable
    private NavigableSet<IAEItemStack> records = null;
    private final ObjectOpenHashSet<IAEItemStack> setRecords = new ObjectOpenHashSet<>();

    @Override
    public void add(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.add(option);
            return;
        }

        final IAEItemStack opt = option.copy();

        this.putItemRecord(opt);
    }

    @Override
    public IAEItemStack findPrecise(final IAEItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        return this.setRecords.get(itemStack);
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(final IAEItemStack filter, final FuzzyMode fuzzy) {
        if (filter == null) {
            return Collections.emptyList();
        }

        final AEItemStack ais = (AEItemStack) filter;

        if (ais.isOre()) {
            final OreReference or = ais.getDefinition().getIsOre();

            if (or.getAEEquivalents().size() == 1) {
                final IAEItemStack is = or.getAEEquivalents().get(0);

                return this
                        .findFuzzyDamage((AEItemStack) is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE);
            } else {
                final Collection<IAEItemStack> output = new LinkedList<>();

                for (final IAEItemStack is : or.getAEEquivalents()) {
                    output.addAll(
                            this.findFuzzyDamage(
                                    (AEItemStack) is,
                                    fuzzy,
                                    is.getItemDamage() == OreDictionary.WILDCARD_VALUE));
                }

                return output;
            }
        }

        return this.findFuzzyDamage(ais, fuzzy, false);
    }

    @Override
    public boolean isEmpty() {
        return !this.iterator().hasNext();
    }

    @Override
    public void addStorage(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.incStackSize(option.getStackSize());
            return;
        }

        final IAEItemStack opt = option.copy();

        this.putItemRecord(opt);
    }

    /*
     * public void clean() { Iterator<StackType> i = iterator(); while (i.hasNext()) { StackType AEI = i.next(); if (
     * !AEI.isMeaningful() ) i.remove(); } }
     */

    @Override
    public void addCrafting(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.setCraftable(true);
            return;
        }

        final IAEItemStack opt = option.copy();
        opt.setStackSize(0);
        opt.setCraftable(true);

        this.putItemRecord(opt);
    }

    @Override
    public void addRequestable(final IAEItemStack option) {
        if (option == null) {
            return;
        }

        final IAEItemStack st = this.setRecords.get(option);

        if (st != null) {
            st.setCountRequestable(st.getCountRequestable() + option.getCountRequestable());
            st.setCountRequestableCrafts(st.getCountRequestableCrafts() + option.getCountRequestableCrafts());
            return;
        }

        final IAEItemStack opt = option.copy();
        opt.setStackSize(0);
        opt.setCraftable(false);
        opt.setCountRequestable(option.getCountRequestable());
        opt.setCountRequestableCrafts(option.getCountRequestableCrafts());

        this.putItemRecord(opt);
    }

    @Override
    public IAEItemStack getFirstItem() {
        for (final IAEItemStack stackType : this) {
            return stackType;
        }

        return null;
    }

    @Override
    public int size() {
        return this.setRecords.size();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        Iterator<IAEItemStack> iterator;

        if (ItemList.this.records == null) {
            iterator = new Iterator<>() {

                private final IAEItemStack[] array = ItemList.this.setRecords
                        .toArray(new IAEItemStack[ItemList.this.setRecords.size()]);
                // fastutil Hash Set throws NPE when nested iterator removes an entry
                // make a copy to prevent it
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < array.length;
                }

                @Override
                public IAEItemStack next() {
                    return array[index++];
                }

                @Override
                public void remove() {
                    ItemList.this.setRecords.remove(array[index - 1]);
                    if (ItemList.this.records != null) {
                        // records should be null here, remove just in case it's initialized during the iteration
                        ItemList.this.records.remove(array[index - 1]);
                    }
                }
            };
        } else {

            iterator = new Iterator<>() {

                private final Iterator<IAEItemStack> i = ItemList.this.records.iterator();
                private IAEItemStack next = null;

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public IAEItemStack next() {
                    return (next = i.next());
                }

                @Override
                public void remove() {
                    i.remove();
                    ItemList.this.setRecords.remove(next);
                }
            };

        }

        return new MeaningfulItemIterator<>(iterator);
    }

    @Override
    public void resetStatus() {
        for (final IAEItemStack i : this) {
            i.reset();
        }
    }

    public void clear() {
        this.setRecords.clear();
        if (this.records != null) this.records.clear();
    }

    private void putItemRecord(final IAEItemStack itemStack) {
        this.setRecords.add(itemStack);
        if (this.records != null) this.records.add(itemStack);
    }

    private Collection<IAEItemStack> findFuzzyDamage(final AEItemStack filter, final FuzzyMode fuzzy,
            final boolean ignoreMeta) {
        final IAEItemStack low = filter.getLow(fuzzy, ignoreMeta);
        final IAEItemStack high = filter.getHigh(fuzzy, ignoreMeta);
        if (this.records == null) {
            initNavigableSet();
        }
        return this.records.subSet(low, true, high, true).descendingSet();
    }

    private void initNavigableSet() {
        records = new ConcurrentSkipListSet();
        records.addAll(setRecords);
    }

    @Override
    public byte getStackType() {
        return LIST_ITEM;
    }

    @Override
    public boolean isSorted() {
        return this.records != null;
    }

    @Override
    public ItemList toSorted() {
        if (this.records == null) {
            initNavigableSet();
        }
        return this;
    }

}
