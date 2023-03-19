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

import appeng.api.config.SortDir;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IInvTweaks;

public class ItemSorters {

    private static SortDir direction = SortDir.ASCENDING;

    public static final Comparator<IAEItemStack> CONFIG_BASED_SORT_BY_NAME = Comparator
            .comparing(Platform::getItemDisplayName, (a, b) -> a.compareToIgnoreCase(b) * direction.sortHint);

    public static final Comparator<IAEItemStack> CONFIG_BASED_SORT_BY_MOD = Comparator
            .comparing(Platform::getModId, (a, b) -> a.compareToIgnoreCase(b) * direction.sortHint)
            .thenComparing(Platform::getItemDisplayName);

    public static final Comparator<IAEItemStack> CONFIG_BASED_SORT_BY_SIZE = Comparator
            .comparing(IAEStack::getStackSize, (a, b) -> Long.compare(b, a) * direction.sortHint);

    private static IInvTweaks api;
    public static final Comparator<IAEItemStack> CONFIG_BASED_SORT_BY_INV_TWEAKS = new Comparator<IAEItemStack>() {

        @Override
        public int compare(final IAEItemStack o1, final IAEItemStack o2) {
            if (api == null) {
                return CONFIG_BASED_SORT_BY_NAME.compare(o1, o2);
            }

            return api.compareItems(o1.getItemStack(), o2.getItemStack()) * direction.sortHint;
        }
    };

    public static void init() {
        if (api != null) {
            return;
        }

        if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.InvTweaks)) {
            api = (IInvTweaks) IntegrationRegistry.INSTANCE.getInstance(IntegrationType.InvTweaks);
        } else {
            api = null;
        }
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
