package appeng.me.cluster.implementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Objects;
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

    void trackProducedOutput(final IAEStack<?> output, final long startTime,
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
                startTime,
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
        final long endTime = currentDiagnosticTimeNanos();
        final List<CompletedDiagnosticRecord> completedRecords = new ArrayList<>();
        while (remainingReturned > 0 && !records.isEmpty()) {
            final CraftingTimingRecord record = records.first();
            final long consumed = Math.min(remainingReturned, record.getRemainingToProduce());
            record.addRemainingToProduce(-consumed);
            remainingReturned -= consumed;

            if (record.getRemainingToProduce() <= 0) {
                record.setEndTime(endTime);
                completedRecords.add(
                        new CompletedDiagnosticRecord(
                                returnedStack,
                                record.getDiagnosticSessionId(),
                                record.getOriginalToProduce(),
                                record.getStartTime(),
                                record.getEndTime(),
                                record.getElapsedTime()));
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
                        && (record.getRemainingToProduce() > 0 || record.getEndTime() == 0L)) {
                    return true;
                }
            }
        }

        return false;
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

    private static long currentDiagnosticTimeNanos() {
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    static final class CompletedDiagnosticRecord {

        private final IAEStack<?> output;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private final long producedAmount;
        private final long startTime;
        private final long endTime;
        private final long elapsedTime;

        private CompletedDiagnosticRecord(final IAEStack<?> output,
                final CraftingDiagnosticSessionId diagnosticSessionId, final long producedAmount, final long startTime,
                final long endTime, final long elapsedTime) {
            this.output = output;
            this.diagnosticSessionId = diagnosticSessionId;
            this.producedAmount = producedAmount;
            this.startTime = startTime;
            this.endTime = endTime;
            this.elapsedTime = elapsedTime;
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

        long getStartTime() {
            return this.startTime;
        }

        long getEndTime() {
            return this.endTime;
        }

        long getElapsedTime() {
            return this.elapsedTime;
        }
    }

    private static final class CraftingTimingRecord implements Comparable<CraftingTimingRecord> {

        private long remainingToProduce;
        private long originalToProduce;
        private long accumulatedElapsedTime;
        private long startTime;
        private final CraftingDiagnosticSessionId diagnosticSessionId;
        private long endTime;

        private CraftingTimingRecord(final long toProduce, final long startTime,
                final CraftingDiagnosticSessionId diagnosticSessionId) {
            this.remainingToProduce = toProduce;
            this.originalToProduce = toProduce;
            this.startTime = startTime;
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

        private void setEndTime(final long endTime) {
            this.endTime = endTime;
        }

        private long getElapsedTime() {
            return this.accumulatedElapsedTime + Math.max(0L, this.endTime - this.startTime);
        }

        private long getStartTime() {
            return this.startTime;
        }

        private long getEndTime() {
            return this.endTime;
        }

        private CraftingDiagnosticSessionId getDiagnosticSessionId() {
            return this.diagnosticSessionId;
        }

        private NBTTagCompound writeToNBT() {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("remainingToProduce", this.remainingToProduce);
            tag.setLong("originalToProduce", this.originalToProduce);
            tag.setLong(
                    "elapsedTime",
                    this.accumulatedElapsedTime + Math.max(0L, currentDiagnosticTimeNanos() - this.startTime));
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
                    currentDiagnosticTimeNanos(),
                    diagnosticSessionId);
            record.accumulatedElapsedTime = tag.getLong("elapsedTime");
            record.remainingToProduce = tag.getLong("remainingToProduce");
            return record;
        }

        @Override
        public int compareTo(final CraftingTimingRecord other) {
            final int byStartTime = Long.compare(this.startTime, other.startTime);
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
            return this.startTime == other.startTime && Objects.equals(this.diagnosticSessionId, other.diagnosticSessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.startTime, this.diagnosticSessionId);
        }
    }
}
