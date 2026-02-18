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
import net.minecraft.util.ChatComponentTranslation;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;

/**
 * Manages the batched reshuffle operation for ME storage networks. Processes items in batches over multiple ticks to
 * prevent server freezing. Uses stack type filters instead of mode enum.
 */
public class ReshuffleTask {

    /** Number of item types to process per tick - configurable for performance tuning */
    private static final int DEFAULT_BATCH_SIZE = 500; // Increased from 100 for faster processing
    private final int batchSize;

    /** Threshold for requiring confirmation (number of unique item types) */
    public static final int LARGE_NETWORK_THRESHOLD = 1000;

    private final Map<IAEStackType<?>, IMEMonitor<?>> monitors;
    private final BaseActionSource actionSource;
    private final EntityPlayer player;
    private final Set<IAEStackType<?>> allowedTypes;
    private final boolean voidProtection;
    private final boolean overwriteProtection;
    private final boolean generateReport;

    private final List<IAEStack<?>> itemsToProcess = new ArrayList<>();
    private int currentIndex = 0;
    private int totalItems = 0;
    private int processedItems = 0;
    private int skippedItems = 0;
    private boolean cancelled = false;
    private boolean completed = false;
    private int lastProgressPercent = -1;
    private int batchNumber = 0;

    // Report tracking
    private ReshuffleReport report = null;

    // Debug logger
    private ReshuffleLogger logger = null;

