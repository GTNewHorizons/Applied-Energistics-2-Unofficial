package appeng.crafting.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MutableClassToInstanceMap;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.CraftingTask.State;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.OreListMultiMap;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * A bundle of state for the crafting operation like the ME grid, who requested crafting, etc.
 */
public final class CraftingContext {

    public final World world;
    public final IGrid meGrid;
    public final ICraftingGrid craftingGrid;
    public BaseActionSource actionSource;

    /**
     * A working copy of the AE system's item list used for modelling what happens as crafting requests get resolved.
     * Only extract, inject into {@link CraftingContext#byproductsInventory}.
     */
    public final MECraftingInventory itemModel;
    /**
     * An initially blank inventory for keeping all crafting byproduct outputs in. Extract from here before extracting
     * from {@link CraftingContext#itemModel}.
     * <p>
     * It is separate from the item model, because at the end of the crafting calculation we must produce a list of
     * items to extract from the system. When a crafting process puts those items back into the modelled inventory, and
     * another process extract them the calculator would request withdrawing that byproduct directly from AE when
     * setting up the crafting CPU. This would be wrong, it should assume that the item will be produced during crafting
     * and does not need to be initially present in the system, which we achieve by separating byproducts.
     *
     * @see appeng.crafting.v2.resolvers.ExtractItemResolver ExtractItemResolver - separates the items extracted from AE
     *      vs from byproducts for the crafting plan
     */
    public final MECraftingInventory byproductsInventory;
    /**
     * A cache of how many items were present at the beginning of the crafting request, do not modify
     */
    public final MECraftingInventory availableCache;

    public boolean wasSimulated = false;

    public static final class RequestInProcessing<StackType extends IAEStack<StackType>> {

        public final CraftingRequest<StackType> request;
        /**
         * Ordered by priority
         */
        public final ArrayList<CraftingTask> resolvers = new ArrayList<>(4);

        public RequestInProcessing(CraftingRequest<StackType> request) {
            this.request = request;
        }
    }

    private final List<RequestInProcessing<?>> liveRequests = new ArrayList<>(32);
    private final List<CraftingTask> resolvedTasks = new ArrayList<>();
    private final ArrayDeque<CraftingTask> tasksToProcess = new ArrayDeque<>(64);
    private boolean doingWork = false;
    // State at the point when the last task executed.
    private CraftingTask.State finishedState = CraftingTask.State.FAILURE;
    private final ImmutableMap<IAEItemStack, ImmutableList<ICraftingPatternDetails>> availablePatterns;
    private final Map<IAEItemStack, List<ICraftingPatternDetails>> precisePatternCache = new HashMap<>();
    private final Map<ICraftingPatternDetails, IAEItemStack> crafterIconCache = new HashMap<>();
    private final OreListMultiMap<ICraftingPatternDetails> fuzzyPatternCache = new OreListMultiMap<>();
    private final IdentityHashMap<ICraftingPatternDetails, Boolean> isPatternComplexCache = new IdentityHashMap<>();
    private final ClassToInstanceMap<Object> userCaches = MutableClassToInstanceMap.create();

    public CraftingContext(@Nonnull World world, @Nonnull IGrid meGrid, @Nonnull BaseActionSource actionSource) {
        this.world = world;
        this.meGrid = meGrid;
        this.craftingGrid = meGrid.getCache(ICraftingGrid.class);
        this.actionSource = actionSource;
        final IStorageGrid sg = meGrid.getCache(IStorageGrid.class);
        this.itemModel = new MECraftingInventory(sg.getItemInventory(), this.actionSource, true, false, true);
        this.byproductsInventory = new MECraftingInventory();
        this.availableCache = new MECraftingInventory(sg.getItemInventory(), this.actionSource, false, false, false);
        this.availablePatterns = craftingGrid.getCraftingPatterns();
    }

    /**
     * Can be used for custom caching in plugins
     */
    public <T> T getUserCache(Class<T> cacheType, Supplier<T> constructor) {
        // Can't use compute with generic types safely here
        T instance = userCaches.getInstance(cacheType);
        if (instance == null) {
            instance = constructor.get();
            userCaches.putInstance(cacheType, instance);
        }
        return instance;
    }

    public void addRequest(@Nonnull CraftingRequest<?> request) {
        if (doingWork) {
            throw new IllegalStateException(
                    "Trying to add requests while inside a CraftingTask handler, return requests in the StepOutput instead");
        }
        final RequestInProcessing<?> processing = new RequestInProcessing<>(request);
        processing.resolvers.addAll(CraftingCalculations.tryResolveCraftingRequest(request, this));
        Collections.reverse(processing.resolvers); // We remove from the end for efficient ArrayList usage
        liveRequests.add(processing);
        if (processing.resolvers.isEmpty()) {
            throw new IllegalStateException("No resolvers available for request " + request.toString());
        }
        queueNextTaskOf(processing, true);
    }

