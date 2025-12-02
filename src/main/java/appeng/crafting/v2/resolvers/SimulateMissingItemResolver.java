package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.GuiText;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class SimulateMissingItemResolver<StackType extends IAEStack<StackType>>
        implements CraftingRequestResolver<StackType> {

    public static class ConjureItemTask<StackType extends IAEStack<StackType>> extends CraftingTask<StackType> {

        private long fulfilled = 0;

        public ConjureItemTask(CraftingRequest<StackType> request) {
            super(request, CraftingTask.PRIORITY_SIMULATE); // conjure items for calculations out of thin air as a last
                                                            // resort
        }

        @SuppressWarnings("unused")
        public ConjureItemTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);

            fulfilled = serializer.getBuffer().readLong();
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            serializer.getBuffer().writeLong(fulfilled);
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
        public void populatePlan(IItemList<IAEStack<?>> targetPlan) {
            if (fulfilled > 0) targetPlan.add(request.stack.copy().setStackSize(fulfilled));
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            throw new IllegalStateException("Trying to start crafting a schedule with simulated items");
        }

        @Override
        public String toString() {
            return "ConjureItemTask{" + "fulfilled="
                    + fulfilled
                    + ", request="
                    + request
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }

        @Override
        public boolean isSimulated() {
            return true;
        }

        @Override
        public String getTooltipText() {
            return GuiText.Simulation.getLocal() + "\n " + GuiText.Missing.getLocal() + ": " + fulfilled;
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
