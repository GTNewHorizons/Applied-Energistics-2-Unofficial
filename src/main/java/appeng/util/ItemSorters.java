/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util;

import java.util.Comparator;
import java.util.function.Function;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;

import appeng.api.config.SortDir;
import appeng.api.storage.data.IAEStack;

public class ItemSorters {

    private static SortDir direction = SortDir.ASCENDING;

    private static final Comparator<String> DIRECTED_IGNORE_CASE = (a, b) -> a.compareToIgnoreCase(b)
            * direction.sortHint;

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_NAME = sortByName(
            Function.identity(),
            ItemSorters::getSortName);

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_MOD = sortByMod(
            Function.identity(),
            IAEStack::getModId,
            ItemSorters::getSortName);

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_SIZE = Comparator
            .comparing(IAEStack::getStackSize, (a, b) -> Long.compare(b, a) * direction.sortHint);

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_INV_TWEAKS = new Comparator<>() {

        @Override
        public int compare(final IAEStack<?> o1, final IAEStack<?> o2) {
            if (!InvTweakSortingModule.isLoaded()) {
                return CONFIG_BASED_SORT_BY_NAME.compare(o1, o2);
            }

            return InvTweakSortingModule.compareItems(o1.getItemStackForNEI(), o2.getItemStackForNEI())
                    * direction.sortHint;
        }
    };

    public static <K> Comparator<IAEStack<?>> sortByName(final Function<IAEStack<?>, K> key,
            final Function<K, String> sortName) {
        return Comparator.comparing(key, Comparator.comparing(sortName, DIRECTED_IGNORE_CASE));
    }

    public static <K> Comparator<IAEStack<?>> sortByMod(final Function<IAEStack<?>, K> key,
            final Function<K, String> modId, final Function<K, String> sortName) {
        return Comparator.comparing(key, Comparator.comparing(modId, DIRECTED_IGNORE_CASE).thenComparing(sortName));
    }

    /** Display name without format codes, so &-styled or colored names sort by their visible text. */
    public static String getSortName(final IAEStack<?> stack) {
        return stripFormatting(FontRendering.preprocessText(stack.getDisplayName()));
    }

    public static String stripFormatting(final String s) {
        int i = s.indexOf('\u00a7');
        if (i < 0) return s;
        final int len = s.length();
        final StringBuilder sb = new StringBuilder(len);
        int last = 0;
        while (i >= 0 && i + 1 < len) {
            sb.append(s, last, i);
            last = i + 1 + Character.charCount(s.codePointAt(i + 1));
            i = s.indexOf('\u00a7', last);
        }
        return sb.append(s, last, len).toString();
    }

    public static int compareInt(final int a, final int b) {
        // for backwards compat for ext mods...
        return Integer.compare(a, b);
    }

    public static int compareLong(final long a, final long b) {
        // for backwards compat with ext mods...
        return Long.compare(a, b);
    }

    public static int compareDouble(final double a, final double b) {
        // for backwards compat for ext mods...
        return Double.compare(a, b);
    }

    public static void setDirection(final SortDir direction) {
        ItemSorters.direction = direction;
    }
}
