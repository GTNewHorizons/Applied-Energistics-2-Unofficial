package appeng.crafting.v2;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.util.item.FluidList;
import appeng.util.item.ItemList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A single requested stack (item or fluid) to craft, e.g. 32x Torches
 *
 * @param <StackType> Should be {@link IAEItemStack} or {@link appeng.api.storage.data.IAEFluidStack}
 */
public class CraftingRequest<StackType extends IAEStack<StackType>> {
    public enum SubstitutionMode {
        /**
         * No substitution, do not use items from the AE system - used for user-started requests
         */
        PRECISE_FRESH,
        /**
         * Use precisely the item requested
         */
        PRECISE,
        /**
         * Allow fuzzy matching of ingredients, the request will have a {@link CraftingRequest#acceptableSubstituteFn} predicate to determine if the given fuzzy match item is valid
         */
        ACCEPT_FUZZY
    }

    public final Class<StackType> stackTypeClass;
    /**
     * An item/fluid + count representing how many need to be crafted
     */
    public final StackType stack;

    public final SubstitutionMode substitutionMode;
    public final Predicate<StackType> acceptableSubstituteFn;
    public final IItemList<StackType> resolvedInputs;
    public final List<CraftingTask> usedResolvers = new ArrayList<>();
    /**
     * Whether this request and its children can be fulfilled by simulations
     */
    public final boolean allowSimulation;
    /**
     * The number of yet-unresolved elements from the stack (items/mB) that need to be crafted. Can go into the negatives if more are crafted than needed.
     */
    public volatile long remainingToProcess;
    /**
     * The cost in bytes to process this task so far
     */
    public volatile long byteCost = 0;
    /**
     * If the item had to be simulated (there was not enough ingredients in the system to fulfill this request in any way)
     */
    public volatile boolean wasSimulated = false;

    /**
     * @param stack                  The item/fluid and stack to request
     * @param substitutionMode       Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass         Pass in {@code StackType.class}, needed for resolving types at runtime
     * @param acceptableSubstituteFn A predicate testing if a given item (in fuzzy mode) can fulfill the request
     */
    public CraftingRequest(
            StackType stack,
            SubstitutionMode substitutionMode,
            Class<StackType> stackTypeClass,
            boolean allowSimulation,
            Predicate<StackType> acceptableSubstituteFn) {
        this.stackTypeClass = stackTypeClass;
        this.stack = stack;
        this.substitutionMode = substitutionMode;
        this.acceptableSubstituteFn = acceptableSubstituteFn;
        this.remainingToProcess = stack.getStackSize();
        this.allowSimulation = allowSimulation;
        if (stackTypeClass == IAEItemStack.class) {
            this.resolvedInputs = (IItemList<StackType>) new ItemList();
        } else if (stackTypeClass == IAEFluidStack.class) {
            this.resolvedInputs = (IItemList<StackType>) new FluidList();
        } else {
            throw new IllegalArgumentException(
                    "Invalid stack type for a crafting request: " + stackTypeClass.getName());
        }
    }

    /**
     * @param request          The item/fluid and stack to request
     * @param substitutionMode Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass   Pass in {@code StackType.class}, needed for resolving types at runtime
     */
    public CraftingRequest(
            StackType request,
            SubstitutionMode substitutionMode,
            Class<StackType> stackTypeClass,
            boolean allowSimulation) {
        this(request, substitutionMode, stackTypeClass, allowSimulation, stack -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    @Override
    public String toString() {
        return "CraftingRequest{request=" + stack + ", substitutionMode=" + substitutionMode + ", remainingToProcess="
                + remainingToProcess + ", byteCost=" + byteCost + ", wasSimulated=" + wasSimulated + '}';
    }

    /**
     * Reduces the items needed to fulfill this request, and adds any leftovers into the item cache of the context.
     */
    public void fulfill(CraftingTask origin, StackType input, CraftingContext context) {
        if (input == null || input.getStackSize() < 0) {
            throw new IllegalArgumentException("Can't fulfill crafting request with a negative amount of " + input);
        }
        if (input.getStackSize() == 0) {
            return;
        }
        final long consumed = Math.max(0L, this.remainingToProcess);
        final long remaining = input.getStackSize() - consumed;
        if (remaining > 0 && input instanceof IAEItemStack) {
            context.itemModel.injectItems((IAEItemStack) input, Actionable.MODULATE, context.actionSource);
        }
        this.byteCost += input.getStackSize();
        this.remainingToProcess -= input.getStackSize();
        this.resolvedInputs.add(input);
        this.usedResolvers.add(origin);
    }

    /**
     * Reduces the amount of items needed by {@code amount}, propagating any necessary refunds via the resolver crafting tasks.
     */
    public void partialRefund(CraftingContext context, long amount) {
        for (CraftingTask task : usedResolvers) {
            task.partialRefund(context, amount);
        }
        final long processed = this.stack.getStackSize() - this.remainingToProcess;
        this.stack.setStackSize(this.stack.getStackSize() - amount);
        this.remainingToProcess = this.stack.getStackSize() - processed;
    }

    public void fullRefund(CraftingContext context) {
        for (CraftingTask task : usedResolvers) {
            task.fullRefund(context);
        }
        this.remainingToProcess = 0;
        this.stack.setStackSize(0);
    }
}
