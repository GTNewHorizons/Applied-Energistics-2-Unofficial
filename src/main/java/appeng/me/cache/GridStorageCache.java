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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellCacheRegistry;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AEConfig;
import appeng.me.helpers.GenericInterestManager;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.ItemWatcher;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;

public class GridStorageCache implements IStorageGrid {

    private final IGrid myGrid;
    private final HashSet<ICellProvider> activeCellProviders = new HashSet<>();
    private final HashSet<ICellProvider> inactiveCellProviders = new HashSet<>();
    private final SetMultimap<IAEStack, ItemWatcher> interests = HashMultimap.create();
    private final GenericInterestManager<ItemWatcher> interestManager = new GenericInterestManager<>(this.interests);
    private final NetworkMonitor<IAEItemStack> itemMonitor = new NetworkMonitor<>(this, StorageChannel.ITEMS);
    private final NetworkMonitor<IAEFluidStack> fluidMonitor = new NetworkMonitor<>(this, StorageChannel.FLUIDS);
    private final HashMap<IGridNode, IStackWatcher> watchers = new HashMap<>();
    private NetworkInventoryHandler<IAEItemStack> myItemNetwork;
    private NetworkInventoryHandler<IAEFluidStack> myFluidNetwork;
    private double itemBytesTotal;
    private double itemBytesUsed;
    private long itemTypesTotal;
    private long itemTypesUsed;
    private long itemCellG;
    private long itemCellO;
    private long itemCellR;
    private long itemCellCount;
    private double fluidBytesTotal;
    private double fluidBytesUsed;
    private long fluidTypesTotal;
    private long fluidTypesUsed;
    private long fluidCellG;
    private long fluidCellO;
    private long fluidCellR;
    private long fluidCellCount;
    private double essentiaBytesTotal;
    private double essentiaBytesUsed;
    private long essentiaTypesTotal;
    private long essentiaTypesUsed;
    private long essentiaCellG;
    private long essentiaCellO;
    private long essentiaCellR;
    private long essentiaCellCount;
    private int ticksCount;
    private int networkBytesUpdateFrequency;
    private static final int CELL_RED = 3;
    private static final int CELL_ORANGE = 2;
    private static final int CELL_GREEN = 1;

    public GridStorageCache(final IGrid g) {
        this.myGrid = g;
        this.networkBytesUpdateFrequency = (int) (AEConfig.instance.networkBytesUpdateFrequency * 20);
        this.ticksCount = this.networkBytesUpdateFrequency;
    }

    @Override
    public void onUpdateTick() {
        this.itemMonitor.onTick();
        this.fluidMonitor.onTick();

        // update every 100Ticks by default
        if (this.ticksCount < this.networkBytesUpdateFrequency) {
            this.ticksCount++;
        } else {
            this.ticksCount = 0;
            this.updateBytesInfo();
        }
    }

    @Override
    public void removeNode(final IGridNode node, final IGridHost machine) {
        if (machine instanceof ICellContainer cc) {
            final CellChangeTracker tracker = new CellChangeTracker();

            this.removeCellProvider(cc, tracker);
            this.inactiveCellProviders.remove(cc);
            this.getGrid().postEvent(new MENetworkCellArrayUpdate());

            tracker.applyChanges();
        }

        if (machine instanceof IStackWatcherHost) {
            final IStackWatcher myWatcher = this.watchers.get(machine);

            if (myWatcher != null) {
                myWatcher.clear();
                this.watchers.remove(machine);
            }
        }
    }

    @Override
    public void addNode(final IGridNode node, final IGridHost machine) {
        if (machine instanceof ICellContainer cc) {
            this.inactiveCellProviders.add(cc);

            this.getGrid().postEvent(new MENetworkCellArrayUpdate());

            if (node.isActive()) {
                final CellChangeTracker tracker = new CellChangeTracker();

                this.addCellProvider(cc, tracker);
                tracker.applyChanges();
            }
        }

        if (machine instanceof IStackWatcherHost swh) {
            final ItemWatcher iw = new ItemWatcher(this, swh);
            this.watchers.put(node, iw);
            swh.updateWatcher(iw);
        }
    }

