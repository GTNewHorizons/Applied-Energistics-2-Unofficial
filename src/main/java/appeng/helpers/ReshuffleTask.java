/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;

public class ReshuffleTask {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private final int batchSize;

    private final Map<IAEStackType<?>, IMEMonitor<?>> monitors;
    private final BaseActionSource actionSource;
    private final EntityPlayer player;
    private final Set<IAEStackType<?>> allowedTypes;
    private final boolean voidProtection;

    private final List<IAEStack<?>> itemsToProcess = new ArrayList<>();
    private final List<IAEStack<?>> skippedItemsList = new ArrayList<>();
    private int currentIndex = 0;
    private int totalItems = 0;
    private int processedItems = 0;
    private int skippedItems = 0;
    private boolean cancelled = false;
    private boolean completed = false;

    private ReshuffleReport report = null;

    public ReshuffleTask(Map<IAEStackType<?>, IMEMonitor<?>> monitors, BaseActionSource actionSource,
            EntityPlayer player, Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean generateReport) {
        this(monitors, actionSource, player, allowedTypes, voidProtection, generateReport, DEFAULT_BATCH_SIZE);
    }

    public ReshuffleTask(Map<IAEStackType<?>, IMEMonitor<?>> monitors, BaseActionSource actionSource,
            EntityPlayer player, Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean generateReport,
            int batchSize) {
        this.monitors = new IdentityHashMap<>(monitors);
        this.actionSource = actionSource;
        this.player = player;
        this.allowedTypes = new HashSet<>(allowedTypes);
        this.voidProtection = voidProtection;
        this.batchSize = batchSize;

        if (generateReport) {
            this.report = new ReshuffleReport();
            this.report.setAllowedTypes(allowedTypes);
            this.report.setVoidProtection(voidProtection);
        }
    }

    public int initialize() {
        itemsToProcess.clear();
        skippedItemsList.clear();
        currentIndex = 0;
        processedItems = 0;
        skippedItems = 0;

        if (report != null) {
            report.snapshotBefore(monitors, allowedTypes);
        }

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList storageList = monitor.getStorageList();
            for (Object obj : storageList) {
                IAEStack<?> stack = (IAEStack<?>) obj;
                if (stack != null && stack.getStackSize() > 0) {
                    itemsToProcess.add(stack.copy());
                }
            }
        }

        totalItems = itemsToProcess.size();
        return totalItems;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public boolean processNextBatch() {
        if (cancelled || completed) {
            return false;
        }

        int processed = 0;
        while (currentIndex < itemsToProcess.size() && processed < batchSize) {
            try {
                IAEStack<?> stack = itemsToProcess.get(currentIndex);
                IAEStackType type = stack.getStackType();
                IMEMonitor monitor = monitors.get(type);

                if (monitor != null && allowedTypes.contains(type)) {
                    boolean shouldProcess = true;

                    if (voidProtection) {
                        IAEStack<?> simExtracted = monitor
                                .extractItems(stack.copy(), Actionable.SIMULATE, actionSource);
                        if (simExtracted != null && simExtracted.getStackSize() > 0) {
                            IAEStack<?> simLeftover = monitor
                                    .injectItems(simExtracted.copy(), Actionable.SIMULATE, actionSource);
                            if (simLeftover != null && simLeftover.getStackSize() > 0) {
                                shouldProcess = false;
                                skippedItems++;
                                skippedItemsList.add(stack.copy());
                            }
                        }
                    }

                    if (shouldProcess) {
                        IAEStack<?> extracted = monitor.extractItems(stack.copy(), Actionable.MODULATE, actionSource);

                        if (extracted != null && extracted.getStackSize() > 0) {
                            IAEStack<?> leftover = monitor.injectItems(extracted, Actionable.MODULATE, actionSource);

                            if (leftover != null && leftover.getStackSize() > 0) {
                                monitor.injectItems(leftover, Actionable.MODULATE, actionSource);
                                skippedItems++;
                                skippedItemsList.add(stack.copy());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AELog.warn("Reshuffle: skipped %s", itemsToProcess.get(currentIndex));
                skippedItems++;
                skippedItemsList.add(itemsToProcess.get(currentIndex).copy());
            }

            currentIndex++;
            processedItems++;
            processed++;
        }

        if (currentIndex >= itemsToProcess.size()) {
            completed = true;
            finalizeReport();
            return false;
        }

        return true;
    }

    private void finalizeReport() {
        if (report != null && player instanceof EntityPlayerMP) {
            report.generateReport(monitors, allowedTypes, processedItems, skippedItems, skippedItemsList);
        }
    }

    public void cancel() {
        if (!completed && !cancelled) {
            cancelled = true;
        }
    }

    public boolean isRunning() {
        return !completed && !cancelled && totalItems > 0;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getProgressPercent() {
        return totalItems > 0 ? (processedItems * 100) / totalItems : 0;
    }

    public ReshuffleReport getReport() {
        return report;
    }
}
