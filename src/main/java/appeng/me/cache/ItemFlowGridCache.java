package appeng.me.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.FlowSearchDTO;
import appeng.core.AEConfig;

public class ItemFlowGridCache implements IGridCache {

    public static class FlowRate {

        private final long in;
        private final long out;

        public FlowRate(final long in, final long out) {
            this.in = in;
            this.out = out;
        }

        public long in() {
            return this.in;
        }

        public long out() {
            return this.out;
        }

        public long net() {
            return this.in - this.out;
        }
    }

    private static final class Bucket {

        public final long start;
        public final Map<IAEStack<?>, ItemFlowAccumulator> byItem = new HashMap<>();

        public Bucket(final long start) {
            this.start = start;
        }
    }

    private static final class ItemFlowAccumulator {

        public long in;
        public long out;
        public final Map<DimensionalCoord, Long> netByLocation = new HashMap<>();

    }

    private static final long BUCKET_SIZE_MS = 200L;

    private static final String TRACKING_ENABLED_KEY = "ItemFlowTrackingEnabled";

    private final ArrayDeque<Bucket> buckets = new ArrayDeque<>();

    private boolean trackingEnabled = false;

    public ItemFlowGridCache(final IGrid grid) {}

    public boolean isTrackingEnabled() {
        return this.trackingEnabled;
    }

    public void setTrackingEnabled(final boolean enabled) {
        this.trackingEnabled = enabled;
        if (!enabled) {
            this.buckets.clear();
        }
    }

    public void recordFlow(final IAEStack<?> diff, final BaseActionSource src) {
        if (!AEConfig.instance.enableItemFlowTracking || !this.trackingEnabled) {
            return;
        }

        if (!(src instanceof MachineSource machineSource)) {
            return;
        }

        final IGridNode node = machineSource.via.getActionableNode();
        if (node == null) {
            return;
        }

        final DimensionalCoord location = node.getGridBlock().getLocation();
        if (location == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        Bucket bucket = this.buckets.peekLast();

        if (bucket == null || now - bucket.start >= BUCKET_SIZE_MS) {
            bucket = new Bucket(now);
            this.buckets.addLast(bucket);
        }

        final long size = diff.getStackSize();
        final ItemFlowAccumulator accumulator = bucket.byItem
                .computeIfAbsent(diff.copy(), k -> new ItemFlowAccumulator());

        if (size < 0) {
            accumulator.out -= size;
        } else {
            accumulator.in += size;
        }

        accumulator.netByLocation.merge(location, size, Long::sum);
    }

    public List<FlowSearchDTO> getRecentFlow(final IAEStack<?> queryStack) {
        final Map<DimensionalCoord, Long> sources = new LinkedHashMap<>();
        final List<FlowSearchDTO> result = new ArrayList<>();

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<IAEStack<?>, ItemFlowAccumulator> entry : bucket.byItem.entrySet()) {

                if (!entry.getKey().isSameType(queryStack)) {
                    continue;
                }

                for (final Map.Entry<DimensionalCoord, Long> locationEntry : entry.getValue().netByLocation
                        .entrySet()) {
                    sources.merge(locationEntry.getKey(), locationEntry.getValue(), Long::sum);
                }
            }
        }

        for (final Map.Entry<DimensionalCoord, Long> entry : sources.entrySet()) {
            if (entry.getValue() != 0) {
                result.add(new FlowSearchDTO(entry.getKey(), entry.getValue()));
            }
        }

        return result;
    }

    public Map<IAEStack<?>, FlowRate> getAllRecentFlow() {
        final Map<IAEStack<?>, Long> totalsIn = new LinkedHashMap<>();
        final Map<IAEStack<?>, Long> totalsOut = new LinkedHashMap<>();
        final Map<IAEStack<?>, FlowRate> result = new HashMap<>();

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<IAEStack<?>, ItemFlowAccumulator> entry : bucket.byItem.entrySet()) {
                totalsIn.merge(entry.getKey(), entry.getValue().in, Long::sum);
                totalsOut.merge(entry.getKey(), entry.getValue().out, Long::sum);
            }
        }

        for (final Map.Entry<IAEStack<?>, Long> entry : totalsIn.entrySet()) {
            result.put(entry.getKey(), new FlowRate(entry.getValue(), totalsOut.get(entry.getKey())));
        }

        return result;
    }

    @Override
    public void onUpdateTick() {
        final long cutoff = System.currentTimeMillis()
                - Math.max(1, AEConfig.instance.itemFlowTrackingWindowMinutes) * 60_000L;
        while (!this.buckets.isEmpty() && this.buckets.peekFirst().start + BUCKET_SIZE_MS <= cutoff) {
            this.buckets.pollFirst();
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {}

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {}

    @Override
    public void onSplit(final IGridStorage destinationStorage) {
        destinationStorage.dataObject().setBoolean(TRACKING_ENABLED_KEY, this.trackingEnabled);
    }

    @Override
    public void onJoin(final IGridStorage sourceStorage) {
        if (sourceStorage == null) {
            return;
        }

        final NBTTagCompound data = sourceStorage.dataObject();
        if (data.hasKey(TRACKING_ENABLED_KEY)) {
            this.trackingEnabled = this.trackingEnabled || data.getBoolean(TRACKING_ENABLED_KEY);
        }
    }

    @Override
    public void populateGridStorage(final IGridStorage destinationStorage) {
        destinationStorage.dataObject().setBoolean(TRACKING_ENABLED_KEY, this.trackingEnabled);
    }

}
