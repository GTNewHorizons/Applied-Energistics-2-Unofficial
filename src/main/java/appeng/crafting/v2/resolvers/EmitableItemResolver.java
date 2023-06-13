package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.GuiText;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class EmitableItemResolver implements CraftingRequestResolver<IAEItemStack> {

    public static class EmitItemTask extends CraftingTask<IAEItemStack> {

        private long fulfilled = 0;

        public EmitItemTask(CraftingRequest<IAEItemStack> request) {
            super(request, CraftingTask.PRIORITY_CRAFTING_EMITTER); // conjure items for calculations out of thin air as
                                                                    // a last
        }

        @SuppressWarnings("unused")
        public EmitItemTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
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
            // Assume items will be generated, triggered by the emitter
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
                targetPlan.addRequestable(request.stack.copy().setCountRequestable(fulfilled));
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            cpuCluster.addEmitable(this.request.stack.copy());
        }

        @Override
        public String toString() {
            return "EmitItemTask{" + "fulfilled="
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
        public String getTooltipText() {
            return GuiText.LevelEmitter.getLocal() + ": " + fulfilled;
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<IAEItemStack> request,
            @Nonnull CraftingContext context) {
        if (context.craftingGrid.canEmitFor(request.stack)) {
            return Collections.singletonList(new EmitItemTask(request));
        } else {
            return Collections.emptyList();
        }
    }
}
