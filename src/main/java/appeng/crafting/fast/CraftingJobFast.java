package appeng.crafting.fast;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingCalculations;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.longs.LongObjectImmutablePair;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLongBiConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public final class CraftingJobFast<StackType extends IAEStack<StackType>> implements ICraftingJob<StackType> {

    private final CraftingContext context;
    private final CraftingRequest originalRequest;
    private final ICraftingCallback callback;

    private final AbstractObject2LongMap<ICraftingPatternDetails> tasks = new Object2LongOpenHashMap<>();
    private final AbstractObject2LongMap<IAEStack<?>> ingredients = new Object2LongOpenHashMap<>();
    private final AbstractObject2LongMap<IAEStack<?>> missingIngredients = new Object2LongOpenHashMap<>();
    private boolean calculated = false;
    private boolean isSimulated = false;
    private long byteCost = 0;
    private String errorMsg = "";

    public CraftingJobFast(final World world, final IGrid meGrid, final BaseActionSource actionSource,
            final StackType what, final CraftingMode craftingMode, final ICraftingCallback callback) {
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequest = new CraftingRequest(what, SubstitutionMode.PRECISE_FRESH, true, craftingMode);
        this.context.addRequest(this.originalRequest);
        this.context.itemModel.ignore(what);
    }

    public CraftingContext getContext() {
        return this.context;
    }

    public void forEachPattern(ObjectLongBiConsumer<ICraftingPatternDetails> consumer) {
        tasks.forEach(consumer);
    }

    /**
     * No ore dict, no substitution, currently no multi-output too. Does not respect any priority, will only use one
     * pattern. This ensures no backtracking is needed.
     */
    private void calculateImpl() {
        SccResolver.Result result = SccResolver.compute(originalRequest.stack, stack -> {
            List<ICraftingPatternDetails> patterns = context.getPrecisePatternsFor(stack);
            if (patterns.isEmpty()) {
                return null;
            }
            for (ICraftingPatternDetails pattern : patterns) {
                for (var out : pattern.getCondensedAEOutputs()) {
                    if (out.equals(stack)) {
                        return new LongObjectImmutablePair<>(out.getStackSize(), pattern);
                    }
                }
            }
            return null;
        });
        AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> resolved = result.patterns;
        AbstractObject2LongMap<IAEStack<?>> inDegree = result.inDegree;
        // Now calculates the tree, by traversing in topological order
        AbstractObjectSet<IAEStack<?>> traversed = new ObjectOpenHashSet<>();
        Queue<IAEStack<?>> loopCandidates = new ArrayDeque<>();
        // Contains anything that has zero in-degree / search head currently
        Queue<IAEStack<?>> toTraverse = new ArrayDeque<>();
        missingIngredients.put(originalRequest.stack, originalRequest.stack.getStackSize());
        toTraverse.add(originalRequest.stack);
        while (!toTraverse.isEmpty() || !loopCandidates.isEmpty()) {
            IAEStack<?> current = toTraverse.poll();
            if (current == null) {
                current = loopCandidates.poll();
            }
            if (current == null) {
                continue;
            }
            if (traversed.contains(current)) {
                // Some form of loop, do not expand again
                continue;
            }
            traversed.add(current);
            var currentPair = resolved.get(current);
            if (currentPair != null) {
                // Regardless of whether we actually need the pattern / output, need to expand so that in-degrees
                // are updated correctly. We're doing topological sort on the fly here.
                for (IAEStack<?> stack : currentPair.right().getCondensedAEInputs()) {
                    if (addCountToMap(inDegree, stack, -1) == 0) {
                        toTraverse.add(stack);
                    }
                    if (result.loopingPatterns.contains(stack)) {
                        loopCandidates.add(stack);
                    }
                }
            }
            exploreItem(current, currentPair);
        }
    }

    // currentPair is the output-pattern pair, where output contains the pattern output count
    private void exploreItem(IAEStack<?> current, @Nullable LongObjectPair<ICraftingPatternDetails> currentPair) {
        long count = moveMissingByExtract(current);
        if (count == 0) return;
        if (currentPair == null) return;
        // Apply the pattern
        ICraftingPatternDetails pattern = currentPair.right();
        long patternMultiplier = Platform.ceilDiv(count, currentPair.leftLong());
        for (IAEStack<?> stack : pattern.getCondensedAEInputs()) {
            addCountToMap(missingIngredients, stack, Math.multiplyExact(stack.getStackSize(), patternMultiplier));
        }
        addCountToMap(tasks, pattern, patternMultiplier);
        byteCost += CraftingCalculations
                .adjustByteCost(current, Math.multiplyExact(currentPair.leftLong(), patternMultiplier));
        addCountToMap(missingIngredients, current, -count);
    }

    // Returns the remainder count
    private long moveMissingByExtract(IAEStack<?> stack) {
        long count = missingIngredients.getLong(stack);
        IAEStack<?> result = context.itemModel.extractItems(stack.copy().setStackSize(count), Actionable.MODULATE);
        if (result != null) {
            long extracted = result.getStackSize();
            addCountToMap(ingredients, stack, extracted);
            byteCost += CraftingCalculations.adjustByteCost(stack, extracted);
            if (extracted >= count) {
                missingIngredients.removeLong(stack);
                return 0;
            }
            addCountToMap(missingIngredients, stack, -extracted);
            return count - extracted;
        }
        return count;
    }

    private void calculate() {
        if (calculated) return;
        try {
            calculateImpl();
        } catch (Throwable t) {
            errorMsg = t.toString();
            isSimulated = true;
        } finally {
            calculated = true;
        }
    }

    private <T> long addCountToMap(AbstractObject2LongMap<T> map, T key, long count) {
        long oldValue = map.getLong(key);
        long newValue = Math.addExact(oldValue, count);
        if (newValue == 0) {
            map.removeLong(key);
            return 0;
        }
        map.put(key, newValue);
        return newValue;
    }

    @Override
    public boolean isSimulation() {
        return isSimulated
                || (originalRequest.craftingMode != CraftingMode.IGNORE_MISSING && !missingIngredients.isEmpty());
    }

    @Override
    public long getByteTotal() {
        return byteCost;
    }

    @Override
    public StackType getOutput() {
        return (StackType) originalRequest.stack;
    }

    @Override
    public boolean simulateFor(int milli) {
        calculate();
        if (callback != null) {
            callback.calculationComplete(this);
        }
        return false;
    }

    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        calculate();
        TickHandler.INSTANCE.registerCraftingSimulation(this.context.world, this);
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cluster) {
        return cluster instanceof CraftingCPUCluster;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.originalRequest.craftingMode;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU craftingCPUCluster, BaseActionSource src) {
        calculate();
        CraftingCPUCluster cluster = (CraftingCPUCluster) craftingCPUCluster;
        for (var entry : tasks.object2LongEntrySet()) {
            cluster.addCrafting(entry.getKey(), entry.getLongValue());
        }
        for (var entry : ingredients.object2LongEntrySet()) {
            pullStack(storage, cluster, entry.getKey(), entry.getLongValue());
        }
        for (var entry : missingIngredients.object2LongEntrySet()) {
            pullStack(storage, cluster, entry.getKey(), entry.getLongValue());
        }
    }

    private void pullStack(MECraftingInventory storage, CraftingCPUCluster cluster, IAEStack<?> stack, long count) {
        IAEStack<?> extracted = storage.extractItems(stack.copy().setStackSize(count), Actionable.MODULATE);
        if (extracted == null || extracted.getStackSize() != count) {
            if (cluster.isMissingMode()) {
                if (extracted == null) {
                    cluster.addEmitable(stack.copy().setStackSize(count));
                    return;
                } else if (extracted.getStackSize() != count) {
                    cluster.addEmitable(stack.copy().setStackSize(count - extracted.getStackSize()));
                }
            } else {
                throw new CraftBranchFailure(stack, count);
            }
        }
        cluster.addStorage(extracted);
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        return context.availableCache;
    }

    @Override
    public void populatePlan(IItemList plan) {
        // This is what gets shown in the crafting plan
        for (var entry : tasks.object2LongEntrySet()) {
            for (var output : entry.getKey().getCondensedAEOutputs()) {
                plan.addRequestable(
                        output.copy().setStackSize(0).setCountRequestable(entry.getLongValue() * output.getStackSize())
                                .setCountRequestableCrafts(entry.getLongValue()));
            }
        }
        for (var entry : ingredients.object2LongEntrySet()) {
            plan.add(entry.getKey().copy().setStackSize(entry.getLongValue()));
        }
        for (var entry : missingIngredients.object2LongEntrySet()) {
            if (originalRequest.craftingMode == CraftingMode.IGNORE_MISSING) {
                plan.addRequestable(entry.getKey().copy().setStackSize(0).setCountRequestable(entry.getLongValue()));
            } else {
                plan.add(entry.getKey().copy().setStackSize(entry.getLongValue()));
            }
        }
    }

    @Override
    public String getErrorMessage() {
        return errorMsg;
    }
}
