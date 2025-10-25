/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.storage;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.SecurityCache;
import appeng.util.SortedArrayList;
import appeng.util.inv.ItemListIgnoreCrafting;
import appeng.util.item.NetworkItemList;

public class NetworkInventoryHandler<T extends IAEStack<T>> implements IMENetworkInventory<T> {

    private static final ThreadLocal<LinkedList> DEPTH_MOD = new ThreadLocal<>();
    private static final ThreadLocal<LinkedList> DEPTH_SIM = new ThreadLocal<>();

    /**
     * Sorter for the {@link #priorityInventory} list. AutoCrafting inventories are first followed by Sticky
     * inventories. The inventories are then sorted by priority (highest first), and then by placement pass (1 first,
     * 1&2 second, 2 last).
     */
    private static final Comparator<IMEInventoryHandler<?>> CRAFTING_STICKY_PRIORITY_PLACEMENT_PASS_SORTER = (o1,
            o2) -> {
        int result = Boolean.compare(o2.isAutoCraftingInventory(), o1.isAutoCraftingInventory());
        if (result != 0) {
            return result;
        }

        result = Boolean.compare(o2.getSticky(), o1.getSticky());
        if (result != 0) {
            return result;
        }

        result = Integer.compare(o2.getPriority(), o1.getPriority());
        if (result != 0) {
            return result;
        }

        boolean o2ValidFor1 = o2.validForPass(1);
        boolean o1ValidFor1 = o1.validForPass(1);
        result = Boolean.compare(o2ValidFor1, o1ValidFor1);

        if (result != 0) {
            return result;
        }

        boolean o2ValidFor2 = o2.validForPass(2);
        boolean o1ValidFor2 = o1.validForPass(2);
        return Boolean.compare(o2ValidFor2, o1ValidFor2);
    };
    private final StorageChannel myChannel;
    private final SecurityCache security;
    private final List<IMEInventoryHandler<T>> priorityInventory;
    private int myPass = 0;
    private NetworkItemList<T> iterationItems = null;

    public NetworkInventoryHandler(final StorageChannel chan, final SecurityCache security) {
        this.myChannel = chan;
        this.security = security;
        this.priorityInventory = new SortedArrayList<>(CRAFTING_STICKY_PRIORITY_PLACEMENT_PASS_SORTER);
    }

    public void addNewStorage(final IMEInventoryHandler<T> h) {
        this.priorityInventory.add(h);
    }

    @Override
    public T injectItems(T input, final Actionable type, final BaseActionSource src) {
        if (this.diveList(this, type)) {
            return input;
        }

        if (this.testPermission(src, SecurityPermissions.INJECT)) {
            this.surface(this, type);
            return input;
        }

        final List<IMEInventoryHandler<T>> priorityInventory = this.priorityInventory;
        final int size = priorityInventory.size();

        int i = 0;
        boolean stickyInventoryFound = false;
        // Try to insert into all sticky inventories which are at the beginning of the list
        for (; i < size && input != null; i++) {
            final IMEInventoryHandler<T> inv = priorityInventory.get(i);
            if (!inv.getSticky() && !inv.isAutoCraftingInventory()) break;

            if (inv.canAccept(input)
                    && (inv.isPrioritized(input) || inv.extractItems(input, Actionable.SIMULATE, src) != null)) {
                input = inv.injectItems(input, type, src);
                if (!stickyInventoryFound && inv.getSticky()) stickyInventoryFound = true;
            }
        }

        if (stickyInventoryFound || input == null || i >= size) {
            this.surface(this, type);
            return input;
        }

        IMEInventoryHandler<T> inv = priorityInventory.get(i);
        int lastPriority = inv.getPriority();
        outer: while (true) {
            int passTwoIndex = -1;
            // Pass 1
            while (true) {
                // If the next if-statement computes this value, we can use it later. If it doesn't we're just being
                // optimistic here and the actual check will happen on pass 2
                boolean canAcceptInput = true;

                final boolean validForPass1 = inv.validForPass(1);
                if (validForPass1 && (canAcceptInput = inv.canAccept(input))
                        && (inv.isPrioritized(input) || inv.extractItems(input, Actionable.SIMULATE, src) != null)) {
                    input = inv.injectItems(input, type, src);
                    if (input == null) break outer;
                }

                // We remember at which index the second pass should start iterating. Additionally, we check if the
                // inventory accepts the item at all, to avoid doing the exact same check again in the second pass.
                // This als assumes that canAccept is not dependent on stack size
                if (canAcceptInput && passTwoIndex == -1 && inv.validForPass(2)) {
                    passTwoIndex = i;
                    // If we're at a pass 2 only inventory, we can stop here and continue with pass 2
                    if (!validForPass1) break;
                }

                i++;

                if (i >= size) {
                    if (passTwoIndex == -1) break outer; // If pass 2 also has no work to do, we're fully done
                    else break; // Otherwise pass 2 will run till the end again and then break out of the outer loop
                }

                inv = priorityInventory.get(i);

                final int priority = inv.getPriority();
                final boolean prioritySwitch = lastPriority != priority;
                lastPriority = priority;

                // Check if the current run has ended
                if (prioritySwitch) break;
            }

            // Pass 2
            if (passTwoIndex != -1) {
                i = passTwoIndex;
                inv = priorityInventory.get(i);
                lastPriority = inv.getPriority();
                while (true) {
                    if (inv.canAccept(input) && !inv.isPrioritized(input)) {
                        input = inv.injectItems(input, type, src);
                        if (input == null) break outer;
                    }

                    i++;

                    // Pass 2 iteration will go at least as far as pass 1, therefore we can be sure pass 1 also has
                    // no work left
                    if (i >= size) break outer;

                    inv = priorityInventory.get(i);

                    final int priority = inv.getPriority();
                    final boolean prioritySwitch = lastPriority != priority;
                    lastPriority = priority;

                    // Check if the current run has ended
                    if (prioritySwitch) break;
                }
            }
        }

        this.surface(this, type);

        return input;
    }

