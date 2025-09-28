/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.IMachineSet;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.util.IReadOnlyCollection;
import appeng.core.AEConfig;
import appeng.core.worlddata.WorldData;
import appeng.hooks.TickHandler;
import appeng.me.cache.CraftingGridCache;
import appeng.parts.misc.PartStorageBus;
import appeng.util.ReadOnlyCollection;

public class Grid implements IGrid {

    private final UUID id;

    private final NetworkEventBus eventBus = new NetworkEventBus();
    private final Map<Class<? extends IGridHost>, MachineSet> machines = new HashMap<>();
    private final Map<Class<? extends IGridCache>, IGridCache> caches = new HashMap<>();
    private GridNode pivot;
    private int priority; // how import is this network?
    private GridStorage myStorage;
    private int[] timeStatistics = null;
    private static final int PROFILING_SAMPLE_COUNT = 200;
    private int timeStatisticsIndex = 0;
    private boolean profilingPassedFullCycle = false;

    public Grid(final GridNode center) {
        this.pivot = center;
        this.id = UUID.randomUUID();

        final Map<Class<? extends IGridCache>, IGridCache> myCaches = AEApi.instance().registries().gridCache()
                .createCacheInstance(this);
        for (final Entry<Class<? extends IGridCache>, IGridCache> c : myCaches.entrySet()) {
            final Class<? extends IGridCache> key = c.getKey();
            final IGridCache value = c.getValue();
            final Class<? extends IGridCache> valueClass = value.getClass();

            this.eventBus.readClass(key, valueClass);
            this.caches.put(key, value);
        }

        this.postEvent(new MENetworkPostCacheConstruction());

        TickHandler.INSTANCE.addNetwork(this);
        center.setGrid(this);
    }

    int getPriority() {
        return this.priority;
    }

    IGridStorage getMyStorage() {
        return this.myStorage;
    }

    Map<Class<? extends IGridCache>, IGridCache> getCaches() {
        return this.caches;
    }

    public Iterable<Class<? extends IGridHost>> getMachineClasses() {
        return this.machines.keySet();
    }

    int size() {
        int out = 0;
        for (final Collection<?> x : this.machines.values()) {
            out += x.size();
        }
        return out;
    }

    void remove(final GridNode gridNode) {
        for (final IGridCache c : this.caches.values()) {
            final IGridHost machine = gridNode.getMachine();
            c.removeNode(gridNode, machine);
        }

        final Class<? extends IGridHost> machineClass = gridNode.getMachineClass();
        final Set<IGridNode> nodes = this.machines.get(machineClass);
        if (nodes != null) {
            nodes.remove(gridNode);
        }

        gridNode.setGridStorage(null);

        if (this.pivot == gridNode) {
            final Iterator<IGridNode> n = this.getNodes().iterator();
            if (n.hasNext()) {
                this.pivot = (GridNode) n.next();
            } else {
                this.pivot = null;
                TickHandler.INSTANCE.removeNetwork(this);
                this.myStorage.remove();
            }
        }
    }

    void add(final GridNode gridNode) {
        final Class<? extends IGridHost> mClass = gridNode.getMachineClass();

        MachineSet nodes = this.machines.get(mClass);
        if (nodes == null) {
            nodes = new MachineSet(mClass);
            this.machines.put(mClass, nodes);
            this.eventBus.readClass(mClass, mClass);
        }

        // handle loading grid storages.
        if (gridNode.getGridStorage() != null) {
            final GridStorage gs = gridNode.getGridStorage();
            final IGrid grid = gs.getGrid();

            if (grid == null) {
                this.myStorage = gs;
                this.myStorage.setGrid(this);

                for (final IGridCache gc : this.caches.values()) {
                    gc.onJoin(this.myStorage);
                }
            } else if (grid != this) {
                if (this.myStorage == null) {
                    this.myStorage = WorldData.instance().storageData().getNewGridStorage();
                    this.myStorage.setGrid(this);
                }

                final IGridStorage tmp = new GridStorage();
                if (!gs.hasDivided(this.myStorage)) {
                    gs.addDivided(this.myStorage);

                    for (final IGridCache gc : ((Grid) grid).caches.values()) {
                        gc.onSplit(tmp);
                    }

                    for (final IGridCache gc : this.caches.values()) {
                        gc.onJoin(tmp);
                    }
                }
            }
        } else if (this.myStorage == null) {
            this.myStorage = WorldData.instance().storageData().getNewGridStorage();
            this.myStorage.setGrid(this);
        }

        // update grid node...
        gridNode.setGridStorage(this.myStorage);

        // track node.
        nodes.add(gridNode);

        for (final IGridCache cache : this.caches.values()) {
            final IGridHost machine = gridNode.getMachine();
            cache.addNode(gridNode, machine);
        }

        gridNode.getGridProxy().gridChanged();
        // postEventTo( gridNode, networkChanged );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends IGridCache> C getCache(final Class<? extends IGridCache> iface) {
        return (C) this.caches.get(iface);
    }

