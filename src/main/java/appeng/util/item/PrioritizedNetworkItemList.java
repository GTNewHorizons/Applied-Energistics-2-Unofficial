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

import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.PriorityPredicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A NetworkItemList contains one or more IItemLists from different networks. These IItemLists can themselves be
 * NetworkItemLists. This allows us to filter items from multiple, or same networks with different filters while also
 * including all their subnetworks and filters.
 *
 * @param <T>
 */
public class PrioritizedNetworkItemList<T extends IAEStack> extends NetworkItemList<T> {

    private final List<PriorityPredicate<T>> priorityPredicates;

    public PrioritizedNetworkItemList(IMENetworkInventory<T> network, Supplier<IItemList<T>> newItemListSupplier) {
        super(network, newItemListSupplier);
        this.priorityPredicates = new ArrayList<>();
    }

    /**
     * Creates a shallow copy of a network item list. Filters are not copied.
     *
     * @param networkItemList the list to copy from
     */
    public PrioritizedNetworkItemList(NetworkItemList<T> networkItemList) {
        super(networkItemList);
        this.priorityPredicates = new ArrayList<>();
    }

    public List<PriorityPredicate<T>> getPriorityPredicates() {
        return priorityPredicates;
    }

    public void addNetworkItems(IMENetworkInventory<T> network, IItemList<T> itemList) {
        IItemList<T> l = this.getNetworkItemLists().get(network);

        if (l instanceof PrioritizedNetworkItemList) {
            if (itemList instanceof PrioritizedNetworkItemList) {
                // since the network is the same we combine the predicates
                for (PriorityPredicate<T> priorityFilter : ((PrioritizedNetworkItemList<T>) itemList).priorityPredicates) {
                    ((PrioritizedNetworkItemList<T>) l).addFilter(priorityFilter);
                }
                return;
            } else {
                throw new RuntimeException(
                        "This NetworkItemList already contains a NetworkItemList for the provided network and cannot replace it with a non-NetworkItemList");
            }
        } else if (l != null) {
            throw new RuntimeException(
                    "This NetworkItemList already contains a non-NetworkItemList for the provided network and cannot replace it");
        }
        this.getNetworkItemLists().put(network, itemList);
    }

    private Stream<PrioritizedNetworkItemStack<T>> getFilteredPrioritizedNetworkItemStackStream(final Set<IMENetworkInventory<T>> visitedNetworks, final boolean ascendingPriority) {
        return this.getNetworkItemLists().entrySet().stream()
                // equivalent to a worse performing mapMulti
                .flatMap(entry -> {
                    if (entry.getValue() instanceof PrioritizedNetworkItemList) {
                        if (visitedNetworks.contains(entry.getKey())) {
                            return Stream.empty();
                        }
                        final Set<IMENetworkInventory<T>> localVisitedNetworks = new HashSet<>(visitedNetworks);
                        localVisitedNetworks.add(entry.getKey());

                        int localPriority = 0;
                        Integer previousPriority = null;
                        // do filter by filter instead and combine the streams afterwards
                        List<Stream<PrioritizedNetworkItemStack<T>>> priorityStreams = new ArrayList<>();
                        for(PriorityPredicate<T> priorityPredicate : priorityPredicates.stream().sorted(getPriorityOrder(ascendingPriority)).toList()) {
                            if(previousPriority == null) previousPriority = priorityPredicate.getPriority();
                            if(previousPriority != priorityPredicate.getPriority()) {
                                localPriority++;
                                previousPriority = priorityPredicate.getPriority();
                            } // if same priority use same local priority
                            final int finalLocalPriority = localPriority;
                            // TODO could combine the predicates with same priority into one test
                            // just gotta make sure the for-loop skips those too then
                            final Predicate<T> predicate = priorityPredicate.getPredicate();
                            Stream<PrioritizedNetworkItemStack<T>> stream = ((PrioritizedNetworkItemList<T>) entry.getValue()).getFilteredPrioritizedNetworkItemStackStream(Collections.unmodifiableSet(localVisitedNetworks), ascendingPriority)
                                    .filter(e -> e.getPriority() == null)
                                    .filter(e -> predicate.test(e.getItemStack()))
                                    .peek(e -> e.setPriority(finalLocalPriority));
                            priorityStreams.add(stream);
                        }
                        return priorityStreams.stream().flatMap(Function.identity());
                    } else {
                        // FIXME the list that contains the networks own items is usually a combined one without priorities
                        // to be correct even in a single network with no reads from other networks we need to know the priority of things like storage bus <-> chest or ME drives items
                        // since that is on the itemstack level and handled by the networkinventory i'll need to add itemstacks there with a priority
                        // so either make it (itemstack, priority) or make it (priority, itemlist)
                        List<PrioritizedNetworkItemStack<T>> buffer = new ArrayList<>();
                        StreamSupport.stream(entry.getValue().spliterator(), false)
                                .forEach(item -> buffer.add(new PrioritizedNetworkItemStack<>(entry.getKey(), item)));
                        return buffer.stream();
                    }
                });
    }

    private Comparator<PriorityPredicate<T>> getPriorityOrder(boolean asc) {
        Comparator<PriorityPredicate<T>> comparator = Comparator.comparing(PriorityPredicate::getPriority);
        if(asc) return comparator;
        else return comparator.reversed();
    }

    @Override
    public Stream<T> getItems() {
        return this.getItems(true);
    }

    public Stream<T> getItems(boolean ascendingPriority) {
        return getFilteredNetworkItemStackStream(ascendingPriority).distinct().map(NetworkItemStack::getItemStack);
    }

    private Stream<PrioritizedNetworkItemStack<T>> getFilteredNetworkItemStackStream(boolean ascendingPriority) {
        Set<IMENetworkInventory<T>> visitedNetworks = new HashSet<>();
        visitedNetworks.add(this.getNetwork());
        return getFilteredPrioritizedNetworkItemStackStream(Collections.unmodifiableSet(visitedNetworks), ascendingPriority);
    }

    public void addFilter(PriorityPredicate<T> filter) {
        priorityPredicates.add(filter);
    }

    private static class PrioritizedNetworkItemStack<U extends IAEStack> extends NetworkItemStack<U>{
        private Integer priority;

        public PrioritizedNetworkItemStack(IMENetworkInventory<U> networkInventory, U itemStack) {
            super(networkInventory, itemStack);
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }
    }
}