    public IAEItemStack getCrafterIconForPattern(@Nonnull ICraftingPatternDetails pattern) {
        return crafterIconCache.computeIfAbsent(pattern, ignored -> {
            if (craftingGrid instanceof CraftingGridCache) {
                final List<ICraftingMedium> mediums = ((CraftingGridCache) craftingGrid).getMediums(pattern);
                for (ICraftingMedium medium : mediums) {
                    ItemStack stack = medium.getCrafterIcon();
                    if (stack != null) {
                        return AEItemStack.create(stack);
                    }
                }
            }
            return AEItemStack.create(AEApi.instance().definitions().blocks().iface().maybeStack(1).orNull());
        });
    }

    public List<ICraftingPatternDetails> getPrecisePatternsFor(@Nonnull IAEItemStack stack) {
        return precisePatternCache.compute(stack, (key, value) -> {
            if (value == null) {
                return availablePatterns.getOrDefault(stack, ImmutableList.of());
            } else {
                return value;
            }
        });
    }

    public List<ICraftingPatternDetails> getFuzzyPatternsFor(@Nonnull IAEItemStack stack) {
        if (!fuzzyPatternCache.isPopulated()) {
            for (final ImmutableList<ICraftingPatternDetails> patternSet : availablePatterns.values()) {
                for (final ICraftingPatternDetails pattern : patternSet) {
                    if (pattern.canBeSubstitute()) {
                        for (final IAEItemStack output : pattern.getOutputs()) {
                            fuzzyPatternCache.put(output.copy(), pattern);
                        }
                    }
                }
            }
            fuzzyPatternCache.freeze();
        }
        return fuzzyPatternCache.get(stack);
    }

    /**
     * @return Whether the pattern has complex behavior leaving items in the crafting grid, requiring 1-by-1 simulation
     *         using a fake player
     */
    public boolean isPatternComplex(@Nonnull ICraftingPatternDetails pattern) {
        if (!pattern.isCraftable()) {
            return false;
        }
        final Boolean cached = isPatternComplexCache.get(pattern);
        if (cached != null) {
            return cached;
        }

        final IAEItemStack[] inputs = pattern.getInputs();
        final IAEItemStack[] mcOutputs = simulateComplexCrafting(inputs, pattern);

        final boolean isComplex = Arrays.stream(mcOutputs).anyMatch(Objects::nonNull);
        isPatternComplexCache.put(pattern, isComplex);
        return isComplex;
    }

