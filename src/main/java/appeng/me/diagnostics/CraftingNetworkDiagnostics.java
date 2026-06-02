package appeng.me.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.DiagnosticSortMode;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;

public final class CraftingNetworkDiagnostics {

    private final Map<IAEStack<?>, DiagnosticStats> diagnostics = new HashMap<>();
    private long revision = 0L;
    private long diagnosticSessionCounter;

    public CraftingDiagnosticSessionId nextSessionId() {
        this.diagnosticSessionCounter++;
        return CraftingDiagnosticSessionId.of(this.diagnosticSessionCounter);
    }

    public long getSessionCounter() {
        return this.diagnosticSessionCounter;
    }

    public void setSessionCounter(final long diagnosticSessionCounter) {
        this.diagnosticSessionCounter = Math.max(this.diagnosticSessionCounter, diagnosticSessionCounter);
    }

    public void recordSample(final IAEStack<?> output, final CraftingDiagnosticSessionId sessionId,
            final long producedAmount, final long observedStartTick, final long observedEndTick) {
        if (output == null || producedAmount <= 0
                || sessionId == null
                || observedStartTick <= 0
                || observedEndTick < observedStartTick) {
            return;
        }

        final IAEStack<?> key = normalizeDiagnosticStack(output);
        if (key == null) {
            return;
        }

        final DiagnosticStats stats = this.diagnostics.computeIfAbsent(key, ignored -> new DiagnosticStats());
        stats.recordSample(sessionId, producedAmount, observedStartTick, observedEndTick);
        this.revision++;
    }

    public void completeSession(final CraftingDiagnosticSessionId sessionId) {
        if (sessionId == null) {
            return;
        }

        boolean changed = false;
        for (final DiagnosticStats stats : this.diagnostics.values()) {
            changed |= stats.compactSession(sessionId);
        }

        if (changed) {
            this.revision++;
        }
    }

    public void clear() {
        if (this.diagnostics.isEmpty()) {
            return;
        }

        this.diagnostics.clear();
        this.revision++;
    }

    public void clear(final IAEStack<?> output) {
        final IAEStack<?> key = normalizeDiagnosticStack(output);
        if (key == null) {
            return;
        }

        if (this.diagnostics.remove(key) != null) {
            this.revision++;
        }
    }

    public boolean isEmpty() {
        return this.diagnostics.isEmpty();
    }

    public long getRevision() {
        return this.revision;
    }

    public List<DiagnosticRowView> createRows(final String search, final DiagnosticSortMode sortMode,
            final boolean ascending) {
        final List<Map.Entry<IAEStack<?>, DiagnosticStats>> rows = new ArrayList<>();
        final String normalizedSearch = search == null ? "" : search.trim().toLowerCase();

        for (final Map.Entry<IAEStack<?>, DiagnosticStats> entry : this.diagnostics.entrySet()) {
            if (normalizedSearch.isEmpty()
                    || entry.getKey().getDisplayName().toLowerCase().contains(normalizedSearch)) {
                rows.add(entry);
            }
        }

        rows.sort((left, right) -> {
            int comparison = compareRows(left, right, sortMode);
            if (!ascending) {
                comparison = -comparison;
            }
            if (comparison == 0) {
                comparison = left.getKey().getDisplayName().compareToIgnoreCase(right.getKey().getDisplayName());
            }
            if (comparison == 0) {
                comparison = Integer.compare(left.getKey().hashCode(), right.getKey().hashCode());
            }
            return comparison;
        });

        final List<DiagnosticRowView> result = new ArrayList<>(rows.size());
        for (final Map.Entry<IAEStack<?>, DiagnosticStats> row : rows) {
            result.add(
                    new DiagnosticRowView(
                            row.getKey().copy(),
                            row.getValue().getTotalProduced(),
                            row.getValue().getElapsedObservedTicks(),
                            row.getValue().getSampleCount()));
        }
        return result;
    }

    public NBTTagList writeToNBT() {
        final NBTTagList list = new NBTTagList();
        for (final Map.Entry<IAEStack<?>, DiagnosticStats> entry : this.diagnostics.entrySet()) {
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setTag("Stack", entry.getKey().toNBTGeneric());
            entry.getValue().writeToNBT(tag);
            list.appendTag(tag);
        }
        return list;
    }

