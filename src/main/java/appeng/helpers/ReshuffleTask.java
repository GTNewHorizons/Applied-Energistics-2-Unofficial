package appeng.helpers;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.util.AEStackTypeFilter;

public class ReshuffleTask {

    private static final int DEFAULT_BATCH_SIZE = 100;
    public static final long TICK_BUDGET_NS = 8_000_000L;

    private final Map<IAEStackType<?>, IMEMonitor<?>> monitors;
    private final BaseActionSource actionSource;
    private final AEStackTypeFilter typeFilters;
    private final boolean voidProtection;
    private final boolean includeSubnets;

    private final List<IAEStack<?>> extractionQueue = new ArrayList<>();
    private final List<IAEStack<?>> injectionQueue = new ArrayList<>();
    private final List<IAEStack<?>> skippedItemsList = new ArrayList<>();

    private enum Phase {
        EXTRACT,
        INJECT,
        DONE
    }

    private Phase phase = Phase.EXTRACT;

    private int extractionIndex = 0;
    private int injectionIndex = 0;
    private int totalItems = 0;
    private int processedItems = 0;
    private int skippedItems = 0;
    private int extractedItems = 0;
    private int typeCount = 0;
    private boolean cancelled = false;
    private boolean completed = false;
    private boolean phaseJustChanged = false;
    private boolean voidProtectionTriggered = false;

    private ReshuffleReport report = null;