    /**
     * Simulates doing 1 craft with a crafting table.
     * 
     * @param inputSlots 3x3 crafting matrix contents
     * @return What remains in the 3x3 crafting matrix
     */
    public IAEItemStack[] simulateComplexCrafting(IAEItemStack[] inputSlots, ICraftingPatternDetails pattern) {
        if (inputSlots.length > 9) {
            throw new IllegalArgumentException(inputSlots.length + " slots supplied to a simulated crafting task");
        }
        final InventoryCrafting simulatedWorkbench = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < inputSlots.length; i++) {
            simulatedWorkbench.setInventorySlotContents(i, inputSlots[i] == null ? null : inputSlots[i].getItemStack());
        }
        if (world instanceof WorldServer) {
            FMLCommonHandler.instance().firePlayerCraftingEvent(
                    Platform.getPlayer((WorldServer) world),
                    pattern.getOutput(simulatedWorkbench, world),
                    simulatedWorkbench);
        }
        IAEItemStack[] output = new IAEItemStack[9];
        for (int i = 0; i < output.length; i++) {
            ItemStack mcOut = simulatedWorkbench.getStackInSlot(i);
            if (mcOut == null || mcOut.getItem() == null || mcOut.stackSize <= 0) {
                output[i] = null;
                continue;
            }
            Item item = mcOut.getItem();
            ItemStack container = Platform.getContainerItem(mcOut);
            if (container != null) {
                if (container.stackSize <= 0) {
                    output[i] = null;
                } else {
                    output[i] = AEItemStack.create(container);
                }
            } else {
                mcOut.stackSize -= 1;
                if (mcOut.stackSize <= 0) {
                    output[i] = null;
                } else {
                    output[i] = AEItemStack.create(mcOut);
                }
            }
        }
        return output;
    }

    /**
     * Does one unit of work towards solving the crafting problem.
     *
     * @return Is more work needed?
     */
    public CraftingTask.State doWork() {
        if (tasksToProcess.isEmpty()) {
            return finishedState;
        }
        // Limit number of crafting steps on dedicated servers to prevent abuse, in singleplayer the lag only affects
        // the player.
        if (FMLCommonHandler.instance().getSide() == Side.SERVER
                && resolvedTasks.size() > AEConfig.instance.maxCraftingSteps) {
            this.finishedState = State.FAILURE;
            for (CraftingTask task : tasksToProcess) {
                if (task.request != null) {
                    task.request.incomplete = true;
                }
            }
            throw new CraftingStepLimitExceeded();
        }
        final CraftingTask frontTask = tasksToProcess.getFirst();
        if (frontTask.getState() == CraftingTask.State.SUCCESS || frontTask.getState() == CraftingTask.State.FAILURE) {
            resolvedTasks.add(frontTask);
            tasksToProcess.removeFirst();
            return CraftingTask.State.NEEDS_MORE_WORK;
        }
        doingWork = true;
        CraftingTask.StepOutput out = frontTask.calculateOneStep(this);
        CraftingTask.State newState = frontTask.getState();
        doingWork = false;
        if (!out.extraInputsRequired.isEmpty()) {
            final Set<ICraftingPatternDetails> parentPatterns = frontTask.request.patternParents;
            // Last pushed gets resolved first, so iterate in reverse order to maintain array ordering
            for (int ri = out.extraInputsRequired.size() - 1; ri >= 0; ri--) {
                final CraftingRequest<?> request = out.extraInputsRequired.get(ri);
                request.patternParents.addAll(parentPatterns);
                this.addRequest(request);
            }
        } else if (newState == CraftingTask.State.SUCCESS) {
            if (tasksToProcess.getFirst() != frontTask) {
                throw new IllegalStateException("A crafting task got added to the queue without requesting more work.");
            }
            resolvedTasks.add(frontTask);
            tasksToProcess.removeFirst();
            finishedState = CraftingTask.State.SUCCESS;
        } else if (newState == CraftingTask.State.FAILURE) {
            tasksToProcess.clear();
            finishedState = CraftingTask.State.FAILURE;
            return CraftingTask.State.FAILURE;
        }
        return tasksToProcess.isEmpty() ? CraftingTask.State.SUCCESS : CraftingTask.State.NEEDS_MORE_WORK;
    }

    /**
     * Gets the list of tasks that have finished executing, sorted topologically (dependencies before the tasks that
     * require them)
     * 
     * @return An unmodifiable list of resolved tasks.
     */
    public List<CraftingTask> getResolvedTasks() {
        return Collections.unmodifiableList(resolvedTasks);
    }

    /**
     * Gets all requests that have been added to the context.
     * 
     * @return An unmodifiable list of the requests.
     */
    public List<RequestInProcessing<?>> getLiveRequests() {
        return Collections.unmodifiableList(liveRequests);
    }

    @Override
    public String toString() {
        final Set<CraftingTask<?>> processed = Collections.newSetFromMap(new IdentityHashMap<>());
        return getResolvedTasks().stream().map(rt -> {
            boolean isNew = processed.add(rt);
            return (isNew ? "  " : "  [duplicate] ") + rt.toString();
        }).collect(Collectors.joining("\n"));
    }

    /**
     * @return If a task was added
     */
    private boolean queueNextTaskOf(RequestInProcessing<?> request, boolean addResolverTask) {
        if (request.request.remainingToProcess <= 0 || request.resolvers.isEmpty()) {
            return false;
        }
        CraftingTask nextResolver = request.resolvers.remove(request.resolvers.size() - 1);
        if (addResolverTask && !request.resolvers.isEmpty()) {
            tasksToProcess.addFirst(new CheckOtherResolversTask(request));
        }
        if (request.resolvers.isEmpty()) {
            request.resolvers.trimToSize();
        }
        tasksToProcess.addFirst(nextResolver);
        return true;
    }

    /**
     * A task to call queueNextTaskOf after a resolver gets computed to check if more resolving is needed for the same
     * request-in-processing.
     */
    private final class CheckOtherResolversTask<T extends IAEStack<T>> extends CraftingTask<T> {

        private final RequestInProcessing<?> myRequest;

        public CheckOtherResolversTask(RequestInProcessing<T> myRequest) {
            super(myRequest.request, 0); // priority doesn't matter as this task is never a resolver output
            this.myRequest = myRequest;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            final boolean needsMoreWork = queueNextTaskOf(myRequest, false);
            if (needsMoreWork) {
                this.state = State.NEEDS_MORE_WORK;
            } else if (myRequest.request.remainingToProcess <= 0) {
                this.state = State.SUCCESS;
            } else {
                this.state = State.FAILURE;
            }
            return new StepOutput();
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            // no-op, does not produce/consume any stacks
            return 0;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            // no-op
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            // no-op
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            // no-op
        }

        @Override
        public String toString() {
            return "CheckOtherResolversTask{" + "myRequest="
                    + myRequest
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }
}
