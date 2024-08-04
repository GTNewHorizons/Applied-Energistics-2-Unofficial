package appeng.util;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

/**
 * Test for {@link IterationCounter}
 */
public class IterationCounterGlobalTest {

    @After
    public void cleanup() {
        IterationCounter.clear();
    }

    @Test
    public void incrementGlobalDepthIterationTest() {
        var prev = IterationCounter.fetchNewId();
        IterationCounter.incrementGlobalDepthWith(prev);
        IterationCounter.decrementGlobalDepth();
        assertEquals(IterationCounter.incrementGlobalDepth(), prev + 1);
    }

    @Test
    public void incrementGlobalDepthDepthTest() {
        IterationCounter.incrementGlobalDepth();
        assertEquals(IterationCounter.getCurrentDepth(), 1);
    }

    @Test
    public void incrementGlobalDepthWithIterationTest() {
        IterationCounter.incrementGlobalDepthWith(1234);
        assertEquals(1234, IterationCounter.getCurrentIteration());
    }

    @Test
    public void incrementGlobalDepthWithDepthTest() {
        IterationCounter.incrementGlobalDepthWith(4567);
        assertEquals(1, IterationCounter.getCurrentDepth());
    }

    @Test
    public void decrementGlobalDepthDepth() {
        IterationCounter.incrementGlobalDepth();
        IterationCounter.decrementGlobalDepth();
        assertEquals(IterationCounter.getCurrentDepth(), 0);
    }
}
