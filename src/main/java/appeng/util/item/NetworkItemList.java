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

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * A NetworkItemList contains one or more IItemLists from different networks.
 * These IItemLists can themselves be NetworkItemLists.
 * This allows us to filter items from multiple, or same networks with different filters while also including all their subnetworks and filters.
 * <p>
 * TODO how do we remove duplicate items?
 * Since we know which items came from which network we can make sure that those are only added once to the final item list.
 *
 * @param <T>
 */
public class NetworkItemList<T extends IAEStack> implements IItemList<T> {
    private final Map<IMENetworkInventory<T>, IItemList<T>> networkItemLists;
    private final Supplier<IItemList<T>> newItemListSupplier;
    private final List<Predicate<T>> predicates;

    public NetworkItemList(Supplier<IItemList<T>> newItemListSupplier) {
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
        this.predicates = new ArrayList<>();
        this.networkItemLists = networkItemList.networkItemLists;
        this.newItemListSupplier = networkItemList.newItemListSupplier;
    }

    public void addNetworkItems(IMENetworkInventory<T> network, IItemList<T> itemList) {
        IItemList<T> l = networkItemLists.get(network);

        if (l instanceof NetworkItemList) {
            if (itemList instanceof NetworkItemList) {
                // since the network is the same just combine the predicates lists
                for (Predicate<T> filter : ((NetworkItemList<T>) itemList).predicates) {
                    ((NetworkItemList<T>) l).addFilter(filter);
                }
                return;
            } else {
                throw new RuntimeException("This NetworkItemList already contains a NetworkItemList for the provided network and cannot replace it with a non-NetworkItemList");
            }
        } else if (l != null) {
            throw new RuntimeException("This NetworkItemList already contains a non-NetworkItemList for the provided network and cannot replace it");
        }
        networkItemLists.put(network, itemList);
    }

    private Stream<IItemList<T>> getItemListStream() {
        return networkItemLists.values().stream();
    }

    private Stream<T> getItemStream() {
        return getItemListStream().flatMap(e -> StreamSupport.stream(e.spliterator(), false));
    }

    private Stream<T> getFilteredItemStream() {
        Stream<T> itemStream = getItemStream();
        Predicate<T> predicate = buildFilter();
        if (predicate == null) return itemStream;
        else return itemStream.filter(predicate);
    }

    // TODO if 2 nets A and B  read from C then the predicate will be applied on read from A -> C, later when B -> C gets the network inventory the predicate will already be added.
    // as such we need a way to make predicates be per network (but still applied for all nested NetworkItemLists)
    // Gotta overthink how predicates work, we got multiple cases:
    // - if multiple storage buses between same networks we want OR predicates
    // - if two separate networks use same network with different filters we want individual predicates

    // predicates are applied when the network item list is outbound, so to say after the network inventory is done with it
    // as such these predicates describe how the network of the storage bus is reading the external network. since the external network does not need to know this information the predicates should instead be stored somewhere in the storage buses network, which should solve the correct attribution
    public void addFilter(Predicate<T> filter) {
        predicates.add(filter);
    }

    private Predicate<T> buildFilter() {
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
        // TODO do not add items from same network multiple times
        getFilteredItemStream().forEach(out::add);
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
        return getFilteredItemStream().findFirst().orElse(null);
    }

    @Override
    public int size() {
        return (int) getFilteredItemStream().count();
    }

    @Override
    public Iterator<T> iterator() {
        return getFilteredItemStream().iterator();
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
        return !getFilteredItemStream().findAny().isPresent();
    }

    private class NetworkItemStack<T extends IAEStack> {
        private IMENetworkInventory<T> networkInventory;
        private T itemStack;
    }
}
