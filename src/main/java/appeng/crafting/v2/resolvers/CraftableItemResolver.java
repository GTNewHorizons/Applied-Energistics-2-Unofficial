package appeng.crafting.v2.resolvers;

import java.util.*;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import net.minecraft.world.World;

import org.apache.logging.log4j.Level;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.HashBasedItemList;

import com.google.common.collect.ImmutableList;

public class CraftableItemResolver implements CraftingRequestResolver<IAEItemStack> {

    public static class RequestAndPerCraftAmount {

        public final CraftingRequest<IAEItemStack> request;
        public final long perCraftAmount;

        public RequestAndPerCraftAmount(CraftingRequest<IAEItemStack> request, long perCraftAmount) {
            this.request = request;
            this.perCraftAmount = perCraftAmount;
        }
    }

    public static class CraftFromPatternTask extends CraftingTask<IAEItemStack> {

        public final ICraftingPatternDetails pattern;
        public final boolean allowSimulation;
        public final boolean isComplex;
        // Inputs needed to kickstart recursive crafting
        protected final IAEItemStack[] patternRecursionInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternOutputs;
        protected final IAEItemStack matchingOutput;
        protected final ArrayList<RequestAndPerCraftAmount> childRequests = new ArrayList<>();
        protected final ArrayList<CraftingRequest> complexRequestPerSlot = new ArrayList<>();
        protected final Map<IAEItemStack, CraftingRequest<IAEItemStack>> childRecursionRequests = new HashMap<>();
        // byproduct injected -> amount per craft
        protected final IdentityHashMap<IAEItemStack, Long> byproducts = new IdentityHashMap<>();
        protected boolean requestedInputs = false;
        protected long totalCraftsDone = 0, fulfilledAmount = 0;
        /**
         * If matchingOutput's stack size is greater than 1, this keeps track of how many remainder items were injected
         * back into the context.
         */
        protected long matchingOutputRemainderItems = 0;

        public CraftFromPatternTask(CraftingRequest<IAEItemStack> request, ICraftingPatternDetails pattern,
                int priority, boolean allowSimulation, boolean isComplex) {
            super(request, priority);
            this.pattern = pattern;
            this.allowSimulation = allowSimulation;
            this.isComplex = isComplex;

            HashBasedItemList pInputs = new HashBasedItemList();
            HashBasedItemList pOutputs = new HashBasedItemList();
            HashBasedItemList pRecInputs = new HashBasedItemList();
            Arrays.stream(pattern.getInputs()).filter(Objects::nonNull).forEach(pInputs::add);
            Arrays.stream(pattern.getOutputs()).filter(Objects::nonNull).forEach(pOutputs::add);
            for (IAEItemStack output : pOutputs) {
                IAEItemStack input = pInputs.findPrecise(output);
                if (input != null) {
                    final long netProduced = output.getStackSize() - input.getStackSize();
                    if (netProduced > 0) {
                        pRecInputs.add(input);
                        input.setStackSize(0);
                        output.setStackSize(netProduced);
                    } else {
                        // Ensure recInput.stackSize + input.stackSize == original input.stackSize
                        pRecInputs.add(input.copy().setStackSize(input.getStackSize() + netProduced));
                        input.setStackSize(-netProduced);
                        output.setStackSize(0);
                    }
                }
            }
            this.patternInputs = pInputs.toArray(new IAEItemStack[0]);
            this.patternOutputs = pOutputs.toArray(new IAEItemStack[0]);
            this.patternRecursionInputs = pRecInputs.toArray(new IAEItemStack[0]);
            IAEItemStack foundMatchingOutput = null;
            for (final IAEItemStack patternOutput : patternOutputs) {
                if (isOutputSameAs(patternOutput)) {
                    foundMatchingOutput = patternOutput;
                    break;
                }
            }
            if (foundMatchingOutput == null) {
                state = State.FAILURE;
                throw new IllegalStateException("Invalid pattern crafting step for " + request);
            }
            this.matchingOutput = foundMatchingOutput;
        }

        public boolean isOutputSameAs(IAEItemStack otherStack) {
            if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
                return this.request.stack.fuzzyComparison(otherStack, FuzzyMode.IGNORE_ALL);
            } else {
                return this.request.stack.isSameType(otherStack);
            }
        }

        public boolean isValidSubstitute(IAEItemStack reference, IAEItemStack stack, World world) {
            if (!pattern.isCraftable()) {
                return true;
            }
            IAEItemStack[] rawInputs = pattern.getInputs();
            for (int slot = 0; slot < rawInputs.length; slot++) {
                if (rawInputs[slot] != null && rawInputs[slot].isSameType(reference)) {
                    return pattern.isValidItemForSlot(slot, stack.getItemStack(), world);
                }
            }
            return true;
        }

