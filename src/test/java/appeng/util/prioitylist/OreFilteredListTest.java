package appeng.util.prioitylist;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Predicate;

import org.junit.Test;

public class OreFilteredListTest {

    @Test
    public void repeatedConjunctiveOperandIsNotDropped() {
        assertRejectsIronAndGold("!ingotIron&ingotIron");
    }

    @Test
    public void repeatedConjunctiveOperandPreservesWhitespaceAndNegation() {
        assertRejectsIronAndGold(" ingotIron & !ingotIron ");
    }

    @Test
    public void repeatedDisjunctiveOperandIsNotDropped() {
        assertAcceptsIronAndGold("!ingotIron|ingotIron");
    }

    @Test
    public void repeatedDisjunctiveOperandPreservesWhitespaceAndNegation() {
        assertAcceptsIronAndGold(" ingotIron | !ingotIron ");
    }

    private static void assertRejectsIronAndGold(String expression) {
        Predicate<String> filter = makeStringFilter(expression);
        assertNotNull(filter);
        assertFalse(filter.test("ingotIron"));
        assertFalse(filter.test("ingotGold"));
    }

    private static void assertAcceptsIronAndGold(String expression) {
        Predicate<String> filter = makeStringFilter(expression);
        assertNotNull(filter);
        assertTrue(filter.test("ingotIron"));
        assertTrue(filter.test("ingotGold"));
    }

    private static Predicate<String> makeStringFilter(String expression) {
        return OreFilteredList.makeBooleanMatcher(expression, filter -> filter::equals);
    }
}