    @Override
    public void onSplit(final IGridStorage storageB) {}

    @Override
    public void onJoin(final IGridStorage storageB) {}

    @Override
    public void populateGridStorage(final IGridStorage storage) {}

    private CellChangeTracker addCellProvider(final ICellProvider cc, final CellChangeTracker tracker) {
        if (this.inactiveCellProviders.contains(cc)) {
            this.inactiveCellProviders.remove(cc);
            this.activeCellProviders.add(cc);

            BaseActionSource actionSrc = new BaseActionSource();
            if (cc instanceof IActionHost) {
                actionSrc = new MachineSource((IActionHost) cc);
            }

            for (final IMEInventoryHandler<IAEItemStack> h : cc.getCellArray(StorageChannel.ITEMS)) {
                tracker.postChanges(StorageChannel.ITEMS, 1, h, actionSrc);
            }

            for (final IMEInventoryHandler<IAEFluidStack> h : cc.getCellArray(StorageChannel.FLUIDS)) {
                tracker.postChanges(StorageChannel.FLUIDS, 1, h, actionSrc);
            }
        }

        return tracker;
    }

    private CellChangeTracker removeCellProvider(final ICellProvider cc, final CellChangeTracker tracker) {
        if (this.activeCellProviders.contains(cc)) {
            this.activeCellProviders.remove(cc);
            this.inactiveCellProviders.add(cc);

            BaseActionSource actionSrc = new BaseActionSource();

            if (cc instanceof IActionHost) {
                actionSrc = new MachineSource((IActionHost) cc);
            }

            for (final IMEInventoryHandler<IAEItemStack> h : cc.getCellArray(StorageChannel.ITEMS)) {
                tracker.postChanges(StorageChannel.ITEMS, -1, h, actionSrc);
            }

            for (final IMEInventoryHandler<IAEFluidStack> h : cc.getCellArray(StorageChannel.FLUIDS)) {
                tracker.postChanges(StorageChannel.FLUIDS, -1, h, actionSrc);
            }
        }

        return tracker;
    }

    @MENetworkEventSubscribe
    public void cellUpdate(final MENetworkCellArrayUpdate ev) {
        this.myItemNetwork = null;
        this.myFluidNetwork = null;

        final LinkedList<ICellProvider> ll = new LinkedList();
        ll.addAll(this.inactiveCellProviders);
        ll.addAll(this.activeCellProviders);

        final CellChangeTracker tracker = new CellChangeTracker();

        for (final ICellProvider cc : ll) {
            boolean active = true;

            if (cc instanceof IActionHost) {
                final IGridNode node = ((IActionHost) cc).getActionableNode();
                active = node != null && node.isActive();
            }

            if (active) {
                this.addCellProvider(cc, tracker);
            } else {
                this.removeCellProvider(cc, tracker);
            }
        }

        this.itemMonitor.forceUpdate();
        this.fluidMonitor.forceUpdate();

        tracker.applyChanges();

    }

    private void postChangesToNetwork(final StorageChannel chan, final int upOrDown, final IItemList availableItems,
            final BaseActionSource src) {
        switch (chan) {
            case FLUIDS -> this.fluidMonitor.postChange(upOrDown > 0, availableItems, src);
            case ITEMS -> this.itemMonitor.postChange(upOrDown > 0, availableItems, src);
            default -> {}
        }
    }

    IMEInventoryHandler<IAEItemStack> getItemInventoryHandler() {
        if (this.myItemNetwork == null) {
            this.buildNetworkStorage(StorageChannel.ITEMS);
        }
        return this.myItemNetwork;
    }

