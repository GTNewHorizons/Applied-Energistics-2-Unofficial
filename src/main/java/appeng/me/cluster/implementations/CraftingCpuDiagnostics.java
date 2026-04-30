package appeng.me.cluster.implementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.api.storage.data.IAEStack;
import appeng.me.diagnostics.CraftingDiagnosticSessionId;
import appeng.util.Platform;

final class CraftingCpuDiagnostics {

    private final Map<IAEStack<?>, NavigableSet<CraftingTimingRecord>> outputTimingRecords = new HashMap<>();
    private long diagnosticSessionCounter;

    CraftingDiagnosticSessionId nextSessionId() {
        this.diagnosticSessionCounter++;
        return CraftingDiagnosticSessionId.of(this.diagnosticSessionCounter);
    }

    long getSessionCounter() {
        return this.diagnosticSessionCounter;
    }

    void setSessionCounter(final long diagnosticSessionCounter) {
        this.diagnosticSessionCounter = Math.max(0L, diagnosticSessionCounter);
    }

    void clear() {
        this.outputTimingRecords.clear();
    }

    void trackProducedOutput(final IAEStack<?> output, final long startTimeMillis,
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
                startTimeMillis,
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
        final long endTimeMillis = currentDiagnosticTimeMillis();
        final List<CompletedDiagnosticRecord> completedRecords = new ArrayList<>();
        while (remainingReturned > 0 && !records.isEmpty()) {
            final CraftingTimingRecord record = records.first();
            final long consumed = Math.min(remainingReturned, record.getRemainingToProduce());
            record.addRemainingToProduce(-consumed);
            remainingReturned -= consumed;

            if (record.getRemainingToProduce() <= 0) {
                record.setEndTimeMillis(endTimeMillis);
                completedRecords.add(
                        new CompletedDiagnosticRecord(
                                returnedStack,
                                record.getDiagnosticSessionId(),
                                record.getOriginalToProduce(),
                                record.getStartTimeMillis(),
                                record.getEndTimeMillis(),
                                record.getElapsedTimeMillis()));
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
                        && (record.getRemainingToProduce() > 0 || record.getEndTimeMillis() == 0L)) {
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

    NBTTagList writeToNBT() {
        final NBTTagList recordsTag = new NBTTagList();
        for (final Entry<IAEStack<?>, NavigableSet<CraftingTimingRecord>> entry : this.outputTimingRecords.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            final NBTTagCompound stackTag = new NBTTagCompound();
            stackTag.setTag("stack", entry.getKey().toNBTGeneric());
            final NBTTagList timingList = new NBTTagList();
            for (final CraftingTimingRecord record : entry.getValue()) {
                timingList.appendTag(record.writeToNBT());
            }
            stackTag.setTag("records", timingList);
            recordsTag.appendTag(stackTag);
        }
        return recordsTag;
    }

    void readFromNBT(final NBTTagList recordsTag) {
        this.outputTimingRecords.clear();
        for (int i = 0; i < recordsTag.tagCount(); i++) {
            final NBTTagCompound stackTag = recordsTag.getCompoundTagAt(i);
            final IAEStack<?> stack = Platform.readStackNBT(stackTag.getCompoundTag("stack"));
            final IAEStack<?> normalizedStack = normalizeTrackingStack(stack);
            if (normalizedStack == null) {
                continue;
            }

            final NavigableSet<CraftingTimingRecord> records = new TreeSet<>();
            final NBTTagList timingList = stackTag.getTagList("records", NBT.TAG_COMPOUND);
            for (int j = 0; j < timingList.tagCount(); j++) {
                final CraftingTimingRecord record = CraftingTimingRecord.fromNBT(timingList.getCompoundTagAt(j));
                if (record != null) {
                    records.add(record);
                }
            }

            if (!records.isEmpty()) {
                this.outputTimingRecords.put(normalizedStack, records);
            }
        }
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

    private static long currentDiagnosticTimeMillis() {
        return System.currentTimeMillis();
    }

    static final class CompletedDiagnosticRecord {

        private final IAEStack<?> output;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private final long producedAmount;
        private final long startTimeMillis;
        private final long endTimeMillis;
        private final long elapsedTimeMillis;

        private CompletedDiagnosticRecord(final IAEStack<?> output,
                final CraftingDiagnosticSessionId diagnosticSessionId, final long producedAmount,
                final long startTimeMillis, final long endTimeMillis, final long elapsedTimeMillis) {
            this.output = output;
            this.diagnosticSessionId = diagnosticSessionId;
            this.producedAmount = producedAmount;
            this.startTimeMillis = startTimeMillis;
            this.endTimeMillis = endTimeMillis;
            this.elapsedTimeMillis = elapsedTimeMillis;
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

        long getStartTimeMillis() {
            return this.startTimeMillis;
        }

        long getEndTimeMillis() {
            return this.endTimeMillis;
        }

        long getElapsedTimeMillis() {
            return this.elapsedTimeMillis;
        }
    }

    private static final class CraftingTimingRecord implements Comparable<CraftingTimingRecord> {

        private long remainingToProduce;
        private long originalToProduce;
        private long accumulatedElapsedTimeMillis;
        private long startTimeMillis;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private long endTimeMillis;

        private CraftingTimingRecord(final long toProduce, final long startTimeMillis,
                final CraftingDiagnosticSessionId diagnosticSessionId) {
            this.remainingToProduce = toProduce;
            this.originalToProduce = toProduce;
            this.startTimeMillis = startTimeMillis;
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

        private void setEndTimeMillis(final long endTimeMillis) {
            this.endTimeMillis = endTimeMillis;
        }

        private long getElapsedTimeMillis() {
            return this.accumulatedElapsedTimeMillis + Math.max(0L, this.endTimeMillis - this.startTimeMillis);
        }

        private long getStartTimeMillis() {
            return this.startTimeMillis;
        }

        private long getEndTimeMillis() {
            return this.endTimeMillis;
        }

        private CraftingDiagnosticSessionId getDiagnosticSessionId() {
            return this.diagnosticSessionId;
        }

        private NBTTagCompound writeToNBT() {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("remainingToProduce", this.remainingToProduce);
            tag.setLong("originalToProduce", this.originalToProduce);
            tag.setLong(
                    "elapsedTimeMillis",
                    this.accumulatedElapsedTimeMillis
                            + Math.max(0L, currentDiagnosticTimeMillis() - this.startTimeMillis));
            this.diagnosticSessionId.writeToNBT(tag, "diagnosticSessionId");
            return tag;
        }

        private static CraftingTimingRecord fromNBT(final NBTTagCompound tag) {
            final CraftingDiagnosticSessionId diagnosticSessionId = CraftingDiagnosticSessionId
                    .fromNBT(tag, "diagnosticSessionId");
            if (diagnosticSessionId == null) {
                return null;
            }

            final CraftingTimingRecord record = new CraftingTimingRecord(
                    tag.getLong("originalToProduce"),
                    currentDiagnosticTimeMillis(),
                    diagnosticSessionId);
            record.accumulatedElapsedTimeMillis = tag.hasKey("elapsedTimeMillis", NBT.TAG_LONG)
                    ? tag.getLong("elapsedTimeMillis")
                    : TimeUnit.MILLISECONDS.convert(tag.getLong("elapsedTime"), TimeUnit.NANOSECONDS);
            record.remainingToProduce = tag.getLong("remainingToProduce");
            return record;
        }

        @Override
        public int compareTo(final CraftingTimingRecord other) {
            final int byStartTime = Long.compare(this.startTimeMillis, other.startTimeMillis);
            if (byStartTime != 0) {
                return byStartTime;
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
            return this.startTimeMillis == other.startTimeMillis
                    && Objects.equals(this.diagnosticSessionId, other.diagnosticSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.startTimeMillis, this.diagnosticSessionId);
        }
    }
}
