/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.CraftingWatcher;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.GenericInterestManager;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.ItemSorters;
import appeng.util.item.OreListMultiMap;
import com.google.common.collect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.StreamSupport;
import net.minecraft.world.World;

public class CraftingGridCache
        implements ICraftingGrid, ICraftingProviderHelper, ICellProvider, IMEInventoryHandler<IAEStack> {

    private static final ExecutorService CRAFTING_POOL;
    private static final Comparator<ICraftingPatternDetails> COMPARATOR = new Comparator<ICraftingPatternDetails>() {
        @Override
        public int compare(final ICraftingPatternDetails firstDetail, final ICraftingPatternDetails nextDetail) {
            return nextDetail.getPriority() - firstDetail.getPriority();
        }
    };

    static {
        final ThreadFactory factory = new ThreadFactory() {

            @Override
            public Thread newThread(final Runnable ar) {
                return new Thread(ar, "AE Crafting Calculator");
            }
        };

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);
    }

    private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
    private final Set<ICraftingProvider> craftingProviders = new HashSet<>();
    private final Map<IGridNode, ICraftingWatcher> craftingWatchers = new HashMap<>();
    private final IGrid grid;
    private final Map<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods = new HashMap<>();
    // Used for fuzzy lookups
    private final OreListMultiMap<ICraftingPatternDetails> craftableItemSubstitutes = new OreListMultiMap<>();
    private final Map<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItems = new HashMap<>();
    private final Set<IAEItemStack> emitableItems = new HashSet<IAEItemStack>();
    private final Map<String, CraftingLinkNexus> craftingLinks = new HashMap<String, CraftingLinkNexus>();
    private final Multimap<IAEStack, CraftingWatcher> interests = HashMultimap.create();
    private final GenericInterestManager<CraftingWatcher> interestManager =
            new GenericInterestManager<CraftingWatcher>(this.interests);
    private IStorageGrid storageGrid;
    private IEnergyGrid energyGrid;
    private boolean updateList = false;
    private static int pauseRebuilds = 0;
    private static Set<CraftingGridCache> rebuildNeeded = new HashSet<>();

    public CraftingGridCache(final IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void afterCacheConstruction(final MENetworkPostCacheConstruction cacheConstruction) {
        this.storageGrid = this.grid.getCache(IStorageGrid.class);
        this.energyGrid = this.grid.getCache(IEnergyGrid.class);

        this.storageGrid.registerCellProvider(this);
    }

    @Override
    public void onUpdateTick() {
        if (this.updateList) {
            this.updateList = false;
            this.updateCPUClusters();
        }

        final Iterator<CraftingLinkNexus> craftingLinkIterator =
                this.craftingLinks.values().iterator();
        while (craftingLinkIterator.hasNext()) {
            if (craftingLinkIterator.next().isDead(this.grid, this)) {
                craftingLinkIterator.remove();
            }
        }

        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            cpu.updateCraftingLogic(this.grid, this.energyGrid, this);
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcher craftingWatcher = this.craftingWatchers.get(machine);
            if (craftingWatcher != null) {
                craftingWatcher.clear();
                this.craftingWatchers.remove(machine);
            }
        }

        if (machine instanceof ICraftingRequester) {
            for (final CraftingLinkNexus link : this.craftingLinks.values()) {
                if (link.isMachine(machine)) {
                    link.removeNode();
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.remove(machine);
            this.updatePatterns();
        }
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcherHost watcherHost = (ICraftingWatcherHost) machine;
            final CraftingWatcher watcher = new CraftingWatcher(this, watcherHost);
            this.craftingWatchers.put(gridNode, watcher);
            watcherHost.updateWatcher(watcher);
        }

        if (machine instanceof ICraftingRequester) {
            for (final ICraftingLink link : ((ICraftingRequester) machine).getRequestedJobs()) {
                if (link instanceof CraftingLink) {
                    this.addLink((CraftingLink) link);
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.add((ICraftingProvider) machine);
            this.updatePatterns();
        }
    }

    @Override
    public void onSplit(final IGridStorage destinationStorage) { // nothing!
    }

    @Override
    public void onJoin(final IGridStorage sourceStorage) {
        // nothing!
    }

    @Override
    public void populateGridStorage(final IGridStorage destinationStorage) {
        // nothing!
    }

    public static void pauseRebuilds() {
        pauseRebuilds++;
    }

    public static void unpauseRebuilds() {
        pauseRebuilds--;
        if (pauseRebuilds == 0 && rebuildNeeded.size() > 0) {
            ImmutableSet<CraftingGridCache> needed = ImmutableSet.copyOf(rebuildNeeded);
            rebuildNeeded.clear();
            for (CraftingGridCache cache : needed) {
                cache.updatePatterns();
            }
        }
    }

    private void updatePatterns() {
        // coalesce change events during a grid traversal to a single rebuild
        if (pauseRebuilds != 0) {
            rebuildNeeded.add(this);
            return;
        }

        final Map<IAEItemStack, ImmutableList<ICraftingPatternDetails>> oldItems = this.craftableItems;

        // erase list.
        this.craftingMethods.clear();
        this.craftableItems.clear();
        this.craftableItemSubstitutes.clear();
        this.emitableItems.clear();

        // update the stuff that was in the list...
        this.storageGrid.postAlterationOfStoredItems(StorageChannel.ITEMS, oldItems.keySet(), new BaseActionSource());

        // re-create list..
        for (final ICraftingProvider provider : this.craftingProviders) {
            provider.provideCrafting(this);
        }

        final Map<IAEItemStack, Set<ICraftingPatternDetails>> tmpCraft = new HashMap<>();

        // new craftables!
        for (final ICraftingPatternDetails details : this.craftingMethods.keySet()) {
            for (IAEItemStack out : details.getOutputs()) {
                out = out.copy();
                out.reset();
                out.setCraftable(true);

                if (details.canBeSubstitute()) {
                    craftableItemSubstitutes.put(out, details);
                }

                Set<ICraftingPatternDetails> methods = tmpCraft.computeIfAbsent(out, k -> new TreeSet<>(COMPARATOR));

                methods.add(details);
            }
        }

        craftableItemSubstitutes.freeze();

        // make them immutable
        for (final Entry<IAEItemStack, Set<ICraftingPatternDetails>> e : tmpCraft.entrySet()) {
            this.craftableItems.put(e.getKey(), ImmutableList.copyOf(e.getValue()));
        }

        this.storageGrid.postAlterationOfStoredItems(
                StorageChannel.ITEMS, this.craftableItems.keySet(), new BaseActionSource());
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (Object cls : StreamSupport.stream(grid.getMachinesClasses().spliterator(), false)
                .filter(c -> TileCraftingStorageTile.class.isAssignableFrom(c))
                .toArray()) {
            for (final IGridNode cst : this.grid.getMachines((Class<? extends IGridHost>) cls)) {
                final TileCraftingStorageTile tile = (TileCraftingStorageTile) cst.getMachine();
                final CraftingCPUCluster cluster = (CraftingCPUCluster) tile.getCluster();
                if (cluster != null) {
                    this.craftingCPUClusters.add(cluster);

                    if (cluster.getLastCraftingLink() != null) {
                        this.addLink((CraftingLink) cluster.getLastCraftingLink());
                    }
                }
            }
        }
    }

    public void addLink(final CraftingLink link) {
        if (link.isStandalone()) {
            return;
        }

        CraftingLinkNexus nexus = this.craftingLinks.get(link.getCraftingID());
        if (nexus == null) {
            this.craftingLinks.put(link.getCraftingID(), nexus = new CraftingLinkNexus(link.getCraftingID()));
        }

        link.setNexus(nexus);
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingCpuChange c) {
        this.updateList = true;
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingPatternChange c) {
        this.updatePatterns();
    }

    @Override
    public void addCraftingOption(final ICraftingMedium medium, final ICraftingPatternDetails api) {
        List<ICraftingMedium> details = this.craftingMethods.get(api);
        if (details == null) {
            details = new ArrayList<ICraftingMedium>();
            details.add(medium);
            this.craftingMethods.put(api, details);
        } else {
            details.add(medium);
        }
    }

    @Override
    public void setEmitable(final IAEItemStack someItem) {
        this.emitableItems.add(someItem.copy());
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        final List<IMEInventoryHandler> list = new ArrayList<IMEInventoryHandler>(1);

        if (channel == StorageChannel.ITEMS) {
            list.add(this);
        }

        return list;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.WRITE;
    }

    @Override
    public boolean isPrioritized(final IAEStack input) {
        return true;
    }

    @Override
    public boolean canAccept(final IAEStack input) {
        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (cpu.canAccept(input)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return i == 1;
    }

    @Override
    public IAEStack injectItems(IAEStack input, final Actionable type, final BaseActionSource src) {
        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            input = cpu.injectItems(input, type, src);
        }

        return input;
    }

    @Override
    public IAEStack extractItems(final IAEStack request, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEStack> getAvailableItems(final IItemList<IAEStack> out) {
        // add craftable items!
        for (final IAEItemStack stack : this.craftableItems.keySet()) {
            out.addCrafting(stack);
        }

        for (final IAEItemStack st : this.emitableItems) {
            out.addCrafting(st);
        }

        return out;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(
            final IAEItemStack whatToCraft,
            final ICraftingPatternDetails details,
            final int slotIndex,
            final World world) {
        final ImmutableList<ICraftingPatternDetails> res;
        boolean normalMode = false;
        if (details != null && details.canSubstitute()) {
            final ImmutableList<ICraftingPatternDetails> substitutions = this.craftableItemSubstitutes.get(whatToCraft);
            if (substitutions.isEmpty()) {
                res = this.craftableItems.get(whatToCraft);
                normalMode = true;
            } else {
                res = substitutions;
            }
        } else {
            res = this.craftableItems.get(whatToCraft);
        }

        if (res == null) {
            if (details != null && details.isCraftable()) {
                for (final IAEItemStack ais : this.craftableItems.keySet()) {
                    final boolean perfectMatch = ais.getItem() == whatToCraft.getItem()
                            && (!ais.getItem().getHasSubtypes() || ais.getItemDamage() == whatToCraft.getItemDamage());
                    if (perfectMatch) {
                        if (details.isValidItemForSlot(slotIndex, ais.getItemStack(), world)) {
                            return this.craftableItems.get(ais);
                        }
                    }
                }
                // no perfect match found, look for substitutions
                if (details.canSubstitute() && !normalMode) {
                    for (final Map.Entry<IAEItemStack, ImmutableList<ICraftingPatternDetails>> entry :
                            this.craftableItems.entrySet()) {
                        final boolean canBeASubstitute =
                                entry.getValue().stream().anyMatch(cp -> (cp != null) && cp.canBeSubstitute());
                        if (canBeASubstitute && entry.getKey().fuzzyComparison(whatToCraft, FuzzyMode.IGNORE_ALL)) {
                            if (details.isValidItemForSlot(
                                    slotIndex, entry.getKey().getItemStack(), world)) {
                                return entry.getValue();
                            }
                        }
                    }
                }
            }

            return ImmutableSet.of();
        }

        return res;
    }

    @Override
    public Future<ICraftingJob> beginCraftingJob(
            final World world,
            final IGrid grid,
            final BaseActionSource actionSrc,
            final IAEItemStack slotItem,
            final ICraftingCallback cb) {
        if (world == null || grid == null || actionSrc == null || slotItem == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final CraftingJob job = new CraftingJob(world, grid, actionSrc, slotItem, cb);

        return CRAFTING_POOL.submit(job, (ICraftingJob) job);
    }

    @Override
    public ICraftingLink submitJob(
            final ICraftingJob job,
            final ICraftingRequester requestingMachine,
            final ICraftingCPU target,
            final boolean prioritizePower,
            final BaseActionSource src) {
        if (job.isSimulation()) {
            return null;
        }

        CraftingCPUCluster cpuCluster = null;

        if (target instanceof CraftingCPUCluster) {
            cpuCluster = (CraftingCPUCluster) target;
        }

        if (target == null) {
            final List<CraftingCPUCluster> validCpusClusters = new ArrayList<CraftingCPUCluster>();
            for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
                if (cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.getByteTotal()) {
                    validCpusClusters.add(cpu);
                }
            }

            Collections.sort(validCpusClusters, new Comparator<CraftingCPUCluster>() {
                private int compareInternal(CraftingCPUCluster firstCluster, CraftingCPUCluster nextCluster) {
                    int comparison =
                            ItemSorters.compareLong(nextCluster.getCoProcessors(), firstCluster.getCoProcessors());
                    if (comparison == 0)
                        comparison = ItemSorters.compareLong(
                                nextCluster.getAvailableStorage(), firstCluster.getAvailableStorage());
                    if (comparison == 0) return nextCluster.getName().compareTo(firstCluster.getName());
                    else return comparison;
                }

                @Override
                public int compare(final CraftingCPUCluster firstCluster, final CraftingCPUCluster nextCluster) {
                    if (prioritizePower) return compareInternal(firstCluster, nextCluster);
                    else return compareInternal(nextCluster, firstCluster);
                }
            });

            if (!validCpusClusters.isEmpty()) {
                cpuCluster = validCpusClusters.get(0);
            }
        }

        if (cpuCluster != null) {
            return cpuCluster.submitJob(this.grid, job, src, requestingMachine);
        }

        return null;
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        return ImmutableSet.copyOf(new ActiveCpuIterator(this.craftingCPUClusters));
    }

    @Override
    public boolean canEmitFor(final IAEItemStack someItem) {
        return this.emitableItems.contains(someItem);
    }

    @Override
    public boolean isRequesting(final IAEItemStack what) {
        for (final CraftingCPUCluster cluster : this.craftingCPUClusters) {
            if (cluster.isMaking(what)) {
                return true;
            }
        }

        return false;
    }

    public List<ICraftingMedium> getMediums(final ICraftingPatternDetails key) {
        List<ICraftingMedium> mediums = this.craftingMethods.get(key);

        if (mediums == null) {
            mediums = ImmutableList.of();
        }

        return mediums;
    }

    public boolean hasCpu(final ICraftingCPU cpu) {
        return this.craftingCPUClusters.contains(cpu);
    }

    public GenericInterestManager<CraftingWatcher> getInterestManager() {
        return this.interestManager;
    }

    private static class ActiveCpuIterator implements Iterator<ICraftingCPU> {

        private final Iterator<CraftingCPUCluster> iterator;
        private CraftingCPUCluster cpuCluster;

        public ActiveCpuIterator(final Collection<CraftingCPUCluster> o) {
            this.iterator = o.iterator();
            this.cpuCluster = null;
        }

        @Override
        public boolean hasNext() {
            this.findNext();

            return this.cpuCluster != null;
        }

        private void findNext() {
            while (this.iterator.hasNext() && this.cpuCluster == null) {
                this.cpuCluster = this.iterator.next();
                if (!this.cpuCluster.isActive() || this.cpuCluster.isDestroyed()) {
                    this.cpuCluster = null;
                }
            }
        }

        @Override
        public ICraftingCPU next() {
            final ICraftingCPU o = this.cpuCluster;
            this.cpuCluster = null;

            return o;
        }

        @Override
        public void remove() {
            // no..
        }
    }
}
