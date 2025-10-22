/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.cache;

import static appeng.api.storage.data.IItemList.LIST_FLUID;
import static appeng.api.storage.data.IItemList.LIST_ITEM;
import static appeng.api.storage.data.IItemList.LIST_MIXED;
import static appeng.util.Platform.convertStack;
import static appeng.util.Platform.stackConvert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import net.minecraft.world.World;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingAllow;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.CraftingWatcher;
import appeng.crafting.v2.CraftingJobV2;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.GenericInterestManager;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.ItemSorters;
import appeng.util.item.OreListMultiMap;

public class CraftingGridCache
        implements ICraftingGrid, ICraftingProviderHelper, ICellProvider, IMEInventoryHandler<IAEStack> {

    private static final ExecutorService CRAFTING_POOL;
    private static final Comparator<ICraftingPatternDetails> COMPARATOR = (firstDetail,
            nextDetail) -> nextDetail.getPriority() - firstDetail.getPriority();

    static {
        final ThreadFactory factory = ar -> new Thread(ar, "AE Crafting Calculator");

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);
    }

    private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
    private final Set<ICraftingProvider> craftingProviders = new HashSet<>();
    private final Map<IGridNode, ICraftingWatcher> craftingWatchers = new HashMap<>();
    private final IGrid grid;
    private final Map<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods = new HashMap<>();
    // Used for fuzzy lookups
    private final OreListMultiMap<ICraftingPatternDetails> craftableItemSubstitutes = new OreListMultiMap<>();
    private final Map<IAEItemStack, ImmutableList<ICraftingPatternDetails>> craftableItemsLegacy = new HashMap<>();
    private final Map<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> craftableItems = new HashMap<>();
    private final Set<IAEStack<?>> emitableItems = new HashSet<>();
    private final Map<String, CraftingLinkNexus> craftingLinks = new HashMap<>();
    private final Multimap<IAEStack, CraftingWatcher> interests = HashMultimap.create();
    private final GenericInterestManager<CraftingWatcher> interestManager = new GenericInterestManager<>(
            this.interests);
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

        this.craftingLinks.values().removeIf(craftingLinkNexus -> craftingLinkNexus.isDead(this.grid, this));

        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            cpu.tryExtractItems();
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
        if (machine instanceof ICraftingWatcherHost watcherHost) {
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

        final Map<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> oldItems = this.craftableItems;

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

        setPatternsFromCraftingMethods();

        this.storageGrid.postAlterationOfStoredItems(
                StorageChannel.ITEMS,
                this.craftableItems.keySet().stream().filter(IAEStack::isItem).collect(Collectors.toList()),
                new BaseActionSource());
        this.storageGrid.postAlterationOfStoredItems(
                StorageChannel.FLUIDS,
                this.craftableItems.keySet().stream().filter(IAEStack::isFluid).collect(Collectors.toList()),
                new BaseActionSource());
    }

    /** Only for unit test usage */
    public void setMockPatternsFromMethods() {
        this.craftableItems.clear();
        this.craftableItemSubstitutes.clear();
        this.emitableItems.clear();
        setPatternsFromCraftingMethods();
    }

    private void setPatternsFromCraftingMethods() {
        final Map<IAEStack<?>, Set<ICraftingPatternDetails>> tmpCraft = new HashMap<>();

        // new craftables!
        for (final ICraftingPatternDetails details : this.craftingMethods.keySet()) {
            for (IAEStack<?> out : details.getAEOutputs()) {
                out = out.copy();
                out.reset();
                out.setCraftable(true);

                if (details.canBeSubstitute() && out instanceof IAEItemStack ais) {
                    craftableItemSubstitutes.put(ais, details);
                }

                Set<ICraftingPatternDetails> methods = tmpCraft.computeIfAbsent(out, k -> new TreeSet<>(COMPARATOR));

                methods.add(details);
            }
        }

        craftableItemSubstitutes.freeze();

        // make them immutable
        for (final Entry<IAEStack<?>, Set<ICraftingPatternDetails>> e : tmpCraft.entrySet()) {
            this.craftableItems.put(e.getKey(), ImmutableList.copyOf(e.getValue()));

            craftableItemsLegacy.put(stackConvert(e.getKey()), ImmutableList.copyOf(e.getValue()));
        }
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (Object cls : StreamSupport.stream(grid.getMachinesClasses().spliterator(), false)
                .filter(TileCraftingStorageTile.class::isAssignableFrom).toArray()) {
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
            details = new ArrayList<>();
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
    public void setEmitable(final IAEStack<?> someItem) {
        this.emitableItems.add(someItem.copy());
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(final StorageChannel channel) {
        return Arrays.asList(this);
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
    public boolean isAutoCraftingInventory() {
        return true;
    }

    @Override
    public IAEStack injectItems(IAEStack input, final Actionable type, final BaseActionSource src) {
        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (input == null) break;
            input = cpu.injectItems(input, type, src);
        }

        return input;
    }

    @Override
    public IAEStack extractItems(final IAEStack request, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEStack> getAvailableItems(final IItemList<IAEStack> out, int iteration) {

        // add craftable items!
        for (final IAEStack<?> stack : this.craftableItems.keySet()) {
            if (stack instanceof IAEFluidStack afs) {
                if (out.getStackType() == LIST_MIXED || out.getStackType() == LIST_FLUID) {
                    out.addCrafting(afs);
                }
            } else {
                if (out.getStackType() == LIST_MIXED || out.getStackType() == LIST_ITEM) {
                    out.addCrafting(stack);
                }
            }
        }

        for (final IAEStack<?> stack : emitableItems) {
            if (stack instanceof IAEFluidStack afs) {
                if (out.getStackType() == LIST_MIXED || out.getStackType() == LIST_FLUID) {
                    out.addCrafting(afs);
                }
            } else {
                if (out.getStackType() == LIST_MIXED || out.getStackType() == LIST_ITEM) {
                    out.addCrafting(stack);
                }
            }
        }

        return out;
    }

    @Override
    public IAEStack getAvailableItem(@Nonnull IAEStack request, int iteration) {
        return null;
    }

    @Override
    public StorageChannel getChannel() {
        return null;
    }

    @Override
    public ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns() {
        return ImmutableMap.copyOf(this.craftableItems);
    }

    @Override
    public ImmutableMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> getCraftingPatterns() {
        return ImmutableMap.copyOf(this.craftableItemsLegacy);
    }

    @Override
    public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(final IAEItemStack whatToCraft,
            final ICraftingPatternDetails details, final int slotIndex, final World world) {
        return getCraftingFor(convertStack(whatToCraft), details, slotIndex, world);
    }

    @Override
    public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(final IAEStack<?> whatToCraft,
            final ICraftingPatternDetails details, final int slotIndex, final World world) {
        if (whatToCraft instanceof IAEItemStack wtc) {
            final ImmutableList<ICraftingPatternDetails> res = this.craftableItems.get(whatToCraft);

            if (res == null) {
                if (details != null && details.isCraftable()) {
                    for (final IAEStack<?> aes : this.craftableItems.keySet()) {
                        if (aes instanceof IAEItemStack ais && ais.getItem() == wtc.getItem()
                                && (!ais.getItem().getHasSubtypes() || ais.getItemDamage() == wtc.getItemDamage())) {
                            if (details.isValidItemForSlot(slotIndex, ais.getItemStack(), world)) {
                                return this.craftableItems.get(ais);
                            }
                        }
                    }
                }

                return ImmutableSet.of();
            }

            return res;
        }

        return ImmutableSet.of();
    }

    /**
     * @return The task pool for executing crafting calculations.
     */
    public static ExecutorService getCraftingPool() {
        return CRAFTING_POOL;
    }

    @Override
    public Future<ICraftingJob> beginCraftingJob(final World world, final IGrid grid, final BaseActionSource actionSrc,
            final IAEItemStack slotItem, final ICraftingCallback cb) {
        return beginCraftingJob(world, grid, actionSrc, convertStack(slotItem), CraftingMode.STANDARD, cb);
    }

    @Override
    public Future<ICraftingJob> beginCraftingJob(final World world, final IGrid grid, final BaseActionSource actionSrc,
            final IAEStack<?> stack, final ICraftingCallback cb) {
        return beginCraftingJob(world, grid, actionSrc, stack, CraftingMode.STANDARD, cb);
    }

    public Future<ICraftingJob> beginCraftingJob(final World world, final IGrid grid, final BaseActionSource actionSrc,
            final IAEStack<?> stack, final CraftingMode craftingMode, final ICraftingCallback cb) {
        if (world == null || grid == null || actionSrc == null || stack == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final ICraftingJob job = new CraftingJobV2<>(world, grid, actionSrc, (IAEStack) stack, craftingMode, cb);

        return job.schedule();
    }

    @Override
    public ICraftingLink submitJob(ICraftingJob job, ICraftingRequester requestingMachine, ICraftingCPU target,
            boolean prioritizePower, BaseActionSource src) {
        return submitJob(job, requestingMachine, target, prioritizePower, src, false);
    }

    @Override
    public ICraftingLink submitJob(final ICraftingJob job, final ICraftingRequester requestingMachine,
            final ICraftingCPU target, final boolean prioritizePower, final BaseActionSource src,
            final boolean followCraft) {
        if (job.isSimulation()) {
            return null;
        }

        CraftingCPUCluster cpuCluster = null;

        if (target instanceof CraftingCPUCluster) {
            cpuCluster = (CraftingCPUCluster) target;
        }

        if (target == null) {
            final List<CraftingCPUCluster> validCpusClusters = new ArrayList<>();
            for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {

                boolean canOrder = false;
                // This cpu can be merge
                canOrder |= (cpu.isActive() && cpu.isBusy()
                        && job.getOutput().isSameType((Object) cpu.getFinalMultiOutput())
                        && cpu.getAvailableStorage() >= cpu.getUsedStorage() + job.getByteTotal());
                // Or this cpu is idle
                canOrder |= (cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.getByteTotal());

                if (canOrder) {
                    if (src.isPlayer() && cpu.getCraftingAllowMode() != CraftingAllow.ONLY_NONPLAYER) {
                        // If is player requests and CraftingAllowMode is not ONLY_NONPLAYER
                        validCpusClusters.add(cpu);
                    } else if (!src.isPlayer() && cpu.getCraftingAllowMode() != CraftingAllow.ONLY_PLAYER) {
                        // If is non-player requests and CraftingAllowMode is not ONLY_PLAYER
                        validCpusClusters.add(cpu);
                    }
                }
            }

            validCpusClusters.sort(new Comparator<>() {

                private int compareInternal(CraftingCPUCluster firstCluster, CraftingCPUCluster nextCluster) {
                    int comparison = ItemSorters
                            .compareLong(nextCluster.getCoProcessors(), firstCluster.getCoProcessors());
                    if (comparison == 0) comparison = ItemSorters
                            .compareLong(nextCluster.getAvailableStorage(), firstCluster.getAvailableStorage());
                    if (comparison == 0) return nextCluster.getName().compareTo(firstCluster.getName());
                    else return comparison;
                }

                @Override
                public int compare(final CraftingCPUCluster firstCluster, final CraftingCPUCluster nextCluster) {
                    if (firstCluster.isBusy() != nextCluster.isBusy()) {
                        return Boolean.compare(nextCluster.isBusy(), firstCluster.isBusy());
                    }
                    if (prioritizePower) return compareInternal(firstCluster, nextCluster);
                    else return compareInternal(nextCluster, firstCluster);
                }
            });

            if (!validCpusClusters.isEmpty()) {
                cpuCluster = validCpusClusters.get(0);
            }
        }

        if (cpuCluster != null) {
            ICraftingLink submittedJob = cpuCluster.submitJob(this.grid, job, src, requestingMachine);

            if (src instanceof PlayerSource playerSource && followCraft) {
                cpuCluster.togglePlayerFollowStatus(playerSource.player.getCommandSenderName());
            }

            return submittedJob;
        }

        return null;
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        return ImmutableSet.copyOf(new ActiveCpuIterator(this.craftingCPUClusters));
    }

    @Override
    public boolean canEmitFor(final IAEStack<?> someItem) {
        return this.emitableItems.contains(someItem);
    }

    @Override
    public boolean canEmitFor(final IAEItemStack someItem) {
        return this.emitableItems.contains(convertStack(someItem));
    }

    @Override
    public boolean isRequesting(final IAEStack<?> what) {
        for (final CraftingCPUCluster cluster : this.craftingCPUClusters) {
            if (cluster.isMaking(what)) {
                return true;
            }
        }

        return false;
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
