package appeng.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class IterationCounter {

    private static AtomicInteger counter = new AtomicInteger(0);
    private static int globalDepth = 0;
    private static int globalCounter = 0;

    public static int fetchNewId() {
        return counter.getAndIncrement();
    }

    /**
     * Increment the global iteration depth
     * 
     * @return Iteration number currently used for global iteration
     */
    public static synchronized int incrementGlobalDepth() {
        if (globalDepth == 0) {
            globalCounter++;
        }
        globalDepth++;
        return globalCounter;
    }

    /**
     * Initialize global iteration number and increment global depth This unconditionally overrides current global
     * iteration number!
     *
     * @param iteration Iteration number to set global one to
     */
    public static synchronized void incrementGlobalDepthWith(int iteration) {
        globalCounter = iteration;
        globalDepth++;
    }

    public static synchronized void decrementGlobalDepth() {
        globalDepth--;
    }
}
