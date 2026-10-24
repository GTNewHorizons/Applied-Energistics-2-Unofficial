package appeng.crafting.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.world.World;

import org.apache.logging.log4j.Level;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext.RequestInProcessing;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * A new, self-contained implementation of the crafting calculator. Does an iterative search on the crafting recipe
 * tree.
 */
public class CraftingJobV2<StackType extends IAEStack<StackType>>
        implements ICraftingJob<StackType>, Future<ICraftingJob<StackType>>, ITreeSerializable {

    protected volatile long totalByteCost = -1; // -1 means it needs to be recalculated

    public CraftingContext getContext() {
        return context;
    }

    protected CraftingContext context;
    public CraftingRequest originalRequest;
    /**
     * All root requests of this job, {@link #originalRequest} is always the first one.
     */
    public List<CraftingRequest> originalRequests;
    // only used by the server to show the job on crafting CPUs, not sent with the crafting tree
    protected IAEStack<?> displayOutput;
    protected ICraftingCallback callback;
    protected String errorMessage = "";

    protected enum State {
        RUNNING,
        FINISHED,
        CANCELLED
    }

    protected State state = State.RUNNING;

    public CraftingJobV2(final World world, final IGrid meGrid, final BaseActionSource actionSource,
            final StackType what, final ICraftingCallback callback) {
        this(world, meGrid, actionSource, what, CraftingMode.STANDARD, callback);
    }

    public CraftingJobV2(final World world, final IGrid meGrid, final BaseActionSource actionSource,
            final StackType what, final CraftingMode craftingMode, final ICraftingCallback callback) {
        this(world, meGrid, actionSource, Collections.singletonList(what), craftingMode, callback);
    }

    /**
     * Creates a single job that crafts all the given stacks, so they share one calculation and one CPU.
     */
    public CraftingJobV2(final World world, final IGrid meGrid, final BaseActionSource actionSource,
            final List<StackType> whats, final CraftingMode craftingMode, final ICraftingCallback callback) {
        if (whats.isEmpty()) {
            throw new IllegalArgumentException("A crafting job needs at least one output");
        }
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequests = new ArrayList<>(whats.size());
        for (StackType what : whats) {
            final CraftingRequest request = new CraftingRequest(
                    what,
                    SubstitutionMode.PRECISE_FRESH,
                    true,
                    craftingMode);
            this.originalRequests.add(request);
            this.context.addRequest(request);
            this.context.itemModel.ignore(what);
        }
        this.originalRequest = this.originalRequests.get(0);
    }

    public CraftingJobV2(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        this.totalByteCost = serializer.getBuffer().readLong();
        this.state = serializer.readEnum(State.class);
        this.errorMessage = ByteBufUtils.readUTF8String(serializer.getBuffer());
        final int rootCount = ByteBufUtils.readVarInt(serializer.getBuffer(), 5);
        if (rootCount == 1) {
            this.originalRequest = new CraftingRequest(serializer, this);
            this.originalRequests = Collections.singletonList(this.originalRequest);
        } else {
            // the roots are serialized as children, see loadChildren
            this.originalRequests = new ArrayList<>(rootCount);
        }
    }

    public static CraftingJobV2 deserialize(World world, ByteBuf buffer) {
        if (buffer.readableBytes() < 1) {
            return null;
        }
        final CraftingTreeSerializer serializer = new CraftingTreeSerializer(world, buffer);
        try {
            serializer.initializeSerializer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final ITreeSerializable rawJob;
        try {
            rawJob = serializer.readSerializableAndQueueChildren(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!(rawJob instanceof CraftingJobV2 job)) {
            throw new UnsupportedOperationException("Invalid job type deserialized: " + rawJob.getClass());
        }
        while (serializer.hasWork()) {
            try {
                serializer.doWork();
            } catch (IndexOutOfBoundsException e) {
                // can not serialize any more items, cut off the tree
                AELog.warn(e, "Ran out of assigned space for crafting tree serialization");
                serializer.doBestEffortWork();
                break;
            }
        }
        return job;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.originalRequest.craftingMode;
    }

    public ByteBuf serialize() {
        try {
            final CraftingTreeSerializer serializer = new CraftingTreeSerializer(context.world);
            try {
                serializer.writeSerializableAndQueueChildren(this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            while (serializer.hasWork()) {
                try {
                    serializer.doWork();
                } catch (IndexOutOfBoundsException e) {
                    // can not serialize any more items, cut off the tree
                    AELog.warn(e, "Ran out of assigned space for crafting tree serialization");
                    break;
                }
            }
            return serializer.finalizeSerializer().slice();
        } catch (Exception e) {
            AELog.error(e, "Could not serialize the crafting job");
            return Unpooled.buffer(0);
        }
    }

    @Override
    public boolean isSimulation() {
        return context.wasSimulated;
    }

    @Override
    public long getByteTotal() {
        long byteCost = totalByteCost;
        if (byteCost < 0) {
            byteCost = 0;
            for (RequestInProcessing request : context.getLiveRequests()) {
                byteCost += request.request.getByteCost();
            }
            totalByteCost = byteCost;
        }
        return byteCost;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void populatePlan(IItemList<IAEStack<?>> plan) {
        for (CraftingTask task : context.getResolvedTasks()) {
            task.populatePlan(plan);
        }
    }

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        if (this.state == State.RUNNING) {
            throw new IllegalStateException("Can't serialize a running crafting simulation");
        }
        if (this.originalRequest == null) {
            throw new IllegalStateException("Can't serialize a null request");
        }
        serializer.getBuffer().writeLong(getByteTotal());
        serializer.writeEnum(state);
        ByteBufUtils.writeUTF8String(serializer.getBuffer(), errorMessage);
        ByteBufUtils.writeVarInt(serializer.getBuffer(), originalRequests.size(), 5);
        if (originalRequests.size() == 1) {
            // a single root is inlined into the job node
            return originalRequest.serializeTree(serializer);
        }
        return originalRequests;
    }

    @Override
    public ITreeSerializable getSerializationParent() {
        // when deserializing several roots, originalRequest is only known after loadChildren
        return originalRequest != null ? originalRequest : this;
    }

    @Override
    public void loadChildren(List<ITreeSerializable> children) throws IOException {
        if (originalRequest != null) {
            originalRequest.loadChildren(children);
            return;
        }
        for (ITreeSerializable child : children) {
            originalRequests.add((CraftingRequest) child);
        }
        if (!originalRequests.isEmpty()) {
            originalRequest = originalRequests.get(0);
        }
    }

    @Override
    public StackType getOutput() {
        // null if the tree was cut off before any root could be deserialized
        return originalRequest == null ? null : (StackType) originalRequest.stack;
    }

    @Override
    public IAEStack<?> getDisplayOutput() {
        return displayOutput;
    }

    public void setDisplayOutput(IAEStack<?> displayOutput) {
        this.displayOutput = displayOutput;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<StackType> getOutputs() {
        final List<StackType> outputs = new ArrayList<>(originalRequests.size());
        for (CraftingRequest request : originalRequests) {
            outputs.add((StackType) request.stack);
        }
        return outputs;
    }

    @Override
    public boolean simulateFor(int milli) {
        if (this.state != State.RUNNING) {
            return false;
        }
        final long startTime = System.currentTimeMillis();
        final long finishTime = startTime + milli;
        CraftingTask.State taskState = CraftingTask.State.NEEDS_MORE_WORK;
        try {
            do {
                taskState = context.doWork();
                totalByteCost = -1;
            } while (taskState.needsMoreWork && System.currentTimeMillis() < finishTime && (state == State.RUNNING));
        } catch (Exception e) {
            AELog.error(e, "Error while simulating crafting for " + originalRequests);
            errorMessage = e.toString();
            this.state = State.CANCELLED;
            if (callback != null) {
                callback.calculationComplete(this);
            }
            return false;
        }

        if (!taskState.needsMoreWork) {
            getByteTotal();
            this.state = State.FINISHED;
            if (AELog.isCraftingDebugLogEnabled()) {
                AELog.log(Level.INFO, "Crafting job for %s finished with resolved steps:", originalRequests.toString());
                AELog.logSimple(Level.INFO, context.toString());
            }
            if (callback != null) {
                callback.calculationComplete(this);
            }
        }

        return taskState.needsMoreWork;
    }

    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        TickHandler.INSTANCE.registerCraftingSimulation(this.context.world, this);
        return this;
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cluster) {
        return cluster instanceof CraftingCPUCluster;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU rawCluster, BaseActionSource src) {
        if (this.state == State.RUNNING) {
            throw new IllegalStateException(
                    "Trying to start crafting a not fully calculated job for " + originalRequests.toString());
        }
        CraftingCPUCluster cluster = (CraftingCPUCluster) rawCluster;
        context.actionSource = src;
        List<CraftingTask> resolvedTasks = context.getResolvedTasks();
        for (CraftingTask task : resolvedTasks) {
            task.startOnCpu(context, cluster, storage);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (this.state != State.RUNNING) {
            return false;
        } else {
            this.state = State.CANCELLED;
            return true;
        }
    }

    @Override
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state != State.RUNNING;
    }

    @Override
    public CraftingJobV2 get() throws InterruptedException, ExecutionException {
        this.simulateFor(Integer.MAX_VALUE);
        return this;
    }

    @Override
    public CraftingJobV2 get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            this.simulateFor((int) unit.convert(timeout, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
        return switch (this.state) {
            case RUNNING -> throw new TimeoutException();
            case CANCELLED -> throw new InterruptedException();
            case FINISHED -> this;
            default -> throw new IllegalStateException();
        };
    }

    public MECraftingInventory getStorageAtBeginning() {
        return getContext().availableCache;
    }

    @Override
    public boolean supportsOptimization() {
        return true;
    }
}