        public boolean isValidSubstitute(IAEItemStack reference, IAEItemStack stack, World world, int slot) {
            if (!pattern.isCraftable()) {
                return true;
            }
            IAEItemStack[] rawInputs = pattern.getInputs();
            return pattern.isValidItemForSlot(slot, stack.getItemStack(), world);
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            }
            final boolean canUseSubstitutes = pattern.canSubstitute();
            final SubstitutionMode childMode = canUseSubstitutes ? SubstitutionMode.ACCEPT_FUZZY
                    : SubstitutionMode.PRECISE;
            final long toCraft = Platform
                    .ceilDiv(isComplex ? 1 : request.remainingToProcess, matchingOutput.getStackSize());

            if (requestedInputs) {
                // Calculate how many full recipes we could fulfill
                long maxCraftable = toCraft;
                for (CraftingRequest<IAEItemStack> recInputChild : childRecursionRequests.values()) {
                    if (recInputChild.remainingToProcess > 0) {
                        // If we can't resolve an input to the recursive process, we can't craft anything at all
                        maxCraftable = 0;
                    }
                }
                for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                    final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                    final long costPerRecipe = inputChild.stack.getStackSize() / toCraft;
                    final long available = inputChild.stack.getStackSize() - inputChild.remainingToProcess;
                    final long fullRecipes = available / costPerRecipe;
                    maxCraftable = Math.min(maxCraftable, fullRecipes);
                }
                final long producedMatchingOutput = Math.multiplyExact(maxCraftable, matchingOutput.getStackSize());
                this.matchingOutputRemainderItems = (producedMatchingOutput > this.request.remainingToProcess)
                        ? (producedMatchingOutput - this.request.remainingToProcess)
                        : 0;
                this.fulfilledAmount = producedMatchingOutput - matchingOutputRemainderItems;
                // Fulfill those recipes
                request.fulfill(this, matchingOutput.copy().setStackSize(fulfilledAmount), context);
                // Add remainder
                if (matchingOutputRemainderItems > 0) {
                    context.byproductsInventory.injectItems(
                            matchingOutput.copy().setStackSize(matchingOutputRemainderItems),
                            Actionable.MODULATE,
                            context.actionSource);
                }
                // Add byproducts of complex recipes
                if (isComplex && fulfilledAmount > 0) {
                    if (maxCraftable > 1) {
                        throw new IllegalStateException(
                                "Complex recipe got calculated with more than 1 set of inputs at a time");
                    }
                    final IAEItemStack[] inputs = new IAEItemStack[9];
                    for (int slot = 0; slot < complexRequestPerSlot.size(); slot++) {
                        final CraftingRequest<IAEItemStack> slotRequest = complexRequestPerSlot.get(slot);
                        if (slotRequest != null) {
                            final IAEItemStack resolvedItem = (IAEItemStack) slotRequest.getOneResolvedType();
                            inputs[slot] = resolvedItem.copy();
                        }
                    }
                    final IAEItemStack[] leftovers = context.simulateComplexCrafting(inputs, pattern);

                    for (IAEItemStack leftover : leftovers) {
                        if (leftover == null || leftover.getStackSize() <= 0) {
                            continue;
                        }
                        context.byproductsInventory.injectItems(leftover, Actionable.MODULATE, context.actionSource);
                        this.byproducts.put(leftover.copy(), leftover.getStackSize());
                    }
                }
                for (IAEItemStack output : patternOutputs) {
                    // add byproducts to the system
                    if (output != matchingOutput) {
                        final IAEItemStack injected = output.copy()
                                .setStackSize(Math.multiplyExact(maxCraftable, output.getStackSize()));
                        context.byproductsInventory.injectItems(injected, Actionable.MODULATE, context.actionSource);
                        this.byproducts.put(injected.copy(), output.getStackSize());
                    }
                }
                this.totalCraftsDone = maxCraftable;
                if (maxCraftable != toCraft) {
                    // Need to refund some items as not everything could be crafted.
                    for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                        final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                        final long actuallyNeeded = Math
                                .multiplyExact(inputChild.stack.getStackSize() / toCraft, maxCraftable);
                        final long produced = inputChild.stack.getStackSize()
                                - Math.max(inputChild.remainingToProcess, 0);
                        if (produced > actuallyNeeded) {
                            if (maxCraftable == 0) {
                                inputChild.fullRefund(context);
                            } else {
                                inputChild.partialRefund(context, produced - actuallyNeeded);
                            }
                        }
                    }
                    // If we couldn't craft even a single recipe, refund recursive inputs too
                    if (maxCraftable == 0) {
                        for (CraftingRequest<IAEItemStack> recChild : childRecursionRequests.values()) {
                            recChild.fullRefund(context);
                        }
                    }
                }
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            } else {
                request.patternParents.add(this.pattern);
                ArrayList<CraftingRequest<IAEItemStack>> newChildren = new ArrayList<>(patternInputs.length);
                if (isComplex) {
                    if (toCraft > 1) {
                        throw new IllegalStateException();
                    }
                    final IAEItemStack[] slotInputs = pattern.getInputs();
                    for (int slot = 0; slot < slotInputs.length; slot++) {
                        final IAEItemStack input = slotInputs[slot];
                        if (input == null) {
                            complexRequestPerSlot.add(null);
                            continue;
                        }
                        final long amount = Math.multiplyExact(input.getStackSize(), toCraft);
                        final int finalSlot = slot; // for lambda capture
                        CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                input.copy().setStackSize(amount),
                                childMode,
                                IAEItemStack.class,
                                allowSimulation,
                                stack -> this.isValidSubstitute(input, stack, context.world, finalSlot));
                        complexRequestPerSlot.add(req);
                        newChildren.add(req);
                        childRequests.add(new RequestAndPerCraftAmount(req, input.getStackSize()));
                    }
                    // Try to fulfill container items (like GT tools) last to prevent them being frozen while other
                    // ingredients are resolved
                    newChildren.sort(
                            Comparator
                                    .<CraftingRequest<IAEItemStack>>comparingInt(
                                            r -> r.stack.getItem().hasContainerItem(r.stack.getItemStack()) ? 1 : 0)
                                    .thenComparingInt(
                                            r -> r.stack.getItem().getItemStackLimit(r.stack.getItemStack()) == 1 ? 1
                                                    : 0));
                } else {
                    if (patternRecursionInputs.length > 0) {
                        for (IAEItemStack recInput : patternRecursionInputs) {
                            CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                    recInput.copy(),
                                    childMode,
                                    IAEItemStack.class,
                                    allowSimulation,
                                    stack -> this.isValidSubstitute(recInput, stack, context.world));
                            newChildren.add(req);
                            childRecursionRequests.put(recInput, req);
                        }
                        state = State.NEEDS_MORE_WORK;
                    }
                    for (IAEItemStack input : patternInputs) {
                        final long amount = Math.multiplyExact(input.getStackSize(), toCraft);
                        CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                input.copy().setStackSize(amount),
                                childMode,
                                IAEItemStack.class,
                                allowSimulation,
                                stack -> this.isValidSubstitute(input, stack, context.world));
                        newChildren.add(req);
                        childRequests.add(new RequestAndPerCraftAmount(req, input.getStackSize()));
                    }
                }
                childRequests.trimToSize();
                complexRequestPerSlot.trimToSize();
                requestedInputs = true;
                state = State.NEEDS_MORE_WORK;
                return new StepOutput(Collections.unmodifiableList(newChildren));
            }
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            final long oldTotalCrafts = this.totalCraftsDone;
            final long oldTotalMade = this.totalCraftsDone * this.matchingOutput.getStackSize();
            final long oldFulfilled = this.fulfilledAmount;
            final long newFulfilled = oldFulfilled - amount;
            final long newTotalCrafts = Platform.ceilDiv(newFulfilled, this.matchingOutput.getStackSize());
            final long newTotalMade = newTotalCrafts * this.matchingOutput.getStackSize();
            final long oldRemainder = this.matchingOutputRemainderItems;
            final long newRemainder = newTotalMade - newFulfilled;
            if (newRemainder < 0 || newRemainder > this.matchingOutput.getStackSize()) {
                throw new IllegalStateException("Refund remainder invariant broken: " + newRemainder + " - " + this);
            }
            if (newTotalCrafts <= 0) {
                fullRefund(context);
                return amount;
            }
            if (newRemainder != oldRemainder) {
                if (newRemainder > oldRemainder) {
                    context.byproductsInventory.injectItems(
                            matchingOutput.copy().setStackSize(newRemainder - oldRemainder),
                            Actionable.MODULATE,
                            context.actionSource);
                } else {
                    context.byproductsInventory.extractItems(
                            matchingOutput.copy().setStackSize(oldRemainder - newRemainder),
                            Actionable.MODULATE,
                            context.actionSource);
                }
                this.matchingOutputRemainderItems = newRemainder;
            }
            if (newTotalCrafts != oldTotalCrafts) {
                if (newTotalCrafts > oldTotalCrafts) {
                    throw new IllegalStateException(
                            "Refund total crafts invariant broken: " + newTotalCrafts + " - " + this);
                }
                this.totalCraftsDone = newTotalCrafts;
                final long craftsRefunded = oldTotalCrafts - newTotalCrafts;
                for (RequestAndPerCraftAmount subrequest : childRequests) {
                    subrequest.request.partialRefund(context, subrequest.perCraftAmount * craftsRefunded);
                }
                for (Entry<IAEItemStack, Long> entry : byproducts.entrySet()) {
                    final IAEItemStack byproductStack = entry.getKey();
                    final long perCraft = entry.getValue();
                    context.byproductsInventory.extractItems(
                            byproductStack.copy().setStackSize(perCraft * craftsRefunded),
                            Actionable.MODULATE,
                            context.actionSource);
                    entry.getKey().setStackSize(byproductStack.getStackSize() - craftsRefunded * perCraft);
                }
            }
            this.fulfilledAmount = newFulfilled;
            return oldFulfilled - newFulfilled;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            request.patternParents.remove(this.pattern);
            totalCraftsDone = 0;
            fulfilledAmount = 0;
            childRequests.forEach(req -> req.request.fullRefund(context));
            childRequests.clear();
            childRecursionRequests.values().forEach(req -> req.fullRefund(context));
            childRecursionRequests.clear();
            // extract all byproducts because they are no longer produced
            for (IAEItemStack byproduct : byproducts.keySet()) {
                context.byproductsInventory.extractItems(byproduct.copy(), Actionable.MODULATE, context.actionSource);
            }
            byproducts.clear();
            if (this.matchingOutputRemainderItems > 0) {
                context.byproductsInventory.extractItems(
                        matchingOutput.copy().setStackSize(matchingOutputRemainderItems),
                        Actionable.MODULATE,
                        context.actionSource);
                this.matchingOutputRemainderItems = 0;
            }
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            if (totalCraftsDone == 0) {
                return;
            }
            for (IAEItemStack output : patternOutputs) {
                targetPlan.addRequestable(
                        output.copy().setStackSize(0).setCountRequestable(output.getStackSize() * totalCraftsDone));
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            cpuCluster.addCrafting(pattern, totalCraftsDone);
        }

        @Override
        public String toString() {
            return "CraftFromPatternTask{" + "request="
                    + request
                    + ", pattern="
                    + pattern
                    + ", allowSimulation="
                    + allowSimulation
                    + ", matchingOutput="
                    + matchingOutput
                    + ", requestedInputs="
                    + requestedInputs
                    + ", totalCraftsDone="
                    + totalCraftsDone
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }

    private void logComplexPattrn(ICraftingPatternDetails pattern, long count) {
        if (AEConfig.instance != null && AEConfig.instance.isFeatureEnabled(AEFeature.ComplexPatternLog)) {
            StringBuilder outputs = new StringBuilder();
            for (IAEItemStack stack : pattern.getOutputs()) {
                if (stack != null) {
                    outputs.append(stack);
                    if (stack instanceof AEItemStack) {
                        outputs.append(" <");
                        try {
                            outputs.append(((AEItemStack) stack).getDisplayName());
                        } catch (Exception e) {
                            outputs.append("? " + e.getMessage());
                        }
                        outputs.append('>');
                    }
                    outputs.append(", ");
                }
            }
            AELog.log(Level.INFO, "Complex crafting pattern found: %d * %s", count, outputs);
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<IAEItemStack> request,
            @Nonnull CraftingContext context) {
        final ImmutableList.Builder<CraftingTask> tasks = new ImmutableList.Builder<>();
        final Set<ICraftingPatternDetails> denyList = request.patternParents;
        final List<ICraftingPatternDetails> patterns = new ArrayList<>(context.getPrecisePatternsFor(request.stack));
        patterns.removeAll(denyList);
        patterns.sort(Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
        // If fuzzy patterns are allowed,
        if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            final List<ICraftingPatternDetails> fuzzyPatterns = new ArrayList<>(
                    context.getFuzzyPatternsFor(request.stack));
            fuzzyPatterns.removeAll(denyList);
            fuzzyPatterns.sort(Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
            patterns.addAll(fuzzyPatterns);
        }
        int priority = CraftingTask.PRIORITY_CRAFT_OFFSET + patterns.size() - 1;
        for (ICraftingPatternDetails pattern : patterns) {
            if (context.isPatternComplex(pattern)) {
                logComplexPattrn(pattern, request.remainingToProcess);
                for (int i = 0; i < request.remainingToProcess; i++) {
                    tasks.add(new CraftFromPatternTask(request, pattern, priority, false, true));
                }
            } else {
                tasks.add(new CraftFromPatternTask(request, pattern, priority, false, false));
            }
            priority--;
        }
        // Fallback: use highest priority pattern to simulate if nothing else works
        if (!patterns.isEmpty()) {
            ICraftingPatternDetails pattern = patterns.get(0);
            if (context.isPatternComplex(pattern)) {
                for (int i = 0; i < request.remainingToProcess; i++) {
                    tasks.add(new CraftFromPatternTask(request, pattern, priority, true, true));
                }
            } else {
                tasks.add(
                        new CraftFromPatternTask(request, pattern, CraftingTask.PRIORITY_SIMULATE_CRAFT, true, false));
            }
        }
        return tasks.build();
    }
}
