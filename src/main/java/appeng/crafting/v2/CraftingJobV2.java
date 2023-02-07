package appeng.crafting.v2;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.world.World;

import org.apache.logging.log4j.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext.RequestInProcessing;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * A new, self-contained implementation of the crafting calculator. Does an iterative search on the crafting recipe
 * tree.
 */
public class CraftingJobV2 implements ICraftingJob, Future<ICraftingJob> {

    protected volatile long totalByteCost = -1; // -1 means it needs to be recalculated

    protected CraftingContext context;
    protected final CraftingRequest<IAEItemStack> originalRequest;
    protected ICraftingCallback callback;

    protected enum State {
        RUNNING,
        FINISHED,
        CANCELLED
    }

    protected State state = State.RUNNING;

    public CraftingJobV2(final World world, final IGrid meGrid, final BaseActionSource actionSource,
            final IAEItemStack what, final ICraftingCallback callback) {
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequest = new CraftingRequest<>(what, SubstitutionMode.PRECISE_FRESH, IAEItemStack.class, true);
        this.context.addRequest(this.originalRequest);
        this.context.itemModel.ignore(what);
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
            for (RequestInProcessing<?> request : context.getLiveRequests()) {
                byteCost += request.request.getByteCost();
            }
            totalByteCost = byteCost;
        }
        return byteCost;
    }

    @Override
    public void populatePlan(IItemList<IAEItemStack> plan) {
        for (CraftingTask task : context.getResolvedTasks()) {
            task.populatePlan(plan);
        }
    }

    @Override
    public IAEItemStack getOutput() {
        return originalRequest.stack;
    }

    @Override
    public boolean simulateFor(int milli) {
        if (this.state != State.RUNNING) {
            return false;
        }
        final long startTime = System.currentTimeMillis();
        final long finishTime = startTime + milli;
        CraftingTask.State taskState = CraftingTask.State.NEEDS_MORE_WORK;
        do {
            taskState = context.doWork();
            totalByteCost = -1;
        } while (taskState.needsMoreWork && System.currentTimeMillis() < finishTime && (state == State.RUNNING));

        if (!taskState.needsMoreWork) {
            getByteTotal();
            this.state = State.FINISHED;
            if (AELog.isCraftingDebugLogEnabled()) {
                AELog.log(
                        Level.DEBUG,
                        "Crafting job for %s finished with resolved steps: %s\n",
                        originalRequest.toString(),
                        context.toString());
            }
            if (callback != null) {
                callback.calculationComplete(this);
            }
        }

        return taskState.needsMoreWork;
    }

    @Override
    public Future<ICraftingJob> schedule() {
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
                    "Trying to start crafting a not fully calculated job for " + originalRequest.toString());
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
        switch (this.state) {
            case RUNNING:
                throw new TimeoutException();
            case CANCELLED:
                throw new InterruptedException();
            case FINISHED:
                return this;
            default:
                throw new IllegalStateException();
        }
    }
}
