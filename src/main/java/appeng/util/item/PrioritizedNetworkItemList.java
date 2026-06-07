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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class PrioritizedNetworkItemList<T extends IAEStack> extends NetworkItemList<T> {

    // Compare every stack on the same root-to-leaf priority axes to keep the comparator transitive.
    private static final Comparator<IntList> PRIORITY_PATH_ORDER = (o1, o2) -> {
        final int sharedPathLength = Math.min(o1.size(), o2.size());
        for (int i = 0; i < sharedPathLength; i++) {
            final int result = Integer.compare(o1.getInt(i), o2.getInt(i));
            if (result != 0) return result;
        }

        return Integer.compare(o1.size(), o2.size());
    };
    private static final Comparator<PrioritizedNetworkItemStack<?>> PRIORITIZED_STACK_ORDER = (o1,
            o2) -> PRIORITY_PATH_ORDER.compare(o1.priorityPath, o2.priorityPath);
    private static final Comparator<PrioritizedNetworkItemStack<?>> REVERSED_PRIORITIZED_STACK_ORDER = PRIORITIZED_STACK_ORDER
            .reversed();

    private final Map<IMENetworkInventory<T>, Map<Integer, IItemList<T>>> prioritizedNetworkItemLists;

    public PrioritizedNetworkItemList(IMENetworkInventory<T> network) {
        super(network, null);
        this.prioritizedNetworkItemLists = new HashMap<>();
    }

    /**
     * Creates a shallow copy of a network item list. Filters are not copied.
     *
     * @param networkItemList the list to copy from
     */
    public PrioritizedNetworkItemList(PrioritizedNetworkItemList<T> networkItemList) {
        super(networkItemList);
        this.prioritizedNetworkItemLists = networkItemList.prioritizedNetworkItemLists;
    }

    @Override
    public void addNetworkItems(IMENetworkInventory<T> network, IItemList<T> itemList) {
        throw new UnsupportedOperationException();
    }

    public void addNetworkItems(IMENetworkInventory<T> network, int priority, IItemList<T> itemList) {
        Map<Integer, IItemList<T>> priorityItemList = this.prioritizedNetworkItemLists.get(network);
        final IItemList<T> l = priorityItemList == null ? null : priorityItemList.get(priority);

        if (l instanceof PrioritizedNetworkItemList) {
            if (itemList instanceof PrioritizedNetworkItemList) {
                // since the network and priority is the same we combine the predicates
                for (Predicate<T> filter : ((PrioritizedNetworkItemList<T>) itemList).getPredicates()) {
                    ((PrioritizedNetworkItemList<T>) l).addFilter(filter);
                }
                return;
            } else {
                throw new RuntimeException(
                        "This PrioritizedNetworkItemList already contains a PrioritizedNetworkItemList for the provided network and priority and cannot replace it with a non-PrioritizedNetworkItemList");
            }
        } else if (l != null) {
            throw new RuntimeException(
                    "This PrioritizedNetworkItemList already contains a non-PrioritizedNetworkItemList for the provided network and priority and cannot replace it");
        }
        if (priorityItemList == null) {
            priorityItemList = new HashMap<>();
            this.prioritizedNetworkItemLists.put(network, priorityItemList);
        }
        priorityItemList.put(priority, itemList);
    }

    private Stream<PrioritizedNetworkItemStack<T>> getFilteredPrioritizedNetworkItemStackStream(
            final Set<IMENetworkInventory<T>> visitedNetworks, final boolean ascendingPriority) {
        return this.prioritizedNetworkItemLists.entrySet().stream()
                // equivalent to a worse performing mapMulti
                .flatMap(entry -> {
                    // do filter by filter instead and combine the streams afterwards
                    List<Stream<PrioritizedNetworkItemStack<T>>> priorityStreams = new ArrayList<>();

                    for (Iterator<Entry<Integer, IItemList<T>>> it = entry.getValue().entrySet().stream()
                            .sorted(getPriorityOrder(ascendingPriority)).iterator(); it.hasNext();) {
                        final Entry<Integer, IItemList<T>> priorityItemList = it.next();
                        final int priorty = priorityItemList.getKey();
                        final IItemList<T> itemList = priorityItemList.getValue();

                        if (itemList instanceof PrioritizedNetworkItemList) {
                            if (visitedNetworks.contains(entry.getKey())) {
                                return Stream.empty();
                            }
                            final Set<IMENetworkInventory<T>> localVisitedNetworks = new HashSet<>(visitedNetworks);
                            localVisitedNetworks.add(entry.getKey());

                            PrioritizedNetworkItemList<T> l = (PrioritizedNetworkItemList<T>) itemList;
                            final Predicate<T> predicate = l.buildFilter();
                            Stream<PrioritizedNetworkItemStack<T>> stream = l
                                    .getFilteredPrioritizedNetworkItemStackStream(
                                            Collections.unmodifiableSet(localVisitedNetworks),
                                            ascendingPriority)
                                    .filter(e -> predicate.test(e.getItemStack()))
                                    .peek(e -> e.prependPriority(priorty));
                            priorityStreams.add(stream);
                        } else {
                            List<PrioritizedNetworkItemStack<T>> buffer = new ArrayList<>();
                            itemList.forEach(
                                    item -> buffer
                                            .add(new PrioritizedNetworkItemStack<>(entry.getKey(), item, priorty)));
                            priorityStreams.add(buffer.stream());
                        }

                    }
                    return priorityStreams.stream().flatMap(Function.identity());
                });
    }

    private Comparator<Entry<Integer, IItemList<T>>> getPriorityOrder(boolean asc) {
        Comparator<Entry<Integer, IItemList<T>>> comparator = Entry.comparingByKey();
        if (asc) return comparator;
        else return comparator.reversed();
    }

    @Override
    public Stream<T> getItems() {
        return this.getItems(true);
    }

    public Stream<T> getItems(boolean ascendingPriority) {
        Set<IMENetworkInventory<T>> visitedNetworks = new HashSet<>();
        visitedNetworks.add(this.getNetwork());
        return getFilteredPrioritizedNetworkItemStackStream(
                Collections.unmodifiableSet(visitedNetworks),
                ascendingPriority).distinct().sorted(getPriorityPathOrder(ascendingPriority))
                        .map(NetworkItemStack::getItemStack);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends IAEStack> Comparator<PrioritizedNetworkItemStack<T>> getPriorityPathOrder(
            boolean ascendingPriority) {
        return (Comparator) (ascendingPriority ? PRIORITIZED_STACK_ORDER : REVERSED_PRIORITIZED_STACK_ORDER);
    }

    @Override
    public IItemList<T> buildFinalItemList() {
        throw new UnsupportedOperationException(); // use a normal network item list if you just want all items
    }

    @Override
    public IItemList<T> buildFinalItemList(IItemList<T> out) {
        throw new UnsupportedOperationException(); // use a normal network item list if you just want all items
    }

    @Override
    public T findPrecise(T i) {
        throw new UnsupportedOperationException(); // use a normal network item list instead
    }

    @Override
    public Collection<T> findFuzzy(T input, FuzzyMode fuzzy) {
        throw new UnsupportedOperationException(); // use a normal network item list instead
    }

    @Override
    public @Nullable IAEStackType<T> getStackType() {
        Map<Integer, IItemList<T>> m = prioritizedNetworkItemLists.values().stream().findAny().orElse(null);
        if (m == null) return null;
        IItemList<T> list = m.values().stream().findAny().orElse(null);
        return list == null ? null : list.getStackType();
    }

    @Override
    public void resetStatus() {
        for (Map<Integer, IItemList<T>> m : prioritizedNetworkItemLists.values()) {
            for (IItemList<T> l : m.values()) {
                l.resetStatus();
            }
        }
    }

    private static class PrioritizedNetworkItemStack<U extends IAEStack> extends NetworkItemStack<U> {

        private final IntList priorityPath;

        public PrioritizedNetworkItemStack(@Nonnull final IMENetworkInventory<U> networkInventory,
                @Nonnull final U itemStack, final int priority) {
            super(networkInventory, itemStack);
            this.priorityPath = new IntArrayList();
            this.priorityPath.add(priority);
        }

        public void prependPriority(int priority) {
            this.priorityPath.add(0, priority);
        }
    }
}