    public void readFromNBT(final NBTTagList list, final boolean merge) {
        if (!merge) {
            this.diagnostics.clear();
        }

        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound tag = list.getCompoundTagAt(i);
            final IAEStack<?> stack = Platform.readStackNBT(tag.getCompoundTag("Stack"));
            if (stack == null) {
                continue;
            }

            final IAEStack<?> key = normalizeDiagnosticStack(stack);
            if (key == null) {
                continue;
            }

            final DiagnosticStats loaded = DiagnosticStats.fromNBT(tag);
            if (loaded == null) {
                continue;
            }
            final DiagnosticStats existing = this.diagnostics.computeIfAbsent(key, ignored -> new DiagnosticStats());
            existing.mergeFrom(loaded);
        }

        this.revision++;
    }

    private static int compareRows(final Map.Entry<IAEStack<?>, DiagnosticStats> left,
            final Map.Entry<IAEStack<?>, DiagnosticStats> right, final DiagnosticSortMode sortMode) {
        return switch (sortMode) {
            case CRAFTED -> Long.compare(left.getValue().getTotalProduced(), right.getValue().getTotalProduced());
            case AVG_PER_SECOND -> Double
                    .compare(left.getValue().getItemsPerSecond(), right.getValue().getItemsPerSecond());
            case SAMPLES -> Long.compare(left.getValue().getSampleCount(), right.getValue().getSampleCount());
            case NAME -> left.getKey().getDisplayName().compareToIgnoreCase(right.getKey().getDisplayName());
            case CUMULATIVE_TIME -> Long
                    .compare(left.getValue().getElapsedObservedTicks(), right.getValue().getElapsedObservedTicks());
        };
    }

    private static IAEStack<?> normalizeDiagnosticStack(final IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }

        final IAEStack<?> normalized = stack.copy();
        normalized.reset();
        normalized.setStackSize(1);
        return normalized;
    }

    private static final class DiagnosticStats {

        private long completedTotalProduced;
        private long completedElapsedTimeTicks;
        private long completedSampleCount;
        private final Map<CraftingDiagnosticSessionId, DiagnosticSessionStats> sessions = new HashMap<>();

        private void recordSample(final CraftingDiagnosticSessionId sessionId, final long producedAmount,
                final long observedStartTick, final long observedEndTick) {
            this.sessions.computeIfAbsent(sessionId, ignored -> new DiagnosticSessionStats())
                    .recordSample(producedAmount, observedStartTick, observedEndTick);
        }

        private long getTotalProduced() {
            long totalProduced = this.completedTotalProduced;
            for (final DiagnosticSessionStats session : this.sessions.values()) {
                totalProduced += session.totalProduced;
            }
            return totalProduced;
        }

        private long getElapsedObservedTicks() {
            long elapsedObservedTicks = this.completedElapsedTimeTicks;
            for (final DiagnosticSessionStats session : this.sessions.values()) {
                elapsedObservedTicks += session.getElapsedObservedTicks();
            }
            return elapsedObservedTicks;
        }

        private long getSampleCount() {
            long sampleCount = this.completedSampleCount;
            for (final DiagnosticSessionStats session : this.sessions.values()) {
                sampleCount += session.sampleCount;
            }
            return sampleCount;
        }

        private boolean compactSession(final CraftingDiagnosticSessionId sessionId) {
            final DiagnosticSessionStats session = this.sessions.remove(sessionId);
            if (session == null) {
                return false;
            }

            this.completedTotalProduced += session.totalProduced;
            this.completedElapsedTimeTicks += session.getElapsedObservedTicks();
            this.completedSampleCount += session.sampleCount;
            return true;
        }

        private void mergeFrom(final DiagnosticStats loaded) {
            this.completedTotalProduced += loaded.completedTotalProduced;
            this.completedElapsedTimeTicks += loaded.completedElapsedTimeTicks;
            this.completedSampleCount += loaded.completedSampleCount;
            for (final Entry<CraftingDiagnosticSessionId, DiagnosticSessionStats> entry : loaded.sessions.entrySet()) {
                this.sessions.computeIfAbsent(entry.getKey(), ignored -> new DiagnosticSessionStats())
                        .mergeFrom(entry.getValue());
            }
        }

        private double getItemsPerSecond() {
            final long elapsedObservedTicks = this.getElapsedObservedTicks();
            if (elapsedObservedTicks <= 0L) {
                return 0.0D;
            }

            return this.getTotalProduced() * 20.0D / (double) elapsedObservedTicks;
        }

        private void writeToNBT(final NBTTagCompound tag) {
            tag.setLong("TotalProduced", this.getTotalProduced());
            tag.setLong("CompletedTotalProduced", this.completedTotalProduced);
            tag.setLong("CompletedElapsedTimeTicks", this.completedElapsedTimeTicks);
            tag.setLong("CompletedSampleCount", this.completedSampleCount);
            final NBTTagList sessionsTag = new NBTTagList();
            for (final Entry<CraftingDiagnosticSessionId, DiagnosticSessionStats> entry : this.sessions.entrySet()) {
                final NBTTagCompound sessionTag = new NBTTagCompound();
                entry.getKey().writeToNBT(sessionTag, "SessionId");
                entry.getValue().writeToNBT(sessionTag);
                sessionsTag.appendTag(sessionTag);
            }
            tag.setTag("Sessions", sessionsTag);
            tag.setLong("SampleCount", this.getSampleCount());
        }

        private static DiagnosticStats fromNBT(final NBTTagCompound tag) {
            if (!tag.hasKey("CompletedElapsedTimeTicks", Constants.NBT.TAG_LONG)) {
                return null;
            }

            final DiagnosticStats stats = new DiagnosticStats();
            stats.completedTotalProduced = tag.getLong("CompletedTotalProduced");
            stats.completedElapsedTimeTicks = tag.getLong("CompletedElapsedTimeTicks");
            stats.completedSampleCount = tag.getLong("CompletedSampleCount");
            if (tag.hasKey("Sessions", Constants.NBT.TAG_LIST)) {
                final NBTTagList sessionsTag = tag.getTagList("Sessions", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < sessionsTag.tagCount(); i++) {
                    final NBTTagCompound sessionTag = sessionsTag.getCompoundTagAt(i);
                    final CraftingDiagnosticSessionId sessionId = CraftingDiagnosticSessionId
                            .fromNBT(sessionTag, "SessionId");
                    if (sessionId == null) {
                        continue;
                    }
                    final DiagnosticSessionStats sessionStats = DiagnosticSessionStats.fromNBT(sessionTag);
                    if (sessionStats != null) {
                        stats.completedTotalProduced += sessionStats.totalProduced;
                        stats.completedElapsedTimeTicks += sessionStats.getElapsedObservedTicks();
                        stats.completedSampleCount += sessionStats.sampleCount;
                    }
                }
            }
            return stats;
        }
    }

    private static final class DiagnosticSessionStats {

        private long totalProduced;
        private long elapsedObservedTicks;
        private long firstObservedTick;
        private long lastObservedTick;
        private long sampleCount;

        private void recordSample(final long producedAmount, final long observedStartTick, final long observedEndTick) {
            this.totalProduced += producedAmount;
            this.sampleCount++;
            if (this.firstObservedTick == 0L || observedStartTick < this.firstObservedTick) {
                this.firstObservedTick = observedStartTick;
            }
            if (observedEndTick > this.lastObservedTick) {
                this.lastObservedTick = observedEndTick;
            }
        }

        private long getElapsedObservedTicks() {
            long elapsed = this.elapsedObservedTicks;
            if (this.firstObservedTick > 0L && this.sampleCount > 0L) {
                elapsed += Math.max(1L, this.lastObservedTick - this.firstObservedTick);
            }
            return elapsed;
        }

        private void mergeFrom(final DiagnosticSessionStats loaded) {
            this.totalProduced += loaded.totalProduced;
            this.sampleCount += loaded.sampleCount;
            this.elapsedObservedTicks += loaded.getElapsedObservedTicks();
        }

        private void writeToNBT(final NBTTagCompound tag) {
            tag.setLong("TotalProduced", this.totalProduced);
            tag.setLong("ElapsedObservedTicks", this.getElapsedObservedTicks());
            tag.setLong("SampleCount", this.sampleCount);
        }

        private static DiagnosticSessionStats fromNBT(final NBTTagCompound tag) {
            if (!tag.hasKey("ElapsedObservedTicks", Constants.NBT.TAG_LONG)) {
                return null;
            }

            final DiagnosticSessionStats stats = new DiagnosticSessionStats();
            stats.totalProduced = tag.getLong("TotalProduced");
            stats.sampleCount = tag.getLong("SampleCount");
            stats.elapsedObservedTicks = tag.getLong("ElapsedObservedTicks");
            return stats;
        }
    }
}
