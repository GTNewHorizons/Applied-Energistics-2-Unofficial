package appeng.recipes.ores;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.Test;

public class OreDictionaryHandlerTest {

    @Test
    public void lateOreRegistrationsAreDeferredAndCoalesced() throws ReflectiveOperationException {
        final CountingHandler handler = new CountingHandler();
        final Field enableRebaking = OreDictionaryHandler.class.getDeclaredField("enableRebaking");

        enableRebaking.setAccessible(true);
        enableRebaking.setBoolean(handler, true);
        handler.onOreDictionaryRegister(new OreDictionary.OreRegisterEvent("oreTest", new ItemStack(new Item())));
        handler.onOreDictionaryRegister(new OreDictionary.OreRegisterEvent("oreTest", new ItemStack(new Item())));
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
