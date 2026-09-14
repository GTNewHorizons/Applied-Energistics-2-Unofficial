package appeng.tile.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TileSuperMEReplenisherTest {

    @Test
    public void countsLargeAmountsWithoutNarrowing() {
        final long amount = (long) Integer.MAX_VALUE + 2;

        assertEquals(1_048_577, TileSuperMEReplenisher.bytesFor(amount, 2_048));
        assertEquals(1, TileSuperMEReplenisher.remainderInLastByte(amount, 2_048));
        assertEquals(0, TileSuperMEReplenisher.bytesFor(0, 2_048));
    }
}
