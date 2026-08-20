package appeng.recipes.ores;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OreDictionaryHandlerTest {

    @Test
    public void loadCompleteDefersAndCoalescesTheFinalBake() {
        final CountingHandler handler = new CountingHandler();

        handler.onLoadComplete();
        handler.onLoadComplete();
        assertEquals(0, handler.bakes);

        handler.onTick();
        assertEquals(1, handler.bakes);

        handler.onTick();
        assertEquals(1, handler.bakes);
    }

    private static final class CountingHandler extends OreDictionaryHandler {

        private int bakes;

        @Override
        public void bakeRecipes() {
            this.bakes++;
        }
    }
}
