package appeng.util;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;

public final class IterationCounter {

    private static AtomicInteger counter = new AtomicInteger(0);
    private static final ThreadLocal<Integer> globalDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> globalCounter = ThreadLocal.withInitial(() -> 0);

    public static int fetchNewId() {
        return counter.getAndIncrement();
    }

    /**
     * Increment the global iteration depth for this thread
     * 
     * @return Iteration number currently used for global iteration
     */
    public static int incrementGlobalDepth() {
        var depth = globalDepth.get();
        if (depth == 0) {
            globalCounter.set(fetchNewId());
        }
        globalDepth.set(depth + 1);
        return globalCounter.get();
    }

    /**
     * Initialize global iteration number and increment global depth for this thread
     *
     * This unconditionally overrides current global iteration number!
     *
     * @param iteration Iteration number to set global one to
     */
    public static void incrementGlobalDepthWith(int iteration) {
        globalCounter.set(iteration);
        globalDepth.set(globalDepth.get() + 1);
    }

    public static void decrementGlobalDepth() {
        globalDepth.set(globalDepth.get() - 1);
    }

    public static int getCurrentDepth() {
        return globalDepth.get();
    }

    public static int getCurrentIteration() {
        return globalCounter.get();
    }

    @VisibleForTesting
    static void clear() {
        counter = new AtomicInteger(0);
        globalDepth.set(0);
        globalCounter.set(0);
    }
}
