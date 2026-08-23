package appeng.test.util.prioitylist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Predicate;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.OreFilteredList;

public class OreFilteredListFunctionalTest {

    private static IAEItemStack ironIngot;
    private static IAEItemStack goldIngot;

    @BeforeAll
    public static void registerTestOres() {
        OreDictionary.registerOre("ingotIron", new ItemStack(Items.iron_ingot));
        OreDictionary.registerOre("ingotGold", new ItemStack(Items.gold_ingot));

        ironIngot = AEItemStack.create(new ItemStack(Items.iron_ingot));
        goldIngot = AEItemStack.create(new ItemStack(Items.gold_ingot));
    }

    @Test
    public void repeatedConjunctiveOperandRejectsAllItems() {
        Predicate<IAEItemStack> filter = OreFilteredList.makeFilter("!ingotIron&ingotIron");

        assertNotNull(filter);
        assertFalse(filter.test(ironIngot));
        assertFalse(filter.test(goldIngot));
    }
}
