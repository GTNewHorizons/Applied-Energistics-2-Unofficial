package appeng.me.cluster.implementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.server.MinecraftServer;

import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;

final class CraftingCpuDiagnostics {

    private final Map<IAEStack<?>, NavigableSet<CraftingTimingRecord>> outputTimingRecords = new HashMap<>();

    void clear() {
        this.outputTimingRecords.clear();
    }

    void recordExpectedOutput(final IAEStack<?> output, final long outputObservedAtTick,
            final CraftingDiagnosticSessionId diagnosticSessionId) {
        if (output == null || output.getStackSize() <= 0 || diagnosticSessionId == null) {
            return;
        }

        final IAEStack<?> key = normalizeTrackingStack(output);
        if (key == null) {
            return;
        }

        final NavigableSet<CraftingTimingRecord> records = this.outputTimingRecords
                .computeIfAbsent(key, ignored -> new TreeSet<>());
        final CraftingTimingRecord probe = new CraftingTimingRecord(
                output.getStackSize(),
                outputObservedAtTick,
                diagnosticSessionId);
        final CraftingTimingRecord existing = records.ceiling(probe);
        if (existing != null && existing.compareTo(probe) == 0) {
            existing.addProduced(output.getStackSize());
        } else {
            records.add(probe);
        }
    }

    List<CompletedDiagnosticRecord> recordReturnedOutputs(final IAEStack<?> returnedStack) {
        if (returnedStack == null || returnedStack.getStackSize() <= 0) {
            return Collections.emptyList();
        }

        final IAEStack<?> key = normalizeTrackingStack(returnedStack);
        if (key == null) {
            return Collections.emptyList();
        }

        final NavigableSet<CraftingTimingRecord> records = this.outputTimingRecords.get(key);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        long remainingReturned = returnedStack.getStackSize();
        final long endTick = getServerTick();
        final List<CompletedDiagnosticRecord> completedRecords = new ArrayList<>();
        while (remainingReturned > 0 && !records.isEmpty()) {
            final CraftingTimingRecord record = records.first();
            final long consumed = Math.min(remainingReturned, record.getRemainingToProduce());
            record.addRemainingToProduce(-consumed);
            remainingReturned -= consumed;

            if (record.getRemainingToProduce() <= 0) {
                record.setEndTick(endTick);
                completedRecords.add(
                        new CompletedDiagnosticRecord(
                                returnedStack,
                                record.getDiagnosticSessionId(),
                                record.getOriginalToProduce(),
                                record.getStartTick(),
                                record.getEndTick(),
                                record.getElapsedTicks()));
                records.pollFirst();
            }
        }

        if (records.isEmpty()) {
            this.outputTimingRecords.remove(key);
        }

        return completedRecords;
    }

    boolean hasPendingRecordsForSession(final CraftingDiagnosticSessionId diagnosticSessionId) {
        if (diagnosticSessionId == null) {
            return false;
        }

        for (final NavigableSet<CraftingTimingRecord> records : this.outputTimingRecords.values()) {
            for (final CraftingTimingRecord record : records) {
                if (diagnosticSessionId.equals(record.getDiagnosticSessionId())
                        && (record.getRemainingToProduce() > 0 || record.getEndTick() == 0L)) {
                    return true;
                }
            }
        }

        return false;
    }

    Set<CraftingDiagnosticSessionId> getPendingSessionIds() {
        final Set<CraftingDiagnosticSessionId> sessionIds = new TreeSet<>();
        for (final NavigableSet<CraftingTimingRecord> records : this.outputTimingRecords.values()) {
            for (final CraftingTimingRecord record : records) {
                if (record.getDiagnosticSessionId() != null) {
                    sessionIds.add(record.getDiagnosticSessionId());
                }
            }
        }
        return sessionIds;
    }

    private static IAEStack<?> normalizeTrackingStack(final IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }

        final IAEStack<?> normalized = stack.copy();
        normalized.reset();
        normalized.setStackSize(1);
        return normalized;
    }

    private static long getServerTick() {
        final MinecraftServer server = MinecraftServer.getServer();
        return server == null ? 0L : server.getTickCounter();
    }

    static final class CompletedDiagnosticRecord {

        private final IAEStack<?> output;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private final long producedAmount;
        private final long startTick;
        private final long endTick;
        private final long elapsedTicks;

        private CompletedDiagnosticRecord(final IAEStack<?> output,
                final CraftingDiagnosticSessionId diagnosticSessionId, final long producedAmount, final long startTick,
                final long endTick, final long elapsedTicks) {
            this.output = output;
            this.diagnosticSessionId = diagnosticSessionId;
            this.producedAmount = producedAmount;
            this.startTick = startTick;
            this.endTick = endTick;
            this.elapsedTicks = elapsedTicks;
        }

        IAEStack<?> getOutput() {
            return this.output;
        }

        CraftingDiagnosticSessionId getDiagnosticSessionId() {
            return this.diagnosticSessionId;
        }

        long getProducedAmount() {
            return this.producedAmount;
        }

        long getStartTick() {
            return this.startTick;
        }

        long getEndTick() {
            return this.endTick;
        }

        long getElapsedTicks() {
            return this.elapsedTicks;
        }
    }

    private static final class CraftingTimingRecord implements Comparable<CraftingTimingRecord> {

        private long remainingToProduce;
        private long originalToProduce;
        private long startTick;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private long endTick;

        private CraftingTimingRecord(final long toProduce, final long startTick,
                final CraftingDiagnosticSessionId diagnosticSessionId) {
            this.remainingToProduce = toProduce;
            this.originalToProduce = toProduce;
            this.startTick = startTick;
            this.diagnosticSessionId = diagnosticSessionId;
        }

        private void addRemainingToProduce(final long delta) {
            this.remainingToProduce += delta;
        }

        private void addProduced(final long delta) {
            this.remainingToProduce += delta;
            this.originalToProduce += delta;
        }

        private long getRemainingToProduce() {
            return this.remainingToProduce;
        }

        private long getOriginalToProduce() {
            return this.originalToProduce;
        }

        private void setEndTick(final long endTick) {
            this.endTick = endTick;
        }

        private long getElapsedTicks() {
            return Math.max(1L, this.endTick - this.startTick);
        }

        private long getStartTick() {
            return this.startTick;
        }

        private long getEndTick() {
            return this.endTick;
        }

        private CraftingDiagnosticSessionId getDiagnosticSessionId() {
            return this.diagnosticSessionId;
        }

        @Override
        public int compareTo(final CraftingTimingRecord other) {
            final int byStartTick = Long.compare(this.startTick, other.startTick);
            if (byStartTick != 0) {
                return byStartTick;
            }

            return this.diagnosticSessionId.compareTo(other.diagnosticSessionId);
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof final CraftingTimingRecord other)) {
                return false;
            }
            return this.startTick == other.startTick
                    && Objects.equals(this.diagnosticSessionId, other.diagnosticSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.startTick, this.diagnosticSessionId);
        }
    }
}
