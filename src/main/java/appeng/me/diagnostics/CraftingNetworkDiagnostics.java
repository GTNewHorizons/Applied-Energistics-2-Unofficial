package appeng.me.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;

public final class CraftingNetworkDiagnostics {

    private final Map<IAEStack<?>, DiagnosticStats> diagnostics = new HashMap<>();
    private long revision = 0L;

    public void recordSample(final IAEStack<?> output, final CraftingDiagnosticSessionId sessionId,
            final long producedAmount, final long observedStartMillis, final long observedEndMillis) {
        if (output == null || producedAmount <= 0
                || sessionId == null
                || observedStartMillis <= 0
                || observedEndMillis <= observedStartMillis) {
            return;
        }

        final IAEStack<?> key = normalizeDiagnosticStack(output);
        if (key == null) {
            return;
        }

        final DiagnosticStats stats = this.diagnostics.computeIfAbsent(key, ignored -> new DiagnosticStats());
        stats.recordSample(sessionId, producedAmount, observedStartMillis, observedEndMillis);
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

    public List<CraftingGridCache.DiagnosticRowView> createRows(final String search,
            final CraftingGridCache.DiagnosticSortMode sortMode, final boolean ascending) {
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

        final List<CraftingGridCache.DiagnosticRowView> result = new ArrayList<>(rows.size());
        for (final Map.Entry<IAEStack<?>, DiagnosticStats> row : rows) {
            result.add(
                    new CraftingGridCache.DiagnosticRowView(
                            row.getKey().copy(),
                            row.getValue().getTotalProduced(),
                            row.getValue().getElapsedObservedTimeMillis(),
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
            final DiagnosticStats existing = this.diagnostics.computeIfAbsent(key, ignored -> new DiagnosticStats());
            existing.mergeFrom(loaded);
        }

        this.revision++;
    }

    private static int compareRows(final Map.Entry<IAEStack<?>, DiagnosticStats> left,
            final Map.Entry<IAEStack<?>, DiagnosticStats> right, final CraftingGridCache.DiagnosticSortMode sortMode) {
        return switch (sortMode) {
            case CRAFTED -> Long.compare(left.getValue().getTotalProduced(), right.getValue().getTotalProduced());
            case AVG_PER_SECOND -> Double
                    .compare(left.getValue().getItemsPerSecond(), right.getValue().getItemsPerSecond());
            case SAMPLES -> Long.compare(left.getValue().getSampleCount(), right.getValue().getSampleCount());
            case NAME -> left.getKey().getDisplayName().compareToIgnoreCase(right.getKey().getDisplayName());
            case CUMULATIVE_TIME -> Long.compare(
                    left.getValue().getElapsedObservedTimeMillis(),
                    right.getValue().getElapsedObservedTimeMillis());
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
        private long completedElapsedTimeMillis;
        private long completedSampleCount;
        private final Map<CraftingDiagnosticSessionId, DiagnosticSessionStats> sessions = new HashMap<>();
        private long lastObservedMillis;

        private void recordSample(final CraftingDiagnosticSessionId sessionId, final long producedAmount,
                final long observedStartMillis, final long observedEndMillis) {
            this.sessions.computeIfAbsent(sessionId, ignored -> new DiagnosticSessionStats())
                    .recordSample(producedAmount, observedStartMillis, observedEndMillis);
            this.lastObservedMillis = System.currentTimeMillis();
        }

        private long getTotalProduced() {
            long totalProduced = this.completedTotalProduced;
            for (final DiagnosticSessionStats session : this.sessions.values()) {
                totalProduced += session.totalProduced;
            }
            return totalProduced;
        }

        private long getElapsedObservedTimeMillis() {
            long elapsedObservedTimeMillis = this.completedElapsedTimeMillis;
            for (final DiagnosticSessionStats session : this.sessions.values()) {
                elapsedObservedTimeMillis += session.getElapsedObservedTimeMillis();
            }
            return elapsedObservedTimeMillis;
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
            this.completedElapsedTimeMillis += session.getElapsedObservedTimeMillis();
            this.completedSampleCount += session.sampleCount;
            return true;
        }

        private void mergeFrom(final DiagnosticStats loaded) {
            this.completedTotalProduced += loaded.completedTotalProduced;
            this.completedElapsedTimeMillis += loaded.completedElapsedTimeMillis;
            this.completedSampleCount += loaded.completedSampleCount;
            for (final Entry<CraftingDiagnosticSessionId, DiagnosticSessionStats> entry : loaded.sessions.entrySet()) {
                this.sessions.computeIfAbsent(entry.getKey(), ignored -> new DiagnosticSessionStats())
                        .mergeFrom(entry.getValue());
            }
            this.lastObservedMillis = Math.max(this.lastObservedMillis, loaded.lastObservedMillis);
        }

        private double getItemsPerSecond() {
            final long elapsedObservedTimeMillis = this.getElapsedObservedTimeMillis();
            if (elapsedObservedTimeMillis <= 0L) {
                return 0.0D;
            }

            return this.getTotalProduced() * (double) TimeUnit.SECONDS.toMillis(1) / (double) elapsedObservedTimeMillis;
        }

        private void writeToNBT(final NBTTagCompound tag) {
            tag.setLong("TotalProduced", this.getTotalProduced());
            tag.setLong("CompletedTotalProduced", this.completedTotalProduced);
            tag.setLong("CompletedElapsedTimeMillis", this.completedElapsedTimeMillis);
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
            tag.setLong("LastObservedMillis", this.lastObservedMillis);
        }

        private static DiagnosticStats fromNBT(final NBTTagCompound tag) {
            final DiagnosticStats stats = new DiagnosticStats();
            stats.completedTotalProduced = tag.getLong("CompletedTotalProduced");
            stats.completedElapsedTimeMillis = tag.hasKey("CompletedElapsedTimeMillis", Constants.NBT.TAG_LONG)
                    ? tag.getLong("CompletedElapsedTimeMillis")
                    : TimeUnit.MILLISECONDS.convert(tag.getLong("CompletedElapsedTimeNanos"), TimeUnit.NANOSECONDS);
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
                    stats.sessions.put(sessionId, DiagnosticSessionStats.fromNBT(sessionTag));
                }
            } else {
                final long legacyObservedTimeMillis = tag.hasKey("ObservedTimeMillis", Constants.NBT.TAG_LONG)
                        ? tag.getLong("ObservedTimeMillis")
                        : TimeUnit.MILLISECONDS.convert(tag.getLong("ObservedTimeNanos"), TimeUnit.NANOSECONDS);
                if (legacyObservedTimeMillis > 0L || tag.getLong("TotalProduced") > 0L) {
                    stats.completedTotalProduced = tag.getLong("TotalProduced");
                    stats.completedElapsedTimeMillis = legacyObservedTimeMillis;
                    stats.completedSampleCount = tag.getLong("SampleCount");
                }
            }
            stats.lastObservedMillis = tag.getLong("LastObservedMillis");
            return stats;
        }
    }

    private static final class DiagnosticSessionStats {

        private long totalProduced;
        private long firstObservedMillis;
        private long lastObservedMillis;
        private long sampleCount;

        private void recordSample(final long producedAmount, final long observedStartMillis,
                final long observedEndMillis) {
            this.totalProduced += producedAmount;
            this.sampleCount++;
            if (this.firstObservedMillis == 0L || observedStartMillis < this.firstObservedMillis) {
                this.firstObservedMillis = observedStartMillis;
            }
            if (observedEndMillis > this.lastObservedMillis) {
                this.lastObservedMillis = observedEndMillis;
            }
        }

        private long getElapsedObservedTimeMillis() {
            if (this.firstObservedMillis <= 0L || this.lastObservedMillis <= this.firstObservedMillis) {
                return 0L;
            }
            return this.lastObservedMillis - this.firstObservedMillis;
        }

        private void mergeFrom(final DiagnosticSessionStats loaded) {
            this.totalProduced += loaded.totalProduced;
            this.sampleCount += loaded.sampleCount;
            if (this.firstObservedMillis == 0L
                    || (loaded.firstObservedMillis > 0L && loaded.firstObservedMillis < this.firstObservedMillis)) {
                this.firstObservedMillis = loaded.firstObservedMillis;
            }
            this.lastObservedMillis = Math.max(this.lastObservedMillis, loaded.lastObservedMillis);
        }

        private void writeToNBT(final NBTTagCompound tag) {
            tag.setLong("TotalProduced", this.totalProduced);
            tag.setLong("FirstObservedMillis", this.firstObservedMillis);
            tag.setLong("LastObservedMillis", this.lastObservedMillis);
            tag.setLong("SampleCount", this.sampleCount);
        }

        private static DiagnosticSessionStats fromNBT(final NBTTagCompound tag) {
            final DiagnosticSessionStats stats = new DiagnosticSessionStats();
            stats.totalProduced = tag.getLong("TotalProduced");
            stats.firstObservedMillis = tag.hasKey("FirstObservedMillis", Constants.NBT.TAG_LONG)
                    ? tag.getLong("FirstObservedMillis")
                    : TimeUnit.MILLISECONDS.convert(tag.getLong("FirstObservedNanos"), TimeUnit.NANOSECONDS);
            stats.lastObservedMillis = tag.hasKey("LastObservedMillis", Constants.NBT.TAG_LONG)
                    ? tag.getLong("LastObservedMillis")
                    : TimeUnit.MILLISECONDS.convert(tag.getLong("LastObservedNanos"), TimeUnit.NANOSECONDS);
            stats.sampleCount = tag.getLong("SampleCount");
            return stats;
        }
    }
}
