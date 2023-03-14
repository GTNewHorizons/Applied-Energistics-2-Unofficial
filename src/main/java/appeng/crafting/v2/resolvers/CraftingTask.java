package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * A single action that can be performed to solve a {@link CraftingRequest}. Can have multiple inputs and outputs,
 * resolved at runtime during crafting resolution (e.g. for handling substitutions).
 */
public abstract class CraftingTask<RequestStackType extends IAEStack<RequestStackType>> implements ITreeSerializable {

    public enum State {

        NEEDS_MORE_WORK(true),
        SUCCESS(false),
        /**
         * This aborts the entire crafting operation, use only if absolutely necessary
         */
        FAILURE(false);

        public final boolean needsMoreWork;

        State(boolean needsMoreWork) {
            this.needsMoreWork = needsMoreWork;
        }
    }

    public static final class StepOutput {

        @Nonnull
        public final List<CraftingRequest<?>> extraInputsRequired;

        public StepOutput() {
            this(Collections.emptyList());
        }

        public StepOutput(@Nonnull List<CraftingRequest<?>> extraInputsRequired) {
            this.extraInputsRequired = extraInputsRequired;
        }
    }

    public static final int PRIORITY_EXTRACT = Integer.MAX_VALUE - 100;
    public static final int PRIORITY_CRAFTING_EMITTER = PRIORITY_EXTRACT - 200;
    /** Gets added to a priority determined by the priority of the crafting pattern */
    public static final int PRIORITY_CRAFT_OFFSET = 0;

    public static final int PRIORITY_SIMULATE_CRAFT = Integer.MIN_VALUE + 200;
    public static final int PRIORITY_SIMULATE = Integer.MIN_VALUE + 100;

    public final CraftingRequest<RequestStackType> request;
    public final int priority;
    protected State state;

    /**
     * Called when it's this task's turn for computation.
     *
     * @return A {@link StepOutput} instance describing progress made by the task in this call. If success or failure,
     *         the task should have cleaned up after itself - cancel won't be called.
     */
    public abstract StepOutput calculateOneStep(CraftingContext context);

    /**
     * @return The amount of actually refunded items
     */
    public abstract long partialRefund(CraftingContext context, long amount);

    public abstract void fullRefund(CraftingContext context);

    public abstract void populatePlan(IItemList<IAEItemStack> targetPlan);

    public abstract void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
            MECraftingInventory craftingInv);

    protected CraftingTask(CraftingRequest<RequestStackType> request, int priority) {
        this.request = request;
        this.priority = priority;
        this.state = State.NEEDS_MORE_WORK;
    }

    public State getState() {
        return state;
    }

    /**
     * Compares priorities - highest priority first
     */
    public static final Comparator<CraftingTask> PRIORITY_COMPARATOR = Comparator.comparing(ct -> -ct.priority);

    // TODO: remove
    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public void loadChildren(Collection<ITreeSerializable> children) throws IOException {

    }
}
