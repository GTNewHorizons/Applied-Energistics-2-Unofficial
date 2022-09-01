package appeng.util.prioitylist;

import appeng.api.storage.data.IAEItemStack;
import appeng.core.AELog;
import java.util.HashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.StringUtils;

public class OreFilteredList implements IPartitionList<IAEItemStack> {
    private final Predicate<IAEItemStack> filterPredicate;

    public OreFilteredList(String filter) {
        filterPredicate = makeFilter(filter.trim());
    }

    @Override
    public boolean isListed(final IAEItemStack input) {
        return filterPredicate != null && filterPredicate.test(input);
    }

    @Override
    public boolean isEmpty() {
        return this.filterPredicate == null;
    }

    @Override
    public Iterable<IAEItemStack> getItems() {
        return null;
    }

    public static Predicate<IAEItemStack> makeFilter(String f) {
        try {
            Predicate<ItemStack> matcher = makeMatcher(f);
            if (matcher == null) return null;
            return new OreListMatcher(matcher);
        } catch (Exception ex) {
            AELog.debug(ex);
            return null;
        }
    }

    private static Predicate<ItemStack> makeMatcher(String f) {
        Predicate<ItemStack> matcher = null;
        if (notAWildcard(f)) {
            final Predicate<String> test = Pattern.compile(f).asPredicate();
            matcher = (is) -> is != null
                    && IntStream.of(OreDictionary.getOreIDs(is))
                            .mapToObj(OreDictionary::getOreName)
                            .anyMatch(test);
        } else if (!f.isEmpty()) {
            String[] filters = f.split("[&|]");
            String lastFilter = null;

            for (String filter : filters) {
                filter = filter.trim();
                if (filter.isEmpty()) continue;
                boolean negated = filter.startsWith("!");
                if (negated) filter = filter.substring(1);

                Predicate<ItemStack> test = filterToItemStackPredicate(filter);

                if (negated) test = test.negate();

                if (matcher == null) {
                    matcher = test;
                    lastFilter = filter;
                } else {
                    int endLast = f.indexOf(lastFilter) + lastFilter.length();
                    int startThis = f.indexOf(filter);
                    lastFilter = filter;
                    if (startThis <= endLast) continue;
                    boolean or = f.substring(endLast, startThis).contains("|");
                    matcher = or ? matcher.or(test) : matcher.and(test);
                }
            }
        }
        return matcher;
    }

    private static class OreListMatcher implements Predicate<IAEItemStack> {
        final HashMap<ItemRef, Boolean> cache = new HashMap<>();
        final Predicate<ItemStack> matcher;

        public OreListMatcher(Predicate<ItemStack> matcher) {
            this.matcher = matcher;
        }

        public boolean test(IAEItemStack t) {
            if (t == null) return false;
            return cache.compute(new ItemRef(t), (k, v) -> (v != null) ? v : matcher.test(t.getItemStack()));
        }
    }

    private static boolean notAWildcard(String f) {
        return f.contains("\\")
                || f.contains("^")
                || f.contains("$")
                || f.contains("+")
                || f.contains("(")
                || f.contains(")")
                || f.contains("[")
                || f.contains("]");
    }

    private static class ItemRef {
        private final Item ref;
        private final int damage;
        private final int hash;

        ItemRef(final IAEItemStack stack) {
            this.ref = stack.getItem();
            this.damage = stack.getItem().isDamageable() ? 0 : stack.getItemDamage();
            this.hash = this.ref.hashCode() ^ this.damage;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || this.getClass() != obj.getClass()) return false;
            final ItemRef other = (ItemRef) obj;
            return this.damage == other.damage && this.ref == other.ref;
        }

        @Override
        public String toString() {
            return "ItemRef [ref=" + this.ref.getUnlocalizedName() + ", damage=" + this.damage + ", hash=" + this.hash
                    + ']';
        }
    }

    private static Predicate<ItemStack> filterToItemStackPredicate(String filter) {
        final Predicate<String> test = filterToPredicate(filter);
        return (is) -> is != null
                && IntStream.of(OreDictionary.getOreIDs(is))
                        .mapToObj(OreDictionary::getOreName)
                        .anyMatch(test);
    }

    private static Predicate<String> filterToPredicate(String filter) {
        int numStars = StringUtils.countMatches(filter, "*");
        if (numStars == filter.length()) {
            return (str) -> true;
        } else if (filter.length() > 2 && filter.startsWith("*") && filter.endsWith("*") && numStars == 2) {
            final String pattern = filter.substring(1, filter.length() - 1);
            return (str) -> str.contains(pattern);
        } else if (filter.length() >= 2 && filter.startsWith("*") && numStars == 1) {
            final String pattern = filter.substring(1);
            return (str) -> str.endsWith(pattern);
        } else if (filter.length() >= 2 && filter.endsWith("*") && numStars == 1) {
            final String pattern = filter.substring(0, filter.length() - 1);
            return (str) -> str.startsWith(pattern);
        } else if (numStars == 0) {
            return (str) -> str.equals(filter);
        } else {
            String filterRegexFragment = filter.replace("*", ".*");
            String regexPattern = "^" + filterRegexFragment + "$";
            final Pattern pattern = Pattern.compile(regexPattern);
            return pattern.asPredicate();
        }
    }
}
