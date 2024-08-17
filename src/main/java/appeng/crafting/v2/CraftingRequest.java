package appeng.crafting.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;

/**
 * A single requested stack (item or fluid) to craft, e.g. 32x Torches
 *
 * @param <StackType> Should be {@link IAEItemStack} or {@link appeng.api.storage.data.IAEFluidStack}
 */
public class CraftingRequest<StackType extends IAEStack<StackType>> implements ITreeSerializable {

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
         * Allow fuzzy matching of ingredients, the request will have a {@link CraftingRequest#acceptableSubstituteFn}
         * predicate to determine if the given fuzzy match item is valid
         */
        ACCEPT_FUZZY
    }

    public static class UsedResolverEntry<T extends IAEStack<T>> implements ITreeSerializable {

        public final CraftingRequest<T> parent;
        public CraftingTask<T> task;
        public final IAEStack<T> resolvedStack;

        public UsedResolverEntry(CraftingRequest<T> parent, CraftingTask<T> task, IAEStack<T> resolvedStack) {
            this.parent = parent;
            this.task = task;
            this.resolvedStack = resolvedStack;
        }

        @SuppressWarnings("unchecked")
        public UsedResolverEntry(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            this.parent = (CraftingRequest<T>) parent;
            this.resolvedStack = (IAEStack<T>) serializer.readStack();
            this.task = null; // to be filled by loadChildren
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            serializer.writeStack(resolvedStack);
            return Collections.singletonList(task);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            task = Objects.requireNonNull((CraftingTask<T>) children.iterator().next());
        }
    }

    public final Class<StackType> stackTypeClass;
    /**
     * An item/fluid + count representing how many need to be crafted
     */
    public final StackType stack;

    public final SubstitutionMode substitutionMode;
    public final Predicate<StackType> acceptableSubstituteFn;
    // (task, amount fulfilled by task)

    public final CraftingMode craftingMode;

    public final List<UsedResolverEntry<StackType>> usedResolvers = new ArrayList<>();
    /**
     * Whether this request and its children can be fulfilled by simulations
     */
    public final boolean allowSimulation;
    /**
     * The number of yet-unresolved elements from the stack (items/mB) that need to be crafted.
     */
    public volatile long remainingToProcess;

    private volatile long byteCost = 0;
    private volatile long untransformedByteCost = 0;
    /**
     * If the item had to be simulated (there was not enough ingredients in the system to fulfill this request in any
     * way)
     */
    public volatile boolean wasSimulated = false;
    public boolean incomplete = false;

    /**
     * A set of all patterns used to resolve this request and its parents, used for avoiding infinite recursion.
     */
    public final Set<ICraftingPatternDetails> patternParents = new HashSet<>();

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        serializer.writeStack(stack);
        serializer.writeEnum(substitutionMode);
        buffer.writeBoolean(allowSimulation);
        buffer.writeLong(remainingToProcess);
        buffer.writeLong(byteCost);
        buffer.writeLong(untransformedByteCost);
        buffer.writeBoolean(wasSimulated);
        buffer.writeBoolean(incomplete);
        buffer.writeInt(craftingMode.ordinal());
        return usedResolvers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadChildren(List<ITreeSerializable> children) throws IOException {
        for (ITreeSerializable child : children) {
            usedResolvers.add((UsedResolverEntry<StackType>) child);
        }
    }

    @SuppressWarnings({ "unchecked", "unused" })
    public CraftingRequest(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        stack = (StackType) serializer.readStack();
        if (stack == null) {
            stackTypeClass = (Class<StackType>) IAEItemStack.class;
        } else if (stack instanceof IAEItemStack) {
            stackTypeClass = (Class<StackType>) IAEItemStack.class;
        } else if (stack instanceof IAEFluidStack) {
            stackTypeClass = (Class<StackType>) IAEFluidStack.class;
        } else {
            throw new UnsupportedOperationException("Unknown stack type " + stack.getClass());
        }
        substitutionMode = serializer.readEnum(SubstitutionMode.class);
        allowSimulation = buffer.readBoolean();
        remainingToProcess = buffer.readLong();
        byteCost = buffer.readLong();
        untransformedByteCost = buffer.readLong();
        wasSimulated = buffer.readBoolean();
        incomplete = buffer.readBoolean();
        int index = buffer.readInt();
        if (index < 0 || index >= CraftingMode.values().length || CraftingMode.values()[index] == CraftingMode.STANDARD)
            craftingMode = CraftingMode.STANDARD;
        else craftingMode = CraftingMode.IGNORE_MISSING;
        acceptableSubstituteFn = x -> true;
    }

    /**
     * @param stack                  The item/fluid and stack to request
     * @param substitutionMode       Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass         Pass in {@code StackType.class}, needed for resolving types at runtime
     * @param acceptableSubstituteFn A predicate testing if a given item (in fuzzy mode) can fulfill the request
     */
    public CraftingRequest(StackType stack, SubstitutionMode substitutionMode, Class<StackType> stackTypeClass,
            boolean allowSimulation, CraftingMode craftingMode, Predicate<StackType> acceptableSubstituteFn) {
        this.stackTypeClass = stackTypeClass;
        this.stack = stack;
        this.substitutionMode = substitutionMode;
        this.acceptableSubstituteFn = acceptableSubstituteFn;
        this.remainingToProcess = stack.getStackSize();
        this.allowSimulation = allowSimulation;
        this.craftingMode = craftingMode;
        if (!(stackTypeClass == IAEItemStack.class || stackTypeClass == IAEFluidStack.class)) {
            throw new IllegalArgumentException(
                    "Invalid stack type for a crafting request: " + stackTypeClass.getName());
        }
    }

