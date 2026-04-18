package appeng.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.ReshufflePhase;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.NetworkMonitor;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.util.AEStackTypeFilter;
import appeng.util.IterationCounter;
import appeng.util.item.IAEStackList;

public class ReshuffleTask {

    // slowdown because working too fast, nobody gonna believe that real working
    public static final long stacks_per_tick = 8;

    private final BaseActionSource src;
    private final IStorageGrid sg;

    private final AEStackTypeFilter typeFilters;
    private final boolean includeSubnets;
    private final boolean insertOrder;

    private ReshufflePhase phase = ReshufflePhase.IDLE;

    private int extractedTypes, injectedTypes = 0;
    private long startTime, endTime;
    private double extractedItems, injectedItems = 0;

    private final IItemList<IAEStack<?>> extracted = new IAEStackList();
    private final IItemList<IAEStack<?>> cantExtract = new IAEStackList();
    private final IItemList<IAEStack<?>> cantInject;

    private final IItemList<IAEStack<?>> beforeSnapshot = new IAEStackList();
    private final IItemList<IAEStack<?>> afterSnapshot = new IAEStackList();
    private final IItemList<IAEStack<?>> stackLookup = new IAEStackList();

    private final List<IAEStack<?>> injectQueue = new ArrayList<>();

    private Iterator<Iterator<IMEInventoryHandler>> netTypesIterator = null;
    private Iterator<IMEInventoryHandler> typeInvHandlersIterator = null;
    private deepDig deepInspector = null;

    private Iterator<IAEStack<?>> injectIterator = null;

    private static class deepDig {

        private final Iterator<IMEInventoryHandler> firstLayer;
        private deepDig nextLayer = null;

        deepDig(Iterator<IMEInventoryHandler> firstLayer) {
            this.firstLayer = firstLayer;
        }

        public boolean hasNextLayer() {
            if (this.nextLayer == null) return false;
            if (this.nextLayer.nextLayer == null && !this.nextLayer.firstLayer.hasNext()) {
                this.nextLayer = null;
            }
            return this.nextLayer != null;
        }
    }

    public ReshuffleTask(AEStackTypeFilter typeFilters, IStorageGrid sg, IItemList<IAEStack<?>> cantInject,
            BaseActionSource src, boolean includeSubnets, boolean insertOrder) {
        this.src = src;
        this.sg = sg;
        this.cantInject = cantInject;
        this.typeFilters = typeFilters;
        this.includeSubnets = includeSubnets;
        this.insertOrder = insertOrder;
    }

    public void initialize() {
        this.startTime = System.currentTimeMillis();
        this.setupIterator();
        this.phase = ReshufflePhase.BEFORE_SNAPSHOT;
    }

    private boolean snapshotBefore() {
        return this.handlerProcessor(this.beforeSnapshot, false);
    }

    private boolean snapshotAfter() {
        return this.handlerProcessor(this.afterSnapshot, false);
    }

    private void setupIterator() {
        final List<Iterator<IMEInventoryHandler>> temp = new ArrayList<>();
        if (this.netTypesIterator == null) {
            for (IAEStackType<?> type : this.typeFilters.getEnabledTypes()) {
                if (!(this.sg.getMEMonitor(type) instanceof NetworkMonitor<?>nm)) continue;
                if (!(nm.getHandler() instanceof NetworkInventoryHandler nih)) continue;
                if (this.netTypesIterator == null) temp.add(nih.getHandlers().iterator());
            }

            this.netTypesIterator = temp.iterator();
        } else {
            this.resetIterators();
            this.setupIterator();
        }
    }

