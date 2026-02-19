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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.DimensionManager;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AELog;

public class ReshuffleLogger {

    public static final boolean DEBUG_LOGGING_ENABLED = true;

    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private PrintWriter writer;
    private final String playerName;
    private final long startTime;
    private final File logFile;
    private boolean closed = false;

    public ReshuffleLogger(String playerName) {
        this.playerName = playerName;
        this.startTime = System.currentTimeMillis();
        this.logFile = createLogFile();

        if (DEBUG_LOGGING_ENABLED && logFile != null) {
            try {
                this.writer = new PrintWriter(new FileWriter(logFile, true), true);
                writeHeader();
            } catch (IOException e) {
                AELog.warn(e, "Failed to create reshuffle log file");
                this.writer = null;
            }
        }
    }

    private File createLogFile() {
        if (!DEBUG_LOGGING_ENABLED) return null;

        try {
            File gameDir = null;

            // Try server-side first
            File worldDir = DimensionManager.getCurrentSaveRootDirectory();
            if (worldDir != null) {
                gameDir = worldDir.getParentFile();
            }

            if (gameDir == null) {
                try {
                    gameDir = Minecraft.getMinecraft().mcDataDir;
                } catch (Exception e) {
                    gameDir = new File(".");
                }
            }

            File logsDir = new File(gameDir, "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            String timestamp = FILE_DATE_FORMAT.format(new Date(startTime));
            String filename = String
                    .format("reshuffle_%s_%s.log", playerName.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);

            return new File(logsDir, filename);
        } catch (Exception e) {
            AELog.warn(e, "Failed to determine log file location");
            return null;
        }
    }

    private void writeHeader() {
        if (writer == null) return;

        writer.println("================================================================================");
        writer.println("AE2 RESHUFFLE OPERATION LOG");
        writer.println("================================================================================");
        writer.println("Started: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startTime)));
        writer.println("Player: " + playerName);
        writer.println("Log File: " + (logFile != null ? logFile.getAbsolutePath() : "N/A"));
        writer.println("================================================================================");
        writer.println();
    }

    public void logConfig(Set<IAEStackType<?>> allowedTypes, boolean voidProtection, boolean overwriteProtection) {
        if (writer == null) return;

        log("CONFIGURATION:");
        StringBuilder typesStr = new StringBuilder();
        for (IAEStackType<?> type : allowedTypes) {
            if (typesStr.length() > 0) typesStr.append(", ");
            // Get simple class name for display
            String typeName = type.getClass().getSimpleName().replace("AE", "").replace("StackType", "");
            typesStr.append(typeName);
        }
        log("  Allowed Types: " + (typesStr.length() > 0 ? typesStr.toString() : "None"));
        log("  Void Protection: " + (voidProtection ? "ENABLED" : "DISABLED"));
        log("  Overwrite Protection: " + (overwriteProtection ? "ENABLED" : "DISABLED"));
        log("");
    }

    public void logInitialization(int totalItems) {
        if (writer == null) return;

        log("INITIALIZATION:");
        log("  Total item types to process: " + NUMBER_FORMAT.format(totalItems));
        log("");
        log("================================================================================");
        log("PROCESSING LOG:");
        log("================================================================================");
    }

    public void logItemProcessStart(int index, IAEStack<?> stack) {
        if (writer == null) return;

        log(
                String.format(
                        "[%d/%d] Processing: %s (count: %s)",
                        index + 1,
                        -1, // Will be filled by caller
                        getStackDisplayName(stack),
                        NUMBER_FORMAT.format(stack.getStackSize())));
    }

    public void logItemExtracted(IAEStack<?> stack, long extractedAmount) {
        if (writer == null) return;

        log(String.format("  -> Extracted: %s", NUMBER_FORMAT.format(extractedAmount)));
    }

    public void logItemInjected(IAEStack<?> stack, long injectedAmount, long leftover) {
        if (writer == null) return;

        if (leftover > 0) {
            log(
                    String.format(
                            "  -> Injected: %s (leftover: %s - re-injected)",
                            NUMBER_FORMAT.format(injectedAmount - leftover),
                            NUMBER_FORMAT.format(leftover)));
        } else {
            log(String.format("  -> Injected: %s (complete)", NUMBER_FORMAT.format(injectedAmount)));
        }
    }

    public void logItemSkipped(IAEStack<?> stack, String reason) {
        if (writer == null) return;

        log(String.format("  -> SKIPPED: %s - Reason: %s", getStackDisplayName(stack), reason));
    }

    public void logItemError(IAEStack<?> stack, Exception e) {
        if (writer == null) return;

        log(String.format("  -> ERROR processing %s: %s", getStackDisplayName(stack), e.getMessage()));
    }

    public void logBatchComplete(int batchNumber, int processedSoFar, int totalItems, int skipped) {
        if (writer == null) return;

        int percent = totalItems > 0 ? (processedSoFar * 100) / totalItems : 100;
        log(
                String.format(
                        "--- Batch %d complete: %d/%d (%d%%) processed, %d skipped ---",
                        batchNumber,
                        processedSoFar,
                        totalItems,
                        percent,
                        skipped));
    }

    public void logProgress(int processed, int total, int percent) {
        if (writer == null) return;

        log(String.format("=== PROGRESS: %d/%d (%d%%) ===", processed, total, percent));
    }

    public void logCancelled(int processedItems, int totalItems) {
        if (writer == null) return;

        log("");
        log("================================================================================");
        log("OPERATION CANCELLED");
        log("================================================================================");
        log(
                "  Processed before cancellation: " + NUMBER_FORMAT.format(processedItems)
                        + "/"
                        + NUMBER_FORMAT.format(totalItems));
        log("  Time elapsed: " + formatDuration(System.currentTimeMillis() - startTime));
    }

    public void logCompletion(int processedItems, int skippedItems, int totalItems) {
        if (writer == null) return;

        log("");
        log("================================================================================");
        log("OPERATION COMPLETED");
        log("================================================================================");
        log("  Total processed: " + NUMBER_FORMAT.format(processedItems));
        log("  Total skipped: " + NUMBER_FORMAT.format(skippedItems));
        log("  Duration: " + formatDuration(System.currentTimeMillis() - startTime));
    }

    public void logReportSummary(long beforeTotal, long afterTotal, int itemTypesChanged, int itemTypesLost,
            int itemTypesGained) {
        if (writer == null) return;

        log("");
        log("STORAGE SUMMARY:");
        log("  Items before: " + NUMBER_FORMAT.format(beforeTotal));
        log("  Items after: " + NUMBER_FORMAT.format(afterTotal));
        log("  Net change: " + NUMBER_FORMAT.format(afterTotal - beforeTotal));
        log("  Types changed: " + NUMBER_FORMAT.format(itemTypesChanged));
        log("  Types lost count: " + NUMBER_FORMAT.format(itemTypesLost));
        log("  Types gained count: " + NUMBER_FORMAT.format(itemTypesGained));
    }

    public void logBeforeSnapshot(int itemTypes, long totalStacks) {
        if (writer == null) return;

        log("");
        log("BEFORE SNAPSHOT:");
        log("  Unique item types: " + NUMBER_FORMAT.format(itemTypes));
        log("  Total item count: " + NUMBER_FORMAT.format(totalStacks));
    }

    public void logAfterSnapshot(int itemTypes, long totalStacks) {
        if (writer == null) return;

        log("");
        log("AFTER SNAPSHOT:");
        log("  Unique item types: " + NUMBER_FORMAT.format(itemTypes));
        log("  Total item count: " + NUMBER_FORMAT.format(totalStacks));
    }

    public void logRaw(String message) {
        if (writer == null) return;
        log(message);
    }

    private void log(String message) {
        if (writer == null || closed) return;

        String timestamp = LOG_DATE_FORMAT.format(new Date());
        writer.println("[" + timestamp + "] " + message);
    }

    private String getStackDisplayName(IAEStack<?> stack) {
        if (stack == null) return "null";
        try {
            String name = stack.getDisplayName();
            if (name != null && !name.isEmpty()) {
                return name + " x" + NUMBER_FORMAT.format(stack.getStackSize());
            }
            return "Unknown x" + NUMBER_FORMAT.format(stack.getStackSize());
        } catch (Exception e) {
            return "Error getting name";
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long ms = millis % 1000;

        if (minutes > 0) {
            return String.format("%dm %ds %dms", minutes, seconds, ms);
        } else {
            return String.format("%ds %dms", seconds, ms);
        }
    }

    public void close() {
        if (writer != null && !closed) {
            log("");
            log("================================================================================");
            log("END OF LOG");
            log("================================================================================");
            writer.close();
            closed = true;

            if (logFile != null) {
                AELog.info("Reshuffle log saved to: " + logFile.getAbsolutePath());
            }
        }
    }

    public File getLogFile() {
        return logFile;
    }

    public boolean isEnabled() {
        return DEBUG_LOGGING_ENABLED && writer != null;
    }
}
