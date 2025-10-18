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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * A NetworkItemList contains one or more IItemLists from different networks. These IItemLists can themselves be
 * NetworkItemLists. This allows us to filter items from multiple, or same networks with different filters while also
 * including all their subnetworks and filters.
 *
 * @param <T>
 */
public class NetworkItemList<T extends IAEStack> implements IItemList<T> {

    /**
     * The network this list was created for.
     */
    private final IMENetworkInventory<T> network;
    /**
     * The networks and their items that were read from this {@code network}. For non-network inventories the
     * network-key is equal to {@code network}.
     */
    private final Map<IMENetworkInventory<T>, IItemList<T>> networkItemLists;
    private final Supplier<IItemList<T>> newItemListSupplier;
    private final List<Predicate<T>> predicates;

    public NetworkItemList(IMENetworkInventory<T> network, Supplier<IItemList<T>> newItemListSupplier) {
        this.network = network;
        this.networkItemLists = new HashMap<>();
        this.predicates = new ArrayList<>();
        this.newItemListSupplier = newItemListSupplier;
    }

    /**
     * Creates a shallow copy of a network item list. Filters are not copied.
     *
     * @param networkItemList the list to copy from
     */
    public NetworkItemList(NetworkItemList<T> networkItemList) {
        this.network = networkItemList.network;
        this.predicates = new ArrayList<>();
        this.networkItemLists = networkItemList.networkItemLists;
        this.newItemListSupplier = networkItemList.newItemListSupplier;
    }

    public IMENetworkInventory<T> getNetwork() {
        return network;
    }

    public List<Predicate<T>> getPredicates() {
        return predicates;
    }

    public void addNetworkItems(IMENetworkInventory<T> network, IItemList<T> itemList) {
        IItemList<T> l = networkItemLists.get(network);

        if (l instanceof NetworkItemList) {
            if (itemList instanceof NetworkItemList) {
                // since the network is the same we combine the predicates
                for (Predicate<T> filter : ((NetworkItemList<T>) itemList).predicates) {
                    ((NetworkItemList<T>) l).addFilter(filter);
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
        networkItemLists.put(network, itemList);
    }

    private Stream<NetworkItemStack<T>> getNetworkItemStackStream(final Set<IMENetworkInventory<T>> visitedNetworks) {
        return networkItemLists.entrySet().stream()
                // equivalent to a worse performing mapMulti
                .flatMap(entry -> {
                    if (entry.getValue() instanceof NetworkItemList) {
                        if (visitedNetworks.contains(entry.getKey())) {
                            return Stream.empty();
                        }
                        final Set<IMENetworkInventory<T>> localVisitedNetworks = new HashSet<>(visitedNetworks);
                        localVisitedNetworks.add(entry.getKey());
                        return ((NetworkItemList<T>) entry.getValue())
                                .getFilteredNetworkItemStackStream(Collections.unmodifiableSet(localVisitedNetworks));
                    } else {
                        List<NetworkItemStack<T>> buffer = new ArrayList<>();
                        StreamSupport.stream(entry.getValue().spliterator(), false)
                                .forEach(item -> buffer.add(new NetworkItemStack<>(entry.getKey(), item)));
                        return buffer.stream();
                    }
                });
    }

    private Stream<NetworkItemStack<T>> filter(Stream<NetworkItemStack<T>> stream) {
        Predicate<T> predicate = buildFilter();
        if (predicate == null) return stream;
        else return stream.filter(e -> predicate.test(e.getItemStack()));
    }

    public Stream<T> getItems() {
        return getFilteredNetworkItemStackStream().distinct().map(NetworkItemStack::getItemStack);
    }

    private Stream<NetworkItemStack<T>> getFilteredNetworkItemStackStream() {
        Set<IMENetworkInventory<T>> visitedNetworks = new HashSet<>();
        visitedNetworks.add(this.network);
        return filter(getNetworkItemStackStream(Collections.unmodifiableSet(visitedNetworks)));
    }

    private Stream<NetworkItemStack<T>> getFilteredNetworkItemStackStream(
            final Set<IMENetworkInventory<T>> visitedNetworks) {
        return filter(getNetworkItemStackStream(visitedNetworks));
    }

    public void addFilter(Predicate<T> filter) {
        predicates.add(filter);
    }

    Predicate<T> buildFilter() {
        Predicate<T> predicate = null;
        for (Predicate<T> filter : predicates) {
            if (predicate == null) {
                predicate = filter;
            } else {
                predicate = predicate.or(filter);
            }
        }
        return predicate;
    }

    /**
     * Writes all available items to a new combined list.
     *
     * @return the available items in the network as a combined IItemList
     */
    public IItemList<T> buildFinalItemList() {
        IItemList<T> out = newItemListSupplier.get();
        return buildFinalItemList(out);
    }

    /**
     * Writes all available items to the provided list.
     *
     * @param out the IItemList the results will be written to
     * @return returns same list that was passed in, is passed out
     */
    public IItemList<T> buildFinalItemList(IItemList<T> out) {
        getItems().forEach(out::add);
        return out;
    }

    @Override
    public void addStorage(T option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCrafting(T option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addRequestable(T option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T getFirstItem() {
        return getItems().findFirst().orElse(null);
    }

    @Override
    public int size() {
        return (int) getItems().count();
    }

    @Override
    public Iterator<T> iterator() {
        return getItems().iterator();
    }

    @Override
    public void resetStatus() {
        for (IItemList<T> list : networkItemLists.values()) {
            list.resetStatus();
        }
    }

    @Override
    public void add(T option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T findPrecise(T i) {
        return buildFinalItemList().findPrecise(i);
    }

    @Override
    public Collection<T> findFuzzy(final T input, final FuzzyMode fuzzy) {
        return buildFinalItemList().findFuzzy(input, fuzzy);
    }

    @Override
    public boolean isEmpty() {
        return !getItems().findAny().isPresent();
    }

    @Override
    public byte getStackType() {
        IItemList<T> list = networkItemLists.values().stream().findAny().orElse(null);
        return list == null ? LIST_NUll : list.getStackType();
    }

    static class NetworkItemStack<U extends IAEStack> {

        private final IMENetworkInventory<U> networkInventory;
        private final U itemStack;

        public NetworkItemStack(IMENetworkInventory<U> networkInventory, U itemStack) {
            this.networkInventory = networkInventory;
            this.itemStack = itemStack;
        }

        public IMENetworkInventory<U> getNetworkInventory() {
            return networkInventory;
        }

        public U getItemStack() {
            return itemStack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NetworkItemStack)) return false;
            NetworkItemStack<?> that = (NetworkItemStack<?>) o;
            // only use reference equality
            return networkInventory == that.networkInventory && itemStack == that.itemStack;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int hash = 1;
            // only use identity for hash
            hash = hash * prime + System.identityHashCode(networkInventory);
            hash = hash * prime + System.identityHashCode(itemStack);
            return hash;
        }
    }
}