    public ReshuffleTask(Map<IAEStackType<?>, IMEMonitor<?>> monitors, BaseActionSource actionSource,
            AEStackTypeFilter filters, boolean voidProtection) {
            EntityPlayer player, Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean includeSubnets) {
        this.monitors = new IdentityHashMap<>(monitors);
        this.actionSource = actionSource;
        this.typeFilters = filters;
        this.voidProtection = voidProtection;
        this.includeSubnets = includeSubnets;
        this.report = new ReshuffleReport(this.allowedTypes, voidProtection, includeSubnets);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<IAEStack<?>> collectLocalStacks(IAEStackType<?> type, IMEMonitor<?> monitor) {
        List<IAEStack<?>> result = new ArrayList<>();

        if (!includeSubnets && monitor instanceof NetworkMonitor<?>nm
                && nm.getHandler() instanceof NetworkInventoryHandler<?>nih) {
            final IItemList consolidatedList = type.createList();
            for (IMEInventoryHandler<?> cellHandler : nih.getHandlers()) {
                if (cellHandler.getExternalNetworkInventory() != null) continue;
                cellHandler.getAvailableItems(consolidatedList, IterationCounter.fetchNewId());
            }
            for (Object obj : consolidatedList) {
                IAEStack<?> stack = (IAEStack<?>) obj;
                if (stack != null && stack.getStackSize() > 0) {
                    result.add(stack.copy());
                }
            }
            return result;
        }

        IItemList<?> storageList = monitor.getStorageList();
        for (Object obj : storageList) {
            IAEStack<?> stack = (IAEStack<?>) obj;
            if (stack != null && stack.getStackSize() > 0) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    public int initialize() {
        extractionQueue.clear();
        injectionQueue.clear();
        skippedItemsList.clear();
        extractionIndex = 0;
        injectionIndex = 0;
        processedItems = 0;
        skippedItems = 0;
        phase = Phase.EXTRACT;

        report.snapshotBefore(monitors, allowedTypes);

        for (Entry<IAEStackType<?>, IMEMonitor<?>> entry : this.monitors.entrySet()) {
            IMEMonitor<?> monitor = entry.getValue();
            if (monitor == null) continue;
            extractionQueue.addAll(collectLocalStacks(type, monitor));
        }

        extractionQueue.sort(Comparator.comparingLong(s -> -s.getStackSize()));

        totalItems = extractionQueue.size();
        typeCount = totalItems;
        return totalItems;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void processNextBatch() {
        if (cancelled || completed) return;
        phaseJustChanged = false;

        int processed = 0;

        if (phase == Phase.EXTRACT) {
            while (extractionIndex < extractionQueue.size() && processed < DEFAULT_BATCH_SIZE) {
                final IAEStack<?> snapshot = extractionQueue.get(extractionIndex);
                final IAEStackType type = snapshot.getStackType();
                final IMEMonitor monitor = monitors.get(type);

                if (monitor != null && allowedTypes.contains(type)) {
                    try {
                        final IAEStack<?> toExtract = snapshot.copy();
                        toExtract.setStackSize(Long.MAX_VALUE);
                        final IAEStack<?> extracted = monitor
                                .extractItems(toExtract, Actionable.MODULATE, actionSource);
                        if (extracted != null && extracted.getStackSize() > 0) {
                            injectionQueue.add(extracted);
                        }
                    } catch (Exception e) {
                        AELog.warn("Reshuffle [extract]: skipped %s – %s", snapshot, e.getMessage());
                        skippedItems++;
                        skippedItemsList.add(snapshot.copy());
                    }
                }

                extractionIndex++;
                processedItems++;
                processed++;
            }

            if (extractionIndex >= extractionQueue.size()) {
                sortInjectionQueue();
                extractedItems = processedItems;
                totalItems = processedItems + injectionQueue.size();
                phase = Phase.INJECT;
                phaseJustChanged = true;
            }
            return;
        }

        if (phase == Phase.INJECT) {
            while (injectionIndex < injectionQueue.size() && processed < DEFAULT_BATCH_SIZE) {
                final IAEStack<?> toInject = injectionQueue.get(injectionIndex);
                final IAEStackType type = toInject.getStackType();
                final IMEMonitor monitor = monitors.get(type);

                if (monitor != null) {
                    try {
                        final IAEStack<?> leftover = monitor.injectItems(toInject, Actionable.MODULATE, actionSource);
                        if (leftover != null && leftover.getStackSize() > 0) {
                            // Retry once in case of transient capacity changes
                            final IAEStack<?> retryLeftover = monitor
                                    .injectItems(leftover, Actionable.MODULATE, actionSource);
                            if (retryLeftover != null && retryLeftover.getStackSize() > 0) {
                                if (voidProtection) {
                                    // Void protection: cancel and return all remaining items
                                    AELog.warn(
                                            "Reshuffle [inject]: void protection triggered – %d of %s could not be reinjected",
                                            retryLeftover.getStackSize(),
                                            toInject);
                                    skippedItemsList.add(retryLeftover.copy());
                                    injectionIndex++;
                                    voidProtectionTriggered = true;
                                    cancelled = true;
                                    returnPendingItems();
                                    finalizeReport();
                                    return;
                                }
                                AELog.warn(
                                        "Reshuffle [inject]: lost %d of %s (could not reinject after retry)",
                                        retryLeftover.getStackSize(),
                                        toInject);
                                skippedItems++;
                                skippedItemsList.add(retryLeftover.copy());
                            }
                        }
                    } catch (Exception e) {
                        AELog.warn("Reshuffle [inject]: skipped %s – %s", toInject, e.getMessage());
                        if (voidProtection) {
                            // Void protection: try to inject, then cancel
                            try {
                                monitor.injectItems(toInject, Actionable.MODULATE, actionSource);
                            } catch (Exception ignored) {}
                            injectionIndex++;
                            voidProtectionTriggered = true;
                            cancelled = true;
                            returnPendingItems();
                            finalizeReport();
                            return;
                        }
                        try {
                            final IAEStack<?> leftover = monitor
                                    .injectItems(toInject, Actionable.MODULATE, actionSource);
                            if (leftover != null && leftover.getStackSize() > 0) {
                                skippedItems++;
                                skippedItemsList.add(leftover.copy());
                            }
                        } catch (Exception ignored) {
                            skippedItems++;
                            skippedItemsList.add(toInject.copy());
                        }
                    }
                }

                injectionIndex++;
                processedItems++;
                processed++;
            }

            if (injectionIndex >= injectionQueue.size()) {
                completed = true;
                phase = Phase.DONE;
                finalizeReport();
            }
        }
    }

    public void cancel() {
        if (!completed && !cancelled) {
            cancelled = true;
            returnPendingItems();
        }
    }

    private void sortInjectionQueue() {
        injectionQueue.sort((a, b) -> {
            final boolean aPrio = hasPrioritizedHome(a);
            final boolean bPrio = hasPrioritizedHome(b);
            if (aPrio != bPrio) return aPrio ? -1 : 1;
            return Long.compare(b.getStackSize(), a.getStackSize());
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean hasPrioritizedHome(IAEStack<?> stack) {
        final IAEStackType type = stack.getStackType();
        final IMEMonitor monitor = monitors.get(type);
        if (!(monitor instanceof NetworkMonitor<?>nm)) return false;
        if (!(nm.getHandler() instanceof NetworkInventoryHandler<?>nih)) return false;
        final IAEStack probe = stack.copy();
        probe.setStackSize(1);
        for (IMEInventoryHandler handler : nih.getHandlers()) {
            if (!includeSubnets && handler.getExternalNetworkInventory() != null) continue;
            if (handler.isPrioritized(probe)) return true;
        }
        return false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void returnPendingItems() {
        final List<IAEStack<?>> toReturn = phase == Phase.INJECT
                ? injectionQueue.subList(injectionIndex, injectionQueue.size())
                : new ArrayList<>(injectionQueue);

        for (IAEStack<?> stack : toReturn) {
            final IAEStackType type = stack.getStackType();
            final IMEMonitor monitor = monitors.get(type);
            if (monitor != null) {
                try {
                    monitor.injectItems(stack, Actionable.MODULATE, actionSource);
                } catch (Exception e) {
                    AELog.error(e, "Reshuffle [cancel]: failed to return " + stack);
                }
            }
        }
    }

    private void finalizeReport() {
        if (player instanceof EntityPlayerMP) {
            final int injectedItems = processedItems - extractedItems;
            final Set<String> stickyPrioritizedKeys = collectStickyPrioritizedKeys();
            report.generateReport(
                    monitors,
                    allowedTypes,
                    extractedItems,
                    injectedItems,
                    skippedItems,
                    skippedItemsList,
                    report.buildKeySet(extractionQueue),
                    stickyPrioritizedKeys);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Set<String> collectStickyPrioritizedKeys() {
        final Set<String> keys = new HashSet<>();
        for (IAEStackType<?> type : monitors.keySet()) {
            IMEMonitor<?> monitor = monitors.get(type);
            if (!(monitor instanceof NetworkMonitor<?>nm)) continue;
            if (!(nm.getHandler() instanceof NetworkInventoryHandler<?>nih)) continue;

            final List<IMEInventoryHandler> singularityHandlers = new ArrayList<>();
            for (IMEInventoryHandler handler : nih.getHandlers()) {
                if (!includeSubnets && handler.getExternalNetworkInventory() != null) continue;
                if (handler instanceof ICellInventoryHandler<?>cellHandler) {
                    try {
                        if (cellHandler.getCellInv() != null && cellHandler.getCellInv().getTotalItemTypes() == 1) {
                            singularityHandlers.add(handler);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (singularityHandlers.isEmpty()) continue;

            final IItemList<?> storageList = monitor.getStorageList();
            for (Object obj : storageList) {
                if (!(obj instanceof IAEStack<?>stack) || stack.getStackSize() <= 0) continue;
                final IAEStack probe = stack.copy();
                probe.setStackSize(1);
                for (IMEInventoryHandler handler : singularityHandlers) {
                    if (handler.isPrioritized(probe)) {
                        keys.add(report.getStackKey(stack));
                        break;
                    }
                }
            }
        }
        return keys;
    }

    public int getProgressPercent() {
        final int extTotal = extractionQueue.size();
        if (extTotal == 0) return 0;
        if (phase == Phase.EXTRACT) return (extractionIndex * 50) / extTotal;
        if (phase == Phase.DONE) return 100;
        final int injTotal = injectionQueue.size();
        if (injTotal == 0) return 100;
        return 50 + (injectionIndex * 50) / injTotal;
    }

    public boolean isRunning() {
        return !completed && !cancelled && totalItems > 0;
    }

    public boolean isVoidProtectionTriggered() {
        return voidProtectionTriggered;
    }

    public boolean hasPhaseJustChanged() {
        return phaseJustChanged;
    }

    public boolean isExtracting() {
        return phase == Phase.EXTRACT;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getPhaseTotal() {
        return phase == Phase.EXTRACT ? extractionQueue.size() : phase == Phase.INJECT ? injectionQueue.size() : 0;
    }

    public int getPhaseProcessed() {
        return phase == Phase.EXTRACT ? extractionIndex : phase == Phase.INJECT ? injectionIndex : 0;
    }

    public int getTypeCount() {
        return typeCount;
    }

    public ReshuffleReport getReport() {
        return this.report;
    }
}