    /**
     * @param request          The item/fluid and stack to request
     * @param substitutionMode Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass   Pass in {@code StackType.class}, needed for resolving types at runtime
     */
    public CraftingRequest(StackType request, SubstitutionMode substitutionMode, Class<StackType> stackTypeClass,
            boolean allowSimulation, CraftingMode craftingMode) {
        this(request, substitutionMode, stackTypeClass, allowSimulation, craftingMode, x -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    /**
     * The cost in bytes to process this task so far
     */
    public long getByteCost() {
        return byteCost;
    }

    private String getReadableStackName() {
        String readableName = "?";
        if (stack instanceof AEItemStack) {
            try {
                readableName = ((AEItemStack) stack).getDisplayName();
            } catch (Exception e) {
                AELog.warn(e, "Trying to obtain display name for " + stack);
                readableName = "<EXCEPTION>";
            }
        }
        return readableName;
    }

    @Override
    public String toString() {
        return "CraftingRequest{request=" + stack
                + "<"
                + getReadableStackName()
                + ">, substitutionMode="
                + substitutionMode
                + ", remainingToProcess="
                + remainingToProcess
                + ", byteCost="
                + byteCost
                + ", wasSimulated="
                + wasSimulated
                + ", incomplete="
                + incomplete
                + '}';
    }

    public String getTooltipText() {
        return GuiText.RequestedItem.getLocal() + ": "
                + getReadableStackName()
                + "\n "
                + GuiText.Substitute.getLocal()
                + " "
                + ((substitutionMode == SubstitutionMode.ACCEPT_FUZZY) ? GuiText.Yes.getLocal() : GuiText.No.getLocal())
                + "\n "
                + GuiText.BytesUsed.getLocal()
                + ": "
                + byteCost
                + "\n "
                + GuiText.Simulation.getLocal()
                + ": "
                + (wasSimulated ? GuiText.Yes.getLocal() : GuiText.No.getLocal())
                + "\n "
                + GuiText.SimulationIncomplete.getLocal()
                + ": "
                + (incomplete ? GuiText.Yes.getLocal() : GuiText.No.getLocal());
    }

    /**
     * Reduces the items needed to fulfill this request, and adds any leftovers into the item cache of the context.
     */
    public void fulfill(CraftingTask origin, StackType input, CraftingContext context) {
        if (input == null || input.getStackSize() == 0) {
            return;
        }
        if (input.getStackSize() < 0) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with a negative amount of " + input + " : " + this);
        }
        if (this.remainingToProcess < input.getStackSize()) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with too many of " + input + " : " + this);
        }
        this.untransformedByteCost += input.getStackSize();
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.remainingToProcess -= input.getStackSize();
        this.usedResolvers.add(new UsedResolverEntry(this, origin, input.copy()));
    }

    /**
     * Reduces the amount of items needed by {@code amount}, propagating any necessary refunds via the resolver crafting
     * tasks.
     */
    public void partialRefund(CraftingContext context, final long refundedAmount) {
        long remainingTaskAmount = refundedAmount;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (remainingTaskAmount <= 0) {
                break;
            }
            if (resolver.resolvedStack.getStackSize() <= 0) {
                continue;
            }
            final long taskRefunded = resolver.task
                    .partialRefund(context, Math.min(remainingTaskAmount, resolver.resolvedStack.getStackSize()));
            remainingTaskAmount -= taskRefunded;
            resolver.resolvedStack.setStackSize(resolver.resolvedStack.getStackSize() - taskRefunded);
        }
        if (remainingTaskAmount < 0) {
            throw new IllegalStateException("Refunds resulted in a negative amount of an item for request " + this);
        }
        if (remainingTaskAmount != 0) {
            throw new IllegalStateException("Partial refunds could not cover all resolved items for request " + this);
        }

        final long originallyRequested = this.stack.getStackSize();
        final long originallyRemainingToProcess = this.remainingToProcess;
        final long originallyProcessed = originallyRequested - originallyRemainingToProcess;

        final long newlyRequested = originallyRequested - refundedAmount;
        final long newlyProcessed = Math.min(originallyProcessed, newlyRequested);
        final long newlyRemainingToProcess = newlyRequested - newlyProcessed;

        this.stack.setStackSize(newlyRequested);
        this.remainingToProcess = newlyRemainingToProcess;
        this.untransformedByteCost -= refundedAmount;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        if (this.remainingToProcess < 0) {
            throw new IllegalArgumentException("Refunded more items than were resolved for request " + this);
        }
    }

    public void fullRefund(CraftingContext context) {
        for (UsedResolverEntry resolver : usedResolvers) {
            resolver.task.fullRefund(context);
        }
        this.remainingToProcess = 0;
        this.untransformedByteCost = 0;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.stack.setStackSize(0);
        this.usedResolvers.clear();
    }

    /**
     * Gets the resolved item stack.
     * 
     * @throws IllegalStateException if multiple item types were used to resolve the request
     */
    public IAEStack<?> getOneResolvedType() {
        IAEStack<?> found = null;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (resolver.resolvedStack.getStackSize() <= 0) {
                continue;
            }
            if (found == null) {
                found = resolver.resolvedStack.copy();
            } else {
                throw new IllegalStateException("Found multiple item types resolving " + this);
            }
        }
        if (found == null) {
            throw new IllegalStateException("Found no resolution for " + this);
        }
        return found;
    }
}
