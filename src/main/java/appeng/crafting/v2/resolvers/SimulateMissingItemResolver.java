package appeng.crafting.v2.resolvers;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class SimulateMissingItemResolver<StackType extends IAEStack<StackType>>
        implements CraftingRequestResolver<StackType> {

    public static class ConjureItemTask<StackType extends IAEStack<StackType>> extends CraftingTask<StackType> {

        private long fulfilled = 0;

        public ConjureItemTask(CraftingRequest<StackType> request) {
            super(request, CraftingTask.PRIORITY_SIMULATE); // conjure items for calculations out of thin air as a last
                                                            // resort
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Simulate items existing
            request.wasSimulated = true;
            context.wasSimulated = true;
            fulfilled = request.remainingToProcess;
            request.fulfill(this, request.stack.copy().setStackSize(request.remainingToProcess), context);
            return new StepOutput(Collections.emptyList());
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            if (amount > fulfilled) {
                amount = fulfilled;
            }
            fulfilled -= amount;
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            fulfilled = 0;
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            if (fulfilled > 0 && request.stack instanceof IAEItemStack) {
                targetPlan.add((IAEItemStack) request.stack.copy().setStackSize(fulfilled));
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            throw new IllegalStateException("Trying to start crafting a schedule with simulated items");
        }

        @Override
        public String toString() {
            return "ConjureItemTask{" + "request=" + request + ", priority=" + priority + ", state=" + state + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<StackType> request,
            @Nonnull CraftingContext context) {
        if (request.allowSimulation) {
            return Collections.singletonList(new ConjureItemTask<>(request));
        } else {
            return Collections.emptyList();
        }
    }
}