    private boolean diveList(final NetworkInventoryHandler<T> networkInventoryHandler, final Actionable type) {
        final LinkedList cDepth = this.getDepth(type);
        if (cDepth.contains(networkInventoryHandler)) {
            return true;
        }

        cDepth.push(this);
        return false;
    }

    private boolean testPermission(final BaseActionSource src, final SecurityPermissions permission) {
        if (src.isPlayer()) {
            if (!this.security.hasPermission(((PlayerSource) src).player, permission)) {
                return true;
            }
        } else if (src.isMachine()) {
            if (this.security.isAvailable()) {
                final IGridNode n = ((MachineSource) src).via.getActionableNode();
                if (n == null) {
                    return true;
                }

                final IGrid gn = n.getGrid();
                if (gn != this.security.getGrid()) {

                    final ISecurityGrid sg = gn.getCache(ISecurityGrid.class);
                    final int playerID = sg.isAvailable() ? sg.getOwner() : n.getPlayerID();

                    if (!this.security.hasPermission(playerID, permission)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void surface(final NetworkInventoryHandler<T> networkInventoryHandler, final Actionable type) {
        if (this.getDepth(type).pop() != this) {
            throw new IllegalStateException("Invalid Access to Networked Storage API detected.");
        }
    }

    private LinkedList getDepth(final Actionable type) {
        final ThreadLocal<LinkedList> depth = type == Actionable.MODULATE ? DEPTH_MOD : DEPTH_SIM;

        LinkedList s = depth.get();

        if (s == null) {
            depth.set(s = new LinkedList());
        }

        return s;
    }

    @Override
    public T extractItems(T request, final Actionable mode, final BaseActionSource src) {
        if (this.diveList(this, mode)) {
            return null;
        }

        if (this.testPermission(src, SecurityPermissions.EXTRACT)) {
            this.surface(this, mode);
            return null;
        }

        final T output = request.copy();
        request = request.copy();
        output.setStackSize(0);
        final long req = request.getStackSize();

        final List<IMEInventoryHandler<T>> priorityInventory = this.priorityInventory;
        final int size = priorityInventory.size();
        for (int i = size - 1; i >= 0 && output.getStackSize() < req; i--) {
            final IMEInventoryHandler<T> inv = priorityInventory.get(i);

            request.setStackSize(req - output.getStackSize());
            output.add(inv.extractItems(request, mode, src));
        }

        this.surface(this, mode);

        if (output.getStackSize() <= 0) {
            return null;
        }

        return output;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IItemList<T> getAvailableItems(IItemList out, int iteration) {
        if (this.diveIteration(this, Actionable.SIMULATE, iteration)) {
            return this.iterationItems == null ? out : this.iterationItems;
        }

        final boolean isIgnoreCrafting = out instanceof ItemListIgnoreCrafting;
        final boolean isSource = this.getDepth(Actionable.SIMULATE).size() == 1;

        final NetworkItemList<T> networkItemList = new NetworkItemList<>(
                this,
                () -> (IItemList<T>) getChannel().createList());
        this.iterationItems = networkItemList;

        final IItemList<T> currentNetworkItemList = isIgnoreCrafting
                ? new ItemListIgnoreCrafting<>(getPrimitiveItemList())
                : getPrimitiveItemList();
        final List<IMEInventoryHandler<T>> priorityInventory = this.priorityInventory;
        final int size = priorityInventory.size();
        for (int i = 0; i < size; i++) {
            final IMEInventoryHandler<T> inv = priorityInventory.get(i);
            final IMENetworkInventory<T> externalNetworkInventory = inv.getExternalNetworkInventory();
            if (externalNetworkInventory == this) {
                continue; // ignore any attempts to read self
            }
            final IItemList<T> passedInList = getPrimitiveItemList();
            final IItemList<T> passedOutList = inv.getAvailableItems(passedInList, iteration);

            if (externalNetworkInventory != null && passedOutList instanceof NetworkItemList) {
                networkItemList.addNetworkItems(externalNetworkInventory, passedOutList);
            } else {
                for (T item : passedOutList) {
                    currentNetworkItemList.add(item);
                }
            }
        }
        networkItemList.addNetworkItems(this, currentNetworkItemList);

        this.surface(this, Actionable.SIMULATE);

        // we're partially violating the api by making the returned list a different one from the provided one, however
        // when we're done with the network inventory scan we fulfill our api contract again
        return isSource ? networkItemList.buildFinalItemList(out) : networkItemList;
    }

    @SuppressWarnings("unchecked")
    private IItemList<T> getPrimitiveItemList() {
        return (IItemList<T>) (getChannel() == StorageChannel.ITEMS
                ? AEApi.instance().storage().createPrimitiveItemList()
                : AEApi.instance().storage().createFluidList());
    }

    @Override
    public T getAvailableItem(@Nonnull T request, int iteration) {
        long count = 0;

        final List<IMEInventoryHandler<T>> priorityInventory = this.priorityInventory;
        final int size = priorityInventory.size();
        boolean readsFromOtherNetwork = false;
        for (int i = 0; i < size; i++) {
            if (priorityInventory.get(i).getExternalNetworkInventory() != null) {
                readsFromOtherNetwork = true;
                break;
            }
        }
        if (readsFromOtherNetwork) {
            final T stack = this.getAvailableItems(getPrimitiveItemList(), iteration).findPrecise(request);
            count = addStackCount(stack, count);
        } else {
            if (this.diveIteration(this, Actionable.SIMULATE, iteration)) {
                return null;
            }
            for (int i = 0; i < size; i++) {
                IMEInventoryHandler<T> j = priorityInventory.get(i);
                final T stack = j.getAvailableItem(request, iteration);
                count = addStackCount(stack, count);
                if (count == Long.MAX_VALUE) {
                    break;
                }
            }

            this.surface(this, Actionable.SIMULATE);
        }

        return count == 0 ? null : request.copy().setStackSize(count);
    }

    private long addStackCount(T stack, long count) {
        if (stack != null && stack.getStackSize() > 0) {
            count += stack.getStackSize();
            if (count < 0) {
                // overflow
                count = Long.MAX_VALUE;
            }
        }
        return count;
    }

    private boolean diveIteration(final NetworkInventoryHandler<T> networkInventoryHandler, final Actionable type,
            int iteration) {
        if (iteration == this.myPass) {
            return true;
        }
        this.myPass = iteration;
        this.getDepth(type).push(this);
        return false;
    }

    /*
     * ME Network Inventory checker. Currently used in PartExportBus only, due to reverse-priority order checking of
     * connected network inventories.
     */
    @Override
    public Collection<T> getSortedFuzzyItems(Collection<T> out, T fuzzyItem, FuzzyMode fuzzyMode, int iteration) {
        if (this.diveIteration(this, Actionable.SIMULATE, iteration)) {
            return out;
        }

        final List<IMEInventoryHandler<T>> priorityInventory = this.priorityInventory;
        final int size = priorityInventory.size();
        for (int i = size - 1; i >= 0; i--) {
            final IMEInventoryHandler<T> invObject = priorityInventory.get(i);

            if (!invObject.isAutoCraftingInventory()) {
                final IItemList inv = invObject.getAvailableItems(invObject.getChannel().createList(), iteration);
                if (!inv.isEmpty()) {
                    final Collection fzlist = inv.findFuzzy(fuzzyItem, fuzzyMode);
                    out.addAll(fzlist);
                }
            }
        }

        this.surface(this, Actionable.SIMULATE);

        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return this.myChannel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final T input) {
        return false;
    }

    @Override
    public boolean canAccept(final T input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }

}
