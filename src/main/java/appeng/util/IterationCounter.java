package appeng.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class IterationCounter {

    private static AtomicInteger counter = new AtomicInteger(0);

    public static int fetchNewId() {
        return counter.getAndIncrement();
    }
}