    private void resetIterators() {
        this.netTypesIterator = null;
        this.typeInvHandlersIterator = null;
        this.deepInspector = null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean handlerProcessor(final IItemList<IAEStack<?>> target, final boolean extract) {
        if (this.typeInvHandlersIterator == null) {
            if (this.netTypesIterator.hasNext()) {
                this.typeInvHandlersIterator = this.netTypesIterator.next();
            } else return true;
        }

        final Iterator<IMEInventoryHandler> i;
        deepDig currentDeep = this.deepInspector;

        if (currentDeep != null) {
            while (currentDeep.hasNextLayer()) currentDeep = currentDeep.nextLayer;
            if (currentDeep.firstLayer.hasNext()) i = currentDeep.firstLayer;
            else i = this.typeInvHandlersIterator;
        } else {
            i = this.typeInvHandlersIterator;
        }

        while (i.hasNext()) {
            final IMEInventoryHandler cellHandler = i.next();
            if (cellHandler == null || cellHandler.isAutoCraftingInventory()) continue;
            final IAEStackType<?> currentType = cellHandler.getStackType();
            final IMENetworkInventory<?> subnet = cellHandler.getExternalNetworkInventory();
            if (subnet instanceof NetworkInventoryHandler nextNetwork) {
                if (!includeSubnets) continue;
                if (currentDeep == null) this.deepInspector = new deepDig(nextNetwork.getHandlers().iterator());
                else currentDeep.nextLayer = new deepDig(nextNetwork.getHandlers().iterator());

                return false;
            }

            cellHandler.getAvailableItems(currentType.createList(), IterationCounter.fetchNewId()).forEach(o -> {
                if (o instanceof IAEStack<?>aes) {
                    aes = aes.copy();
                    if (extract) {
                        final IAEStack<?> extracted = cellHandler.extractItems(aes, Actionable.MODULATE, this.src);

                        if (extracted == null) {
                            this.cantExtract.add(aes);
                        } else if (extracted.getStackSize() != aes.getStackSize()) {
                            aes.decStackSize(extracted.getStackSize());
                            this.cantExtract.add(aes);
                        } else {
                            this.extractedItems += extracted.getStackSize();
                            target.add(extracted);
                        }

                        this.extractedTypes = this.extracted.size();
                    } else {
                        this.stackLookup.add(aes);
                        target.add(aes);
                    }
                }
            });

            return false;
        }

        this.typeInvHandlersIterator = null;

        return false;
    }

    private void toQueueList() {
        final Iterator<IAEStack<?>> i = this.extracted.iterator();
        while (i.hasNext()) {
            final IAEStack<?> item = i.next();
            this.injectQueue.add(item);
            i.remove();
        }
        this.injectQueue
                .sort(Comparator.comparingLong(aes -> this.insertOrder ? -aes.getStackSize() : aes.getStackSize()));
    }

    public void processNextBatch() {
        switch (this.phase) {
            case BEFORE_SNAPSHOT -> {
                if (this.snapshotBefore()) {
                    this.setupIterator();
                    this.phase = ReshufflePhase.EXTRACTION;
                }
            }

            case EXTRACTION -> {
                if (this.handlerProcessor(this.extracted, true)) {
                    this.setupIterator();
                    this.toQueueList();
                    this.phase = ReshufflePhase.INJECTION;
                }
            }

            case INJECTION -> {
                int operations = 0;
                if (this.injectIterator == null) this.injectIterator = this.injectQueue.iterator();

                while (this.injectIterator.hasNext()) {
                    final IAEStack<?> aes = this.injectIterator.next();
                    final IMEMonitor monitor = this.sg.getMEMonitor(aes.getStackType());

                    if (monitor == null) {
                        this.cantInject.add(aes);
                        this.injectIterator.remove();
                    }

                    final IAEStack<?> res = monitor.injectItems(aes, Actionable.MODULATE, this.src);

                    if (res != null) {
                        this.cantInject.add(res);
                        this.injectedItems -= res.getStackSize();
                        if (res.getStackSize() == aes.getStackSize()) this.injectedTypes -= 1;
                    }

                    this.injectedTypes += 1;
                    this.injectedItems += aes.getStackSize();
                    this.injectIterator.remove();

                    operations++;

                    if (operations == stacks_per_tick) return;
                }

                this.injectIterator = null;
                this.phase = ReshufflePhase.AFTER_SNAPSHOT;
            }

            case AFTER_SNAPSHOT -> {
                if (this.snapshotAfter()) {
                    this.setupIterator();
                    this.finalizeReport();
                }
            }
        }
    }

    public void cancel() {
        this.phase = ReshufflePhase.CANCEL;
        this.returnPendingItems(this.extracted.iterator());
        this.returnPendingItems(this.injectQueue.iterator());
    }

    public void cancelNbt() {
        this.phase = ReshufflePhase.CANCEL;
        this.returnPendingItemsNbt(this.extracted.iterator());
        this.returnPendingItemsNbt(this.injectQueue.iterator());
    }

    public void error() {
        this.phase = ReshufflePhase.ERROR;
    }

    private void returnPendingItemsNbt(Iterator<IAEStack<?>> i) {
        while (i.hasNext()) {
            final IAEStack<?> aes = i.next();
            this.cantInject.add(aes);
            i.remove();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void returnPendingItems(Iterator<IAEStack<?>> i) {
        while (i.hasNext()) {
            final IAEStack<?> aes = i.next();
            final IMEMonitor monitor = this.sg.getMEMonitor(aes.getStackType());

            if (monitor != null) {
                final IAEStack<?> res = monitor.injectItems(aes, Actionable.MODULATE, this.src);
                if (res != null) this.cantInject.add(res);

                i.remove();
            }
        }
    }

    private void finalizeReport() {
        this.phase = ReshufflePhase.DONE;
        this.endTime = System.currentTimeMillis();
    }

    public ReshuffleReport getReport() {
        return new ReshuffleReport(
                this.typeFilters,
                this.includeSubnets,
                this.phase,
                this.extractedTypes,
                this.injectedTypes,
                this.startTime,
                this.endTime,
                this.extractedItems,
                this.injectedItems,
                this.cantExtract,
                this.cantInject,
                this.beforeSnapshot,
                this.afterSnapshot,
                this.stackLookup);
    }

    public boolean isRunning() {
        return this.phase == ReshufflePhase.EXTRACTION || this.phase == ReshufflePhase.INJECTION
                || this.phase == ReshufflePhase.AFTER_SNAPSHOT
                || this.phase == ReshufflePhase.BEFORE_SNAPSHOT;
    }
}
