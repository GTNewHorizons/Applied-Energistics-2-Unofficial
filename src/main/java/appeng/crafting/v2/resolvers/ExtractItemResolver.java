package appeng.crafting.v2.resolvers;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.*;
import javax.annotation.Nonnull;

public class ExtractItemResolver implements CraftingRequestResolver<IAEItemStack> {
    public static class ExtractItemTask extends CraftingTask {
        public final CraftingRequest<IAEItemStack> request;
        public final List<IAEItemStack> removedFromSystem = new ArrayList<>();

        public ExtractItemTask(CraftingRequest<IAEItemStack> request) {
            super(CraftingTask.PRIORITY_EXTRACT); // always try to extract items first
            this.request = request;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Extract exact
            IAEItemStack exactMatching = context.itemModel.getItemList().findPrecise(request.stack);
            if (exactMatching != null) {
                final long requestSize = Math.min(request.remainingToProcess, exactMatching.getStackSize());
                final IAEItemStack extracted = context.itemModel.extractItems(
                        exactMatching.copy().setStackSize(requestSize), Actionable.MODULATE, context.actionSource);
                if (extracted != null && extracted.getStackSize() > 0) {
                    request.fulfill(this, extracted, context);
                    removedFromSystem.add(extracted.copy());
                }
            }
            // Extract fuzzy
            if (request.remainingToProcess > 0
                    && request.substitutionMode == CraftingRequest.SubstitutionMode.ACCEPT_FUZZY) {
                Collection<IAEItemStack> fuzzyMatching =
                        context.itemModel.getItemList().findFuzzy(request.stack, FuzzyMode.IGNORE_ALL);
                for (final IAEItemStack candidate : fuzzyMatching) {
                    if (candidate == null) {
                        continue;
                    }
                    if (request.acceptableSubstituteFn.test(candidate)) {
                        final long requestSize = Math.min(request.remainingToProcess, candidate.getStackSize());
                        final IAEItemStack extracted = context.itemModel.extractItems(
                                candidate.copy().setStackSize(requestSize), Actionable.MODULATE, context.actionSource);
                        if (extracted == null || extracted.getStackSize() <= 0) {
                            continue;
                        }
                        request.fulfill(this, extracted, context);
                        removedFromSystem.add(extracted.copy());
                    }
                }
            }
            return new StepOutput(Collections.emptyList());
        }

        @Override
        public void partialRefund(CraftingContext context, long amount) {
            // Remove fuzzy things first
            Collections.reverse(removedFromSystem);
            final Iterator<IAEItemStack> removedIt = removedFromSystem.iterator();
            while (removedIt.hasNext() && amount > 0) {
                final IAEItemStack available = removedIt.next();
                final long availAmount = available.getStackSize();
                if (availAmount > amount) {
                    context.itemModel.injectItems(
                            available.copy().setStackSize(amount), Actionable.MODULATE, context.actionSource);
                    available.setStackSize(availAmount - amount);
                    amount = 0;
                } else {
                    context.itemModel.injectItems(available, Actionable.MODULATE, context.actionSource);
                    amount -= availAmount;
                    removedIt.remove();
                }
            }
            Collections.reverse(removedFromSystem);
        }

        @Override
        public void fullRefund(CraftingContext context) {
            for (IAEItemStack removed : removedFromSystem) {
                context.itemModel.injectItems(removed, Actionable.MODULATE, context.actionSource);
            }
            removedFromSystem.clear();
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            for (IAEItemStack removed : removedFromSystem) {
                targetPlan.add(removed.copy());
            }
        }

        @Override
        public void startOnCpu(
                CraftingContext context, CraftingCPUCluster cpuCluster, MECraftingInventory craftingInv) {
            for (IAEItemStack stack : removedFromSystem) {
                if (stack.getStackSize() > 0) {
                    IAEItemStack extracted = craftingInv.extractItems(stack, Actionable.MODULATE, context.actionSource);
                    if (extracted == null || extracted.getStackSize() != stack.getStackSize()) {
                        throw new CraftBranchFailure(stack, stack.getStackSize());
                    }
                    cpuCluster.addStorage(extracted);
                }
            }
        }

        @Override
        public String toString() {
            return "ExtractItemTask{" + "request="
                    + request + ", removedFromSystem="
                    + removedFromSystem + ", priority="
                    + priority + ", state="
                    + state + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(
            @Nonnull CraftingRequest<IAEItemStack> request, @Nonnull CraftingContext context) {
        if (request.substitutionMode == CraftingRequest.SubstitutionMode.PRECISE_FRESH) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new ExtractItemTask(request));
        }
    }
}
