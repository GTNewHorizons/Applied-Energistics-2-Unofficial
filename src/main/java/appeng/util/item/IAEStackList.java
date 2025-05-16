package appeng.util.item;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class IAEStackList implements IItemList<IAEStack<?>> {

    private final NavigableMap<IAEStack<?>, IAEStack<?>> records = new ConcurrentSkipListMap<>(IAEStack::compareTo);
    private final ObjectOpenHashSet<IAEStack<?>> setRecords = new ObjectOpenHashSet<>();

    @Override
    public void add(final IAEStack<?> option) {
        if (option != null) {
            final IAEStack st = setRecords.get(option);
            if (st != null) st.add(option);
            else putItemRecord(option.copy());
        }
    }

    @Override
    public IAEStack<?> findPrecise(final IAEStack<?> itemStack) {
        if (itemStack != null) return setRecords.get(itemStack);
        return null;
    }

    @Override
    public Collection<IAEStack<?>> findFuzzy(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        if (filter != null) {
            if (filter.isFluid()) return Arrays.asList(findPrecise(filter));

            final AEItemStack ais = (AEItemStack) filter;
            if (ais.isOre()) {
                final OreReference or = ais.getDefinition().getIsOre();
                if (or.getAEEquivalents().size() == 1) {
                    final IAEItemStack is = or.getAEEquivalents().get(0);
                    return findFuzzyDamage((AEItemStack) is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE);
                } else {
                    final Collection<IAEStack<?>> output = new LinkedList<>();
                    for (final IAEItemStack is : or.getAEEquivalents()) {
                        output.addAll(
                                findFuzzyDamage(
                                        (AEItemStack) is,
                                        fuzzy,
                                        is.getItemDamage() == OreDictionary.WILDCARD_VALUE));
                    }
                    return output;
                }
            }
            return findFuzzyDamage(ais, fuzzy, false);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    public void addStorage(final IAEStack<?> option) {
        if (option != null) {
            final IAEStack<?> st = setRecords.get(option);
            if (st != null) st.incStackSize(option.getStackSize());
            else putItemRecord(option.copy());
        }
    }

    @Override
    public void addCrafting(final IAEStack<?> option) {
        if (option != null) {
            final IAEStack<?> st = setRecords.get(option);
            if (st != null) st.setCraftable(true);
            else putItemRecord(option.copy().setStackSize(0).setCraftable(true));
        }
    }

    @Override
    public void addRequestable(final IAEStack<?> option) {
        if (option != null) {
            final IAEStack<?> st = setRecords.get(option);
            if (st != null) st.setCountRequestable(st.getCountRequestable() + option.getCountRequestable())
                    .setCountRequestableCrafts(st.getCountRequestableCrafts() + option.getCountRequestableCrafts());
            else putItemRecord(option.copy().setStackSize(0).setCraftable(false));
        }
    }

    @Override
    public IAEStack<?> getFirstItem() {
        for (final IAEStack<?> stackType : this) {
            return stackType;
        }
        return null;
    }

    @Override
    public int size() {
        return setRecords.size();
    }

    @Override
    public Iterator<IAEStack<?>> iterator() {
        return new MeaningfulAEStackIterator<>(new Iterator<>() {

            private final Iterator<IAEStack<?>> i = IAEStackList.this.records.values().iterator();
            private IAEStack<?> next = null;

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public IAEStack<?> next() {
                return (next = i.next());
            }

            @Override
            public void remove() {
                i.remove();
                IAEStackList.this.setRecords.remove(next);
            }
        });
    }

    @Override
    public void resetStatus() {
        for (final IAEStack<?> i : this) {
            i.reset();
        }
    }

    public void clear() {
        setRecords.clear();
        records.clear();
    }

    private void putItemRecord(final IAEStack<?> itemStack) {
        setRecords.add(itemStack);
        records.put(itemStack, itemStack);
    }

    private Collection<IAEStack<?>> findFuzzyDamage(final AEItemStack filter, final FuzzyMode fuzzy,
            final boolean ignoreMeta) {
        final IAEItemStack low = filter.getLow(fuzzy, ignoreMeta);
        final IAEItemStack high = filter.getHigh(fuzzy, ignoreMeta);

        return records.subMap(low, true, high, true).descendingMap().values();
    }
}
