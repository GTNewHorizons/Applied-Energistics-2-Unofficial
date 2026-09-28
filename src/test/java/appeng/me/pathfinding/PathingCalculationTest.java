package appeng.me.pathfinding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PathingCalculationTest {

    @Test
    public void controllerFaceEnforcesItsChannelCapacity() {
        final PathingCalculation.ControllerFace face = new PathingCalculation.ControllerFace(32);

        for (int i = 0; i < 32; i++) {
            assertTrue(face.canUseChannel());
            face.useChannel();
        }

        assertFalse(face.canUseChannel());
    }
}