    private void buildNetworkStorage(final StorageChannel chan) {
        final SecurityCache security = this.getGrid().getCache(ISecurityGrid.class);

        switch (chan) {
            case FLUIDS -> {
                this.myFluidNetwork = new NetworkInventoryHandler<>(StorageChannel.FLUIDS, security);
                for (final ICellProvider cc : this.activeCellProviders) {
                    for (final IMEInventoryHandler<IAEFluidStack> h : cc.getCellArray(chan)) {
                        this.myFluidNetwork.addNewStorage(h);
                    }
                }
            }
            case ITEMS -> {
                this.myItemNetwork = new NetworkInventoryHandler<>(StorageChannel.ITEMS, security);
                for (final ICellProvider cc : this.activeCellProviders) {
                    for (final IMEInventoryHandler<IAEItemStack> h : cc.getCellArray(chan)) {
                        this.myItemNetwork.addNewStorage(h);
                    }
                }
            }
            default -> {}
        }
    }

    IMEInventoryHandler<IAEFluidStack> getFluidInventoryHandler() {
        if (this.myFluidNetwork == null) {
            this.buildNetworkStorage(StorageChannel.FLUIDS);
        }
        return this.myFluidNetwork;
    }

    @Override
    public void postAlterationOfStoredItems(final StorageChannel chan, final Iterable<? extends IAEStack> input,
            final BaseActionSource src) {
        if (chan == StorageChannel.ITEMS) {
            this.itemMonitor.postChange(true, (Iterable<IAEItemStack>) input, src);
        } else if (chan == StorageChannel.FLUIDS) {
            this.fluidMonitor.postChange(true, (Iterable<IAEFluidStack>) input, src);
        }
    }

    @Override
    public void registerCellProvider(final ICellProvider provider) {
        this.inactiveCellProviders.add(provider);
        this.addCellProvider(provider, new CellChangeTracker()).applyChanges();
    }