    @Override
    public MENetworkEvent postEvent(final MENetworkEvent ev) {
        CraftingGridCache.pauseRebuilds();
        final MENetworkEvent ret = this.eventBus.postEvent(this, ev);
        CraftingGridCache.unpauseRebuilds();
        return ret;
    }

    @Override
    public MENetworkEvent postEventTo(final IGridNode node, final MENetworkEvent ev) {
        return this.eventBus.postEventTo(this, (GridNode) node, ev);
    }

    @Override
    public IReadOnlyCollection<Class<? extends IGridHost>> getMachinesClasses() {
        final Set<Class<? extends IGridHost>> machineKeys = this.machines.keySet();

        return new ReadOnlyCollection<>(machineKeys);
    }

    @Override
    public IMachineSet getMachines(final Class<? extends IGridHost> c) {
        final MachineSet s = this.machines.get(c);
        if (s == null) {
            return new MachineSet(c);
        }
        return s;
    }

    @Override
    public IReadOnlyCollection<IGridNode> getNodes() {
        return new GridNodeCollection(this.machines);
    }

    @Override
    public boolean isEmpty() {
        return this.pivot == null;
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public IGridNode getPivot() {
        return this.pivot;
    }

    void setPivot(final GridNode pivot) {
        this.pivot = pivot;
    }

    public void startProfiling() {
        timeStatistics = new int[PROFILING_SAMPLE_COUNT];
        profilingPassedFullCycle = false;
    }

    public int stopProfiling() {
        if (timeStatistics == null) return 0;
        long sum = 0;
        int N = profilingPassedFullCycle ? PROFILING_SAMPLE_COUNT : timeStatisticsIndex;
        for (int i = 0; i < N; ++i) sum += timeStatistics[i];
        timeStatistics = null;
        return (int) (sum / N);
    }

    public boolean isProfiling() {
        return timeStatistics != null;
    }

    public void update() {
        long time = 0;
        if (isProfiling()) time = System.nanoTime();
        for (final IGridCache gc : this.caches.values()) {
            // are there any nodes left?
            if (this.pivot != null) {
                gc.onUpdateTick();
            }
        }
        if (isProfiling()) {
            ++timeStatisticsIndex;
            if (timeStatisticsIndex == PROFILING_SAMPLE_COUNT) {
                profilingPassedFullCycle = true;
                timeStatisticsIndex = 0;
            }
            timeStatistics[timeStatisticsIndex] = (int) (System.nanoTime() - time);
        }
    }

    void saveState() {
        for (final IGridCache c : this.caches.values()) {
            c.populateGridStorage(this.myStorage);
        }
    }

    public void setImportantFlag(final int i, final boolean publicHasPower) {
        final int flag = 1 << i;
        this.priority = (this.priority & ~flag) | (publicHasPower ? flag : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Grid grid) {
            return this.id.equals(grid.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId());
    }

    @Override
    public NetworkList getGridConnections(Class<? extends IGridHost> accessType) {
        NetworkList result = new NetworkList();
        result.add(this);
        HashMap<IGridHost, IGrid> gridConnections = this.getSubnetGridMap(accessType);
        for (Entry<IGridHost, IGrid> entry : gridConnections.entrySet()) {
            if (accessType.isInstance(entry.getKey())) {
                if (!result.contains((Grid) entry.getValue())) result.add((Grid) entry.getValue());
            }
        }
        return result;
    }

    @Override
    public NetworkList getAllRecursiveGridConnections(Class<? extends IGridHost> accessType) {
        if (accessType == null) return null;
        return getAllRecursiveGridConnections(accessType, new HashSet<>(), 0);
    }

    private HashMap<IGridHost, IGrid> getSubnetGridMap(Class<? extends IGridHost> accessType) {
        IMachineSet storageBuses = this.getMachines(PartStorageBus.class);
        HashMap<IGridHost, IGrid> gridConnections = new HashMap<>();
        for (IGridNode bus : storageBuses) {
            if (bus.getMachine() instanceof PartStorageBus sb) { // TODO Support partFluidStorageBus
                IGrid connectedGrid = sb.getConnectedGrid();
                if (connectedGrid != null) gridConnections.put(sb, sb.getConnectedGrid());
            }
        }
        return gridConnections;
    }

    private NetworkList getAllRecursiveGridConnections(Class<? extends IGridHost> accessType, Set<UUID> visited,
            int depth) {
        NetworkList result = this.getGridConnections(accessType);
        if (depth > AEConfig.instance.maxRecursiveDepth) return result;

        HashMap<IGridHost, IGrid> gridConnections = this.getSubnetGridMap(accessType);
        for (Entry<IGridHost, IGrid> entry : gridConnections.entrySet()) {
            if (accessType.isInstance(entry.getKey())) {
                Grid innerGrid = (Grid) entry.getValue();

                if (innerGrid == null || visited.contains(innerGrid.getId())) {
                    continue; // skip to avoid infinite loop
                }

                visited.add(innerGrid.getId());
                if (!result.contains(innerGrid)) result.add(innerGrid);

                NetworkList subResult = innerGrid.getAllRecursiveGridConnections(accessType, visited, ++depth);
                result.mergeDistinct(subResult);
            }
        }

        return result;
    }
}
