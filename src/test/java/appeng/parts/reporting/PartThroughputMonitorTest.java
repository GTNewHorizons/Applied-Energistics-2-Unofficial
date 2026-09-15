package appeng.parts.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import appeng.parts.reporting.PartThroughputMonitor.ThroughputTracker;

public final class PartThroughputMonitorTest {

    @Test
    public void averagesBurstsAcrossTwoMinutes() {
        final ThroughputTracker tracker = new ThroughputTracker();
        tracker.reset(0);

        assertTrue(tracker.update(120, 200));
        assertFalse(tracker.isFull());
        for (int i = 1; i < 12; i++) {
            assertTrue(tracker.update(120, 200));
        }

        assertTrue(tracker.isFull());
        assertEquals(1.0, tracker.getAveragePerTick() * 20, 0.0001);

        assertTrue(tracker.update(120, 200));
        assertEquals(0.0, tracker.getAveragePerTick(), 0.0);
    }

    @Test
    public void preservesFractionalRatesAndActualTickDurations() {
        final ThroughputTracker tracker = new ThroughputTracker();
        tracker.reset(1_000);

        assertFalse(tracker.update(1_090, 199));
        assertEquals(90 / 199.0, tracker.getAveragePerTick(), 0.0001);
        assertTrue(tracker.update(1_090, 51));
        assertEquals(7.2, tracker.getAveragePerTick() * 20, 0.0001);
    }

    @Test
    public void resetClearsPreviousItemHistory() {
        final ThroughputTracker tracker = new ThroughputTracker();
        tracker.reset(0);
        tracker.update(100, 200);

        tracker.reset(1_000);

        assertEquals(0.0, tracker.getAveragePerTick(), 0.0);
    }
}