    @Override
    public void unregisterCellProvider(final ICellProvider provider) {
        this.removeCellProvider(provider, new CellChangeTracker()).applyChanges();
        this.inactiveCellProviders.remove(provider);
    }

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        return this.itemMonitor;
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        return this.fluidMonitor;
    }

    public GenericInterestManager<ItemWatcher> getInterestManager() {
        return this.interestManager;
    }

    IGrid getGrid() {
        return this.myGrid;
    }

    private class CellChangeTrackerRecord {

        final StorageChannel channel;
        final int up_or_down;
        final IItemList list;
        final BaseActionSource src;

        public CellChangeTrackerRecord(final StorageChannel channel, final int i,
                final IMEInventoryHandler<? extends IAEStack> h, final BaseActionSource actionSrc) {
            this.channel = channel;
            this.up_or_down = i;
            this.src = actionSrc;

            if (channel == StorageChannel.ITEMS) {
                this.list = ((IMEInventoryHandler<IAEItemStack>) h)
                        .getAvailableItems(AEApi.instance().storage().createItemList());
            } else if (channel == StorageChannel.FLUIDS) {
                this.list = ((IMEInventoryHandler<IAEFluidStack>) h)
                        .getAvailableItems(AEApi.instance().storage().createFluidList());
            } else {
                this.list = null;
            }
        }

        public void applyChanges() {
            GridStorageCache.this.postChangesToNetwork(this.channel, this.up_or_down, this.list, this.src);
        }
    }

    private class CellChangeTracker {

        final List<CellChangeTrackerRecord> data = new LinkedList<>();

        public void postChanges(final StorageChannel channel, final int i,
                final IMEInventoryHandler<? extends IAEStack> h, final BaseActionSource actionSrc) {
            this.data.add(new CellChangeTrackerRecord(channel, i, h, actionSrc));
        }

        public void applyChanges() {
            for (final CellChangeTrackerRecord rec : this.data) {
                rec.applyChanges();
            }
        }
    }

    private void updateBytesInfo() {
        this.resetCellInfo();
        try {
            for (ICellProvider icp : this.activeCellProviders) {
                if (icp instanceof TileDrive) {
                    // Item cells
                    for (IMEInventoryHandler<?> meih : icp.getCellArray(StorageChannel.ITEMS)) {
                        // exclude void cell
                        if (((DriveWatcher<IAEItemStack>) meih).getInternal() instanceof ICellCacheRegistry iccr) {
                            // exclude creative cell
                            if (iccr.canGetInv()) {
                                itemBytesTotal += iccr.getTotalBytes();
                                itemBytesUsed += iccr.getUsedBytes();
                                switch (iccr.getCellStatus()) {
                                    case CELL_GREEN -> itemCellG++;
                                    case CELL_ORANGE -> itemCellO++;
                                    case CELL_RED -> itemCellR++;
                                }
                                itemTypesTotal += iccr.getTotalTypes();
                                itemTypesUsed += iccr.getUsedTypes();
                                itemCellCount++;
                            }
                        }
                    }
                    // Essentia and Fluid cells
                    for (IMEInventoryHandler<?> meih : icp.getCellArray(StorageChannel.FLUIDS)) {
                        // exclude void cell
                        if (((DriveWatcher<IAEItemStack>) meih).getInternal() instanceof ICellCacheRegistry iccr) {
                            // exclude creative cell
                            if (iccr.canGetInv()) {
                                if (iccr.getCellType() == ICellCacheRegistry.TYPE.FLUID) {
                                    fluidBytesTotal += iccr.getTotalBytes();
                                    fluidBytesUsed += iccr.getUsedBytes();
                                    switch (iccr.getCellStatus()) {
                                        case CELL_GREEN -> fluidCellG++;
                                        case CELL_ORANGE -> fluidCellO++;
                                        case CELL_RED -> fluidCellR++;
                                    }
                                    fluidTypesTotal += iccr.getTotalTypes();
                                    fluidTypesUsed += iccr.getUsedTypes();
                                    fluidCellCount++;
                                } else if (iccr.getCellType() == ICellCacheRegistry.TYPE.ESSENTIA) {
                                    essentiaBytesTotal += iccr.getTotalBytes();
                                    essentiaBytesUsed += iccr.getUsedBytes();
                                    switch (iccr.getCellStatus()) {
                                        case CELL_GREEN -> essentiaCellG++;
                                        case CELL_ORANGE -> essentiaCellO++;
                                        case CELL_RED -> essentiaCellR++;
                                    }
                                    essentiaTypesTotal += iccr.getTotalTypes();
                                    essentiaTypesUsed += iccr.getUsedTypes();
                                    essentiaCellCount++;
                                }
                            }
                        }

                    }
                } else if (icp instanceof TileChest tc) {
                    // Check if chest is clear
                    if (tc.getStackInSlot(1) == null) continue;
                    IMEInventoryHandler handler = tc.getInternalHandler(StorageChannel.ITEMS);
                    if (handler != null) {
                        if (handler instanceof ICellCacheRegistry iccr) {
                            // exclude creative cell
                            if (iccr.canGetInv()) {
                                itemBytesTotal += iccr.getTotalBytes();
                                itemBytesUsed += iccr.getUsedBytes();
                                switch (iccr.getCellStatus()) {
                                    case CELL_GREEN -> itemCellG++;
                                    case CELL_ORANGE -> itemCellO++;
                                    case CELL_RED -> itemCellR++;
                                }
                                itemTypesTotal += iccr.getTotalTypes();
                                itemTypesUsed += iccr.getUsedTypes();
                                itemCellCount++;
                            }
                        }
                    } else {
                        handler = tc.getInternalHandler(StorageChannel.FLUIDS);
                        // exclude void cell
                        if (handler instanceof ICellCacheRegistry iccr) {
                            // exclude creative cell
                            if (iccr.canGetInv()) {
                                if (iccr.getCellType() == ICellCacheRegistry.TYPE.FLUID) {
                                    fluidBytesTotal += iccr.getTotalBytes();
                                    fluidBytesUsed += iccr.getUsedBytes();
                                    switch (iccr.getCellStatus()) {
                                        case CELL_GREEN -> fluidCellG++;
                                        case CELL_ORANGE -> fluidCellO++;
                                        case CELL_RED -> fluidCellR++;
                                    }
                                    fluidTypesTotal += iccr.getTotalTypes();
                                    fluidTypesUsed += iccr.getUsedTypes();
                                    fluidCellCount++;
                                } else if (iccr.getCellType() == ICellCacheRegistry.TYPE.ESSENTIA) {
                                    essentiaBytesTotal += iccr.getTotalBytes();
                                    essentiaBytesUsed += iccr.getUsedBytes();
                                    switch (iccr.getCellStatus()) {
                                        case CELL_GREEN -> essentiaCellG++;
                                        case CELL_ORANGE -> essentiaCellO++;
                                        case CELL_RED -> essentiaCellR++;
                                    }
                                    essentiaTypesTotal += iccr.getTotalTypes();
                                    essentiaTypesUsed += iccr.getUsedTypes();
                                    essentiaCellCount++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // XD Normally won't be here, just normally..
        }
    }

    private void resetCellInfo() {
        this.itemBytesTotal = 0;
        this.itemBytesUsed = 0;
        this.itemTypesTotal = 0;
        this.itemTypesUsed = 0;
        this.itemCellG = 0;
        this.itemCellO = 0;
        this.itemCellR = 0;
        this.itemCellCount = 0;
        this.fluidBytesTotal = 0;
        this.fluidBytesUsed = 0;
        this.fluidTypesTotal = 0;
        this.fluidTypesUsed = 0;
        this.fluidCellG = 0;
        this.fluidCellO = 0;
        this.fluidCellR = 0;
        this.fluidCellCount = 0;
        this.essentiaBytesTotal = 0;
        this.essentiaBytesUsed = 0;
        this.essentiaTypesTotal = 0;
        this.essentiaTypesUsed = 0;
        this.essentiaCellG = 0;
        this.essentiaCellO = 0;
        this.essentiaCellR = 0;
        this.essentiaCellCount = 0;
    }

    public double getItemBytesTotal() {
        return itemBytesTotal;
    }

    public double getItemBytesUsed() {
        return itemBytesUsed;
    }

    public long getItemTypesTotal() {
        return itemTypesTotal;
    }

    public long getItemTypesUsed() {
        return itemTypesUsed;
    }

    public long getItemCellG() {
        return itemCellG;
    }

    public long getItemCellO() {
        return itemCellO;
    }

    public long getItemCellR() {
        return itemCellR;
    }

    public long getItemCellCount() {
        return itemCellCount;
    }

    public double getFluidBytesTotal() {
        return fluidBytesTotal;
    }

    public double getFluidBytesUsed() {
        return fluidBytesUsed;
    }

    public long getFluidTypesTotal() {
        return fluidTypesTotal;
    }

    public long getFluidTypesUsed() {
        return fluidTypesUsed;
    }

    public long getFluidCellG() {
        return fluidCellG;
    }

    public long getFluidCellO() {
        return fluidCellO;
    }

    public long getFluidCellR() {
        return fluidCellR;
    }

    public long getFluidCellCount() {
        return fluidCellCount;
    }

    public double getEssentiaBytesTotal() {
        return essentiaBytesTotal;
    }

    public double getEssentiaBytesUsed() {
        return essentiaBytesUsed;
    }

    public long getEssentiaTypesTotal() {
        return essentiaTypesTotal;
    }

    public long getEssentiaTypesUsed() {
        return essentiaTypesUsed;
    }

    public long getEssentiaCellG() {
        return essentiaCellG;
    }

    public long getEssentiaCellO() {
        return essentiaCellO;
    }

    public long getEssentiaCellR() {
        return essentiaCellR;
    }

    public long getEssentiaCellCount() {
        return essentiaCellCount;
    }
}
