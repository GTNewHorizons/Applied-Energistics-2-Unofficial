package appeng.crafting.v2.resolvers;

import java.util.*;

import javax.annotation.Nonnull;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class ExtractItemResolver implements CraftingRequestResolver<IAEItemStack> {

    public static class ExtractItemTask extends CraftingTask<IAEItemStack> {

        public final ArrayList<IAEItemStack> removedFromSystem = new ArrayList<>();
        public final ArrayList<IAEItemStack> removedFromByproducts = new ArrayList<>();

        public ExtractItemTask(CraftingRequest<IAEItemStack> request) {
            super(request, CraftingTask.PRIORITY_EXTRACT); // always try to extract items first
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            extractExact(context, context.byproductsInventory, removedFromByproducts);
            if (request.remainingToProcess > 0) {
                extractExact(context, context.itemModel, removedFromSystem);
            }
            if (request.remainingToProcess > 0
                    && request.substitutionMode == CraftingRequest.SubstitutionMode.ACCEPT_FUZZY) {
                extractFuzzy(context, context.byproductsInventory, removedFromByproducts);
                if (request.remainingToProcess > 0) {
                    extractFuzzy(context, context.itemModel, removedFromSystem);
                }
            }
            removedFromSystem.trimToSize();
            removedFromByproducts.trimToSize();
            return new StepOutput(Collections.emptyList());
        }

        private void extractExact(CraftingContext context, MECraftingInventory source, List<IAEItemStack> removedList) {
            IAEItemStack exactMatching = source.getItemList().findPrecise(request.stack);
            if (exactMatching != null) {
                final long requestSize = Math.min(request.remainingToProcess, exactMatching.getStackSize());
                final IAEItemStack extracted = source.extractItems(
                        exactMatching.copy().setStackSize(requestSize),
                        Actionable.MODULATE,
                        context.actionSource);
                if (extracted != null && extracted.getStackSize() > 0) {
                    request.fulfill(this, extracted, context);
                    removedList.add(extracted.copy());
                }
            }
        }

        private void extractFuzzy(CraftingContext context, MECraftingInventory source, List<IAEItemStack> removedList) {
            Collection<IAEItemStack> fuzzyMatching = source.getItemList()
                    .findFuzzy(request.stack, FuzzyMode.IGNORE_ALL);
            for (final IAEItemStack candidate : fuzzyMatching) {
                if (candidate == null) {
                    continue;
                }
                if (request.acceptableSubstituteFn.test(candidate)) {
                    final long requestSize = Math.min(request.remainingToProcess, candidate.getStackSize());
                    final IAEItemStack extracted = source.extractItems(
                            candidate.copy().setStackSize(requestSize),
                            Actionable.MODULATE,
                            context.actionSource);
                    if (extracted == null || extracted.getStackSize() <= 0) {
                        continue;
                    }
                    request.fulfill(this, extracted, context);
                    removedList.add(extracted.copy());
                }
            }
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            final long originalAmount = amount;
            // Remove fuzzy things first
            Collections.reverse(removedFromSystem);
            Collections.reverse(removedFromByproducts);
            amount = partialRefundFrom(context, amount, removedFromSystem, context.itemModel);
            amount = partialRefundFrom(context, amount, removedFromByproducts, context.byproductsInventory);
            Collections.reverse(removedFromSystem);
            Collections.reverse(removedFromByproducts);
            return originalAmount - amount;
        }

        private long partialRefundFrom(CraftingContext context, long amount, List<IAEItemStack> source,
                MECraftingInventory target) {
            final Iterator<IAEItemStack> removedIt = source.iterator();
            while (removedIt.hasNext() && amount > 0) {
                final IAEItemStack available = removedIt.next();
                final long availAmount = available.getStackSize();
                if (availAmount > amount) {
                    target.injectItems(
                            available.copy().setStackSize(amount),
                            Actionable.MODULATE,
                            context.actionSource);
                    available.setStackSize(availAmount - amount);
                    amount = 0;
                } else {
                    target.injectItems(available, Actionable.MODULATE, context.actionSource);
                    amount -= availAmount;
                    removedIt.remove();
                }
            }
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            for (IAEItemStack removed : removedFromByproducts) {
                context.byproductsInventory.injectItems(removed, Actionable.MODULATE, context.actionSource);
            }
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
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
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
                    + request
                    + ", removedFromSystem="
                    + removedFromSystem
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<IAEItemStack> request,
            @Nonnull CraftingContext context) {
        if (request.substitutionMode == CraftingRequest.SubstitutionMode.PRECISE_FRESH) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new ExtractItemTask(request));
        }
    }
}
