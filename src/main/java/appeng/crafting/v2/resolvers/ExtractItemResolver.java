package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.GuiText;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;

public class ExtractItemResolver implements CraftingRequestResolver {

    public static class ExtractItemTask<StackType extends IAEStack<StackType>> extends CraftingTask {

        public final ArrayList<IAEStack<?>> removedFromSystem = new ArrayList<>();
        public final ArrayList<IAEStack<?>> removedFromByproducts = new ArrayList<>();

        public ExtractItemTask(CraftingRequest request) {
            super(request, CraftingTask.PRIORITY_EXTRACT); // always try to extract items first
        }

        @SuppressWarnings("unused")
        public ExtractItemTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            serializer.readList(removedFromSystem, serializer::readStack);
            serializer.readList(removedFromByproducts, serializer::readStack);
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            serializer.writeList(removedFromSystem, serializer::writeStack);
            serializer.writeList(removedFromByproducts, serializer::writeStack);
            return Collections.emptyList();
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {}

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

        private void extractExact(CraftingContext context, MECraftingInventory source, List<IAEStack<?>> removedList) {
            StackType exactMatching = source.extractItems((StackType) request.stack, Actionable.SIMULATE);
            if (exactMatching != null) {
                final long requestSize = Math.min(request.remainingToProcess, exactMatching.getStackSize());
                final StackType extracted = source
                        .extractItems(exactMatching.copy().setStackSize(requestSize), Actionable.MODULATE);
                if (extracted != null && extracted.getStackSize() > 0) {
                    extracted.setCraftable(false);
                    request.fulfill(this, extracted, context);
                    removedList.add(extracted.copy());
                }
            }
        }

        private void extractFuzzy(CraftingContext context, MECraftingInventory source, List<IAEStack<?>> removedList) {
            Collection<StackType> fuzzyMatching = source.findFuzzy((StackType) request.stack, FuzzyMode.IGNORE_ALL);
            for (final StackType candidate : fuzzyMatching) {
                if (candidate == null) {
                    continue;
                }
                if (request.acceptableSubstituteFn.test(candidate)) {
                    final long requestSize = Math.min(request.remainingToProcess, candidate.getStackSize());
                    final StackType extracted = source
                            .extractItems(candidate.copy().setStackSize(requestSize), Actionable.MODULATE);
                    if (extracted == null || extracted.getStackSize() <= 0) {
                        continue;
                    }
                    extracted.setCraftable(false);
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

        private long partialRefundFrom(CraftingContext context, long amount, List<IAEStack<?>> source,
                MECraftingInventory target) {
            final Iterator<IAEStack<?>> removedIt = source.iterator();
            while (removedIt.hasNext() && amount > 0) {
                final IAEStack<?> available = removedIt.next();
                final long availAmount = available.getStackSize();
                if (availAmount > amount) {
                    target.injectItems(available.copy().setStackSize(amount), Actionable.MODULATE);
                    available.setStackSize(availAmount - amount);
                    amount = 0;
                } else {
                    target.injectItems(available, Actionable.MODULATE);
                    amount -= availAmount;
                    removedIt.remove();
                }
            }
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            for (IAEStack<?> removed : removedFromByproducts) {
                context.byproductsInventory.injectItems(removed, Actionable.MODULATE);
            }
            for (IAEStack<?> removed : removedFromSystem) {
                context.itemModel.injectItems(removed, Actionable.MODULATE);
            }
            removedFromSystem.clear();
        }

        @Override
        public void populatePlan(IItemList<IAEStack<?>> targetPlan) {
            for (IAEStack<?> removed : removedFromSystem) {
                targetPlan.add(removed.copy());
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            for (IAEStack stack : removedFromSystem) {
                if (stack.getStackSize() > 0) {
                    IAEStack<?> extracted = craftingInv.extractItems(stack, Actionable.MODULATE);
                    if (extracted == null || extracted.getStackSize() != stack.getStackSize()) {
                        if (cpuCluster.isMissingMode()) {
                            if (extracted == null) {
                                cpuCluster.addEmitable(stack.copy());
                                stack.setStackSize(0);
                                continue;
                            } else if (extracted.getStackSize() != stack.getStackSize()) {
                                cpuCluster.addEmitable(
                                        stack.copy().setStackSize(stack.getStackSize() - extracted.getStackSize()));
                                stack.setStackSize(extracted.getStackSize());
                            }
                        } else {
                            throw new CraftBranchFailure(stack, stack.getStackSize());
                        }
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

        @Override
        public String getTooltipText() {
            long removedCount = 0, removedTypes = 0;
            final StringBuilder itemList = new StringBuilder();
            for (IAEStack<?> stack : removedFromSystem) {
                if (stack != null) {
                    removedCount += stack.getStackSize();
                    removedTypes++;
                    itemList.append("\n ");
                    itemList.append(stack);
                    itemList.append(" (");
                    itemList.append(Platform.getItemDisplayName(stack));
                    itemList.append(')');
                }
            }
            for (IAEStack<?> stack : removedFromByproducts) {
                if (stack != null) {
                    removedCount += stack.getStackSize();
                    removedTypes++;
                    itemList.append("\n ");
                    itemList.append(stack);
                    itemList.append(" (");
                    itemList.append(Platform.getItemDisplayName(stack));
                    itemList.append(')');
                }
            }

            return GuiText.StoredItems.getLocal() + ": "
                    + removedCount
                    + "\n "
                    + GuiText.StoredStacks.getLocal()
                    + ": "
                    + removedTypes
                    + itemList;
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest request,
            @Nonnull CraftingContext context) {
        if (request.substitutionMode == CraftingRequest.SubstitutionMode.PRECISE_FRESH) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new ExtractItemTask(request));
        }
    }
}