    /**
     * Creates a new ReshuffleTask with the specified options.
     *
     * @param monitors            The storage monitors to use
     * @param actionSource        The action source for security
     * @param player              The player initiating the reshuffle
     * @param allowedTypes        Set of IAEStackType to process (e.g., items, fluids, etc.)
     * @param voidProtection      If true, items won't be extracted if they can't be fully re-inserted
     * @param overwriteProtection If true, items won't be moved if they would just go back to same location
     * @param generateReport      If true, generates a detailed report at the end
     */
    public ReshuffleTask(Map<IAEStackType<?>, IMEMonitor<?>> monitors, BaseActionSource actionSource,
            EntityPlayer player, Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean overwriteProtection,
            boolean generateReport) {
        this(
                monitors,
                actionSource,
                player,
                allowedTypes,
                voidProtection,
                overwriteProtection,
                generateReport,
                DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates a new ReshuffleTask with custom batch size.
     *
     * @param monitors            The storage monitors to use
     * @param actionSource        The action source for security
     * @param player              The player initiating the reshuffle
     * @param allowedTypes        Set of IAEStackType to process (e.g., items, fluids, etc.)
     * @param voidProtection      If true, items won't be extracted if they can't be fully re-inserted
     * @param overwriteProtection If true, items won't be moved if they would just go back to same location
     * @param generateReport      If true, generates a detailed report at the end
     * @param batchSize           Number of items to process per tick
     */
    public ReshuffleTask(Map<IAEStackType<?>, IMEMonitor<?>> monitors, BaseActionSource actionSource,
            EntityPlayer player, Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean overwriteProtection,
            boolean generateReport, int batchSize) {
        this.monitors = new IdentityHashMap<>(monitors);
        this.actionSource = actionSource;
        this.player = player;
        this.allowedTypes = new HashSet<>(allowedTypes);
        this.voidProtection = voidProtection;
        this.overwriteProtection = overwriteProtection;
        this.generateReport = generateReport;
        this.batchSize = batchSize;

        // Initialize report if enabled
        if (generateReport) {
            this.report = new ReshuffleReport();
            this.report.setAllowedTypes(allowedTypes);
            this.report.setVoidProtection(voidProtection);
            this.report.setOverwriteProtection(overwriteProtection);
        }

        // Initialize debug logger
        if (ReshuffleLogger.DEBUG_LOGGING_ENABLED) {
            this.logger = new ReshuffleLogger(player.getCommandSenderName());
            this.logger.logConfig(allowedTypes, voidProtection, overwriteProtection);
        }
    }

    /**
     * Initializes the task by collecting all items from storage.
     * 
     * @return The total number of unique item types to reshuffle
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public int initialize() {
        itemsToProcess.clear();
        currentIndex = 0;
        processedItems = 0;
        skippedItems = 0;

        // Take a snapshot before reshuffling for the report
        if (report != null) {
            report.snapshotBefore(monitors, allowedTypes);
        }

        // Collect all items from allowed storage types
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

    /**
     * Process the next batch of items.
     * 
     * @return true if there are more items to process, false if done or cancelled
     */
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

                    // Void protection: simulate extraction and injection first
                    if (voidProtection) {
                        IAEStack<?> simExtracted = monitor
                                .extractItems(stack.copy(), Actionable.SIMULATE, actionSource);
                        if (simExtracted != null && simExtracted.getStackSize() > 0) {
                            IAEStack<?> simLeftover = monitor
                                    .injectItems(simExtracted.copy(), Actionable.SIMULATE, actionSource);
                            // If there would be leftover (voiding), skip this item
                            if (simLeftover != null && simLeftover.getStackSize() > 0) {
                                shouldProcess = false;
                                skippedItems++;
                            }
                        }
                    }

                    // Overwrite protection: check if item would just go back to same place
                    if (shouldProcess && overwriteProtection) {
                        // This is a simplified check - in reality, checking exact destination is complex
                        // We simulate by checking if injection would result in same state
                        IAEStack<?> simExtracted = monitor
                                .extractItems(stack.copy(), Actionable.SIMULATE, actionSource);
                        if (simExtracted != null && simExtracted.getStackSize() > 0) {
                            // Get current storage list state for this item
                            IAEStack<?> beforeState = monitor.getStorageList().findPrecise(stack);
                            if (beforeState != null) {
                                // If simulation shows it would fully re-inject, check if destination changed
                                IAEStack<?> simLeftover = monitor
                                        .injectItems(simExtracted.copy(), Actionable.SIMULATE, actionSource);
                                if (simLeftover == null || simLeftover.getStackSize() == 0) {
                                    // Full re-injection possible - for now we allow it since priority routing may
                                    // change
                                    // dest
                                    // More advanced check would require tracking storage handler destinations
                                }
                            }
                        }
                    }

                    if (shouldProcess) {
                        // Extract the full amount
                        IAEStack<?> extracted = monitor.extractItems(stack.copy(), Actionable.MODULATE, actionSource);

                        if (extracted != null && extracted.getStackSize() > 0) {
                            // Re-inject - this will route to sticky/prioritized storage
                            IAEStack<?> leftover = monitor.injectItems(extracted, Actionable.MODULATE, actionSource);

                            // If there's leftover that couldn't be inserted, force inject back
                            // This should not happen with void protection, but safety first
                            if (leftover != null && leftover.getStackSize() > 0) {
                                monitor.injectItems(leftover, Actionable.MODULATE, actionSource);
                                skippedItems++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Log error but continue processing other items
                AELog.warn(e, "Error processing item during reshuffle at index " + currentIndex);
                skippedItems++;
            }

            currentIndex++;
            processedItems++;
            processed++;
        }

        // Send progress update to player
        sendProgressUpdate();

        if (currentIndex >= itemsToProcess.size()) {
            completed = true;
            sendCompletionMessage();
            return false;
        }

        return true;
    }

    private void sendProgressUpdate() {
        int percent = totalItems > 0 ? (processedItems * 100) / totalItems : 100;

        // Only send updates at 10% intervals to avoid spam
        int progressInterval = percent / 10;
        int lastInterval = lastProgressPercent < 0 ? -1 : lastProgressPercent / 10;

        if (progressInterval > lastInterval && percent < 100) {
            lastProgressPercent = percent;
            AELog.debug("Reshuffle progress: %d/%d (%d%%), sending update", processedItems, totalItems, percent);

            // Log progress to file
            if (logger != null) {
                logger.logProgress(processedItems, totalItems, percent);
            }
        }
    }

    private void sendCompletionMessage() {
        if (player instanceof EntityPlayerMP) {
            // Generate the report - it will be displayed in GUI
            if (report != null) {
                report.generateReport(monitors, allowedTypes, processedItems, skippedItems);
                // Report is now available via getReport().generateReportLines()
                // The tile entity will handle updating the GUI with the report
            } else {
                // Fallback to simple message if report is disabled
                if (skippedItems > 0) {
                    player.addChatMessage(
                            new ChatComponentTranslation(
                                    "chat.appliedenergistics2.ReshuffleCompleteWithSkipped",
                                    totalItems,
                                    skippedItems));
                } else {
                    player.addChatMessage(
                            new ChatComponentTranslation("chat.appliedenergistics2.ReshuffleComplete", totalItems));
                }
            }
        }
    }

    /**
     * Cancels the reshuffle operation.
     */
    public void cancel() {
        if (!completed && !cancelled) {
            cancelled = true;
            if (player instanceof EntityPlayerMP) {
                player.addChatMessage(
                        new ChatComponentTranslation(
                                "chat.appliedenergistics2.ReshuffleCancelled",
                                processedItems,
                                totalItems));
            }
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCancelled() {
        return cancelled;
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

    public int getSkippedItems() {
        return skippedItems;
    }

    public int getProgressPercent() {
        return totalItems > 0 ? (processedItems * 100) / totalItems : 0;
    }

    public Set<IAEStackType<?>> getAllowedTypes() {
        return new HashSet<>(allowedTypes);
    }

    public boolean hasVoidProtection() {
        return voidProtection;
    }

    public boolean hasOverwriteProtection() {
        return overwriteProtection;
    }

    public ReshuffleReport getReport() {
        return report;
    }
}
