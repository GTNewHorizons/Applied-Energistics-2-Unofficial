package appeng.me.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

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
        public final Map<IAEStack<?>, ItemFlowAccumulator> byItem = new Object2ObjectOpenHashMap<>();

        public Bucket(final long start) {
            this.start = start;
        }
    }

    private static final class ItemFlowAccumulator {

        public long in;
        public long out;
        public final Object2LongOpenHashMap<DimensionalCoord> netByLocation = new Object2LongOpenHashMap<>();

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

        accumulator.netByLocation.addTo(location, size);
    }

    public List<FlowSearchDTO> getRecentFlow(final IAEStack<?> queryStack) {
        final Object2LongLinkedOpenHashMap<DimensionalCoord> sources = new Object2LongLinkedOpenHashMap<>();
        final List<FlowSearchDTO> result = new ArrayList<>();

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<IAEStack<?>, ItemFlowAccumulator> entry : bucket.byItem.entrySet()) {

                if (!entry.getKey().isSameType(queryStack)) {
                    continue;
                }

                for (final Object2LongMap.Entry<DimensionalCoord> locationEntry : entry.getValue().netByLocation
                        .object2LongEntrySet()) {
                    sources.addTo(locationEntry.getKey(), locationEntry.getLongValue());
                }
            }
        }

        for (final Object2LongMap.Entry<DimensionalCoord> entry : sources.object2LongEntrySet()) {
            if (entry.getLongValue() != 0) {
                result.add(new FlowSearchDTO(entry.getKey(), entry.getLongValue()));
            }
        }

        return result;
    }

    public Map<IAEStack<?>, FlowRate> getAllRecentFlow() {
        final Object2LongLinkedOpenHashMap<IAEStack<?>> totalsIn = new Object2LongLinkedOpenHashMap<>();
        final Object2LongLinkedOpenHashMap<IAEStack<?>> totalsOut = new Object2LongLinkedOpenHashMap<>();
        final Map<IAEStack<?>, FlowRate> result = new Object2ObjectOpenHashMap<>();

        for (final Bucket bucket : this.buckets) {
            for (final Map.Entry<IAEStack<?>, ItemFlowAccumulator> entry : bucket.byItem.entrySet()) {
                totalsIn.addTo(entry.getKey(), entry.getValue().in);
                totalsOut.addTo(entry.getKey(), entry.getValue().out);
            }
        }

        for (final Object2LongMap.Entry<IAEStack<?>> entry : totalsIn.object2LongEntrySet()) {
            result.put(entry.getKey(), new FlowRate(entry.getLongValue(), totalsOut.getLong(entry.getKey())));
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
