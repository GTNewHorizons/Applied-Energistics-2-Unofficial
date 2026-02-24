/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.GuiText;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

public class ReshuffleReport {

    private static final int LINE_WIDTH = 36;

    private static final String DARK_AQUA = "§3";
    private static final String BLACK = "§0";
    private static final String DARK_GREEN = "§2";
    private static final String DARK_RED = "§4";
    private static final String DARK_GRAY = "§8";
    private static final String GOLD = "§6";

    public static class ItemChange {

        public final IAEStack<?> stack;
        public final long beforeCount;
        public final long afterCount;
        public final long difference;
        public final ChangeType changeType;

        public ItemChange(IAEStack<?> stack, long beforeCount, long afterCount) {
            this.stack = stack;
            this.beforeCount = beforeCount;
            this.afterCount = afterCount;
            this.difference = afterCount - beforeCount;

            if (difference > 0) {
                this.changeType = ChangeType.GAINED;
            } else if (difference < 0) {
                this.changeType = ChangeType.LOST;
            } else {
                this.changeType = ChangeType.UNCHANGED;
            }
        }
    }

    public enum ChangeType {
        GAINED,
        LOST,
        UNCHANGED
    }

    private final Map<String, Long> beforeSnapshot = new HashMap<>();
    private final Map<String, IAEStack<?>> stackLookup = new HashMap<>();

    private int totalItemTypesBefore = 0;
    private int totalItemTypesAfter = 0;
    private long totalStacksBefore = 0;
    private long totalStacksAfter = 0;
    private int itemsProcessed = 0;
    private int itemsSkipped = 0;
    private int itemsGained = 0;
    private int itemsLost = 0;
    private int itemsUnchanged = 0;
    private long totalGained = 0;
    private long totalLost = 0;

    private final List<ItemChange> lostItems = new ArrayList<>();
    private final List<ItemChange> gainedItems = new ArrayList<>();
    private final List<IAEStack<?>> skippedItemsList = new ArrayList<>();

    private final Set<IAEStackType<?>> allowedTypes;
    private final boolean voidProtection;
    private final long startTime;
    private long endTime;

    public ReshuffleReport(final Set<IAEStackType<?>> allowedTypes, final boolean voidProtection) {
        this.allowedTypes = allowedTypes;
        this.voidProtection = voidProtection;
        this.startTime = System.currentTimeMillis();
    }

    public void snapshotBefore(Map<IAEStackType<?>, IMEMonitor<?>> monitors, Set<IAEStackType<?>> allowedTypes) {
        beforeSnapshot.clear();
        stackLookup.clear();
        totalStacksBefore = 0;
        totalItemTypesBefore = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor<?> monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList<?> storageList = monitor.getStorageList();
            for (Object obj : storageList) {
                IAEStack<?> stack = (IAEStack<?>) obj;
                if (stack != null && stack.getStackSize() > 0) {
                    String key = getStackKey(stack);
                    beforeSnapshot.put(key, stack.getStackSize());
                    stackLookup.put(key, stack.copy());
                    totalStacksBefore += stack.getStackSize();
                    totalItemTypesBefore++;
                }
            }
        }
    }

    public void generateReport(Map<IAEStackType<?>, IMEMonitor<?>> monitors, Set<IAEStackType<?>> allowedTypes,
            int processed, int skipped, List<IAEStack<?>> skippedStacks) {
        this.endTime = System.currentTimeMillis();
        this.itemsProcessed = processed;
        this.itemsSkipped = skipped;
        this.skippedItemsList.clear();
        if (skippedStacks != null) {
            for (IAEStack<?> s : skippedStacks) {
                this.skippedItemsList.add(s.copy());
            }
        }

        Map<String, Long> afterSnapshot = new HashMap<>();
        totalStacksAfter = 0;
        totalItemTypesAfter = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor<?> monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList<?> storageList = monitor.getStorageList();
            for (Object obj : storageList) {
                IAEStack<?> stack = (IAEStack<?>) obj;
                if (stack != null && stack.getStackSize() > 0) {
                    String key = getStackKey(stack);
                    afterSnapshot.put(key, stack.getStackSize());
                    if (!stackLookup.containsKey(key)) stackLookup.put(key, stack.copy());
                    totalStacksAfter += stack.getStackSize();
                    totalItemTypesAfter++;
                }
            }
        }

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(beforeSnapshot.keySet());
        allKeys.addAll(afterSnapshot.keySet());

        for (String key : allKeys) {
            long before = beforeSnapshot.getOrDefault(key, 0L);
            long after = afterSnapshot.getOrDefault(key, 0L);
            IAEStack<?> stack = stackLookup.get(key);
            if (stack == null) continue;

            ItemChange change = new ItemChange(stack, before, after);

            switch (change.changeType) {
                case GAINED:
                    itemsGained++;
                    totalGained += change.difference;
                    gainedItems.add(change);
                    break;
                case LOST:
                    itemsLost++;
                    totalLost += Math.abs(change.difference);
                    lostItems.add(change);
                    break;
                case UNCHANGED:
                    itemsUnchanged++;
                    break;
            }
        }

        lostItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
        gainedItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
    }

    private List<String> buildItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String name = getStackDisplayName(c.stack);
        String delta = abbrev(Math.abs(c.difference));
        out.add(accentColor + "• " + BLACK + name + " " + accentColor + sign + delta);
        out.add(DARK_GRAY + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    public List<String> generateReportLines() {
        List<String> lines = new ArrayList<>();
        long durationMs = endTime - startTime;

        lines.add(DARK_AQUA + centerTitle(GuiText.ReshuffleReportTitle.getLocal()));

        lines.add(
                DARK_AQUA + GuiText.ReshuffleReportTime.getLocal()
                        + " "
                        + BLACK
                        + String.format("%.2fs", durationMs / 1000.0)
                        + DARK_AQUA
                        + "  "
                        + GuiText.ReshuffleReportMode.getLocal()
                        + " "
                        + BLACK
                        + buildModeString());

        lines.add(
                DARK_AQUA + GuiText.ReshuffleReportVoidLabel.getLocal()
                        + " "
                        + (voidProtection ? DARK_GREEN + GuiText.ReshuffleReportVoidOn.getLocal()
                                : DARK_RED + GuiText.ReshuffleReportVoidOff.getLocal()));

        lines.add("");
        lines.add(DARK_AQUA + GuiText.ReshuffleReportSectionProcessing.getLocal());
        lines.add(
                DARK_AQUA + GuiText.ReshuffleReportDone.getLocal()
                        + " "
                        + DARK_GREEN
                        + fmt(itemsProcessed)
                        + DARK_AQUA
                        + "  "
                        + GuiText.ReshuffleReportSkip.getLocal()
                        + " "
                        + GOLD
                        + fmt(itemsSkipped));

        lines.add("");
        lines.add(DARK_AQUA + GuiText.ReshuffleReportSectionStorageTotals.getLocal());
        lines.add(
                buildTotalLine(
                        GuiText.ReshuffleReportLabelTypes.getLocal(),
                        totalItemTypesBefore,
                        totalItemTypesAfter));
        lines.add(buildTotalLine(GuiText.ReshuffleReportLabelStacks.getLocal(), totalStacksBefore, totalStacksAfter));

        lines.add("");
        lines.add(DARK_AQUA + GuiText.ReshuffleReportSectionItemChanges.getLocal());
        lines.add(
                DARK_GREEN + GuiText.ReshuffleReportGainedLabel
                        .getLocal() + " " + BLACK + fmt(itemsGained) + DARK_GREEN + " (" + abbrev(totalGained) + ")");
        lines.add(
                DARK_RED + GuiText.ReshuffleReportLostLabel
                        .getLocal() + " " + BLACK + fmt(itemsLost) + DARK_RED + " (" + abbrev(totalLost) + ")");
        lines.add(DARK_GRAY + GuiText.ReshuffleReportUnchangedLabel.getLocal() + " " + BLACK + fmt(itemsUnchanged));

        if (!lostItems.isEmpty()) {
            lines.add("");
            lines.add(DARK_RED + GuiText.ReshuffleReportSectionTopLost.getLocal());
            for (ItemChange c : lostItems) {
                lines.addAll(buildItemLines(c, DARK_RED, "-"));
            }
        }

        if (!gainedItems.isEmpty() && totalGained > 0) {
            lines.add("");
            lines.add(DARK_GREEN + GuiText.ReshuffleReportSectionTopGained.getLocal());
            for (ItemChange c : gainedItems) {
                lines.addAll(buildItemLines(c, DARK_GREEN, "+"));
            }
        }

        if (!skippedItemsList.isEmpty()) {
            lines.add("");
            lines.add(GOLD + GuiText.ReshuffleReportSectionSkipped.getLocal());
            for (IAEStack<?> stack : skippedItemsList) {
                lines.add(
                        GOLD + "• "
                                + BLACK
                                + getStackDisplayName(stack)
                                + " "
                                + DARK_GRAY
                                + abbrev(stack.getStackSize()));
            }
        }

        lines.add("");
        long net = totalStacksAfter - totalStacksBefore;
        if (net != 0) {
            lines.add(
                    GOLD + GuiText.ReshuffleReportNetChanged.getLocal()
                            + " "
                            + diffColor(net)
                            + signedAbbrev(net)
                            + " "
                            + DARK_GRAY
                            + GuiText.ReshuffleReportNetChangedReason.getLocal());
        } else {
            lines.add(DARK_GREEN + GuiText.ReshuffleReportIntegrityOk.getLocal());
        }

        lines.add(DARK_AQUA + repeatEquals(LINE_WIDTH));
        return lines;
    }

    private String buildTotalLine(String label, long before, long after) {
        long delta = after - before;
        return DARK_AQUA + label
                + " "
                + DARK_GRAY
                + abbrev(before)
                + BLACK
                + " -> "
                + BLACK
                + abbrev(after)
                + " "
                + diffColor(delta)
                + "("
                + signedAbbrev(delta)
                + ")";
    }

    private String buildModeString() {
        if (allowedTypes.isEmpty()) return GuiText.ReshuffleReportModeNone.getLocal();
        StringBuilder sb = new StringBuilder();
        for (IAEStackType<?> type : allowedTypes) {
            if (sb.length() > 0) sb.append('/');
            sb.append(type.getDisplayName());
        }
        return sb.toString();
    }

    private static String abbrev(long v) {
        if (v < 0) return "-" + abbrev(-v);
        if (v >= 1_000_000_000_000L) return String.format("%.1fT", v / 1_000_000_000_000.0);
        if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1_000_000_000.0);
        if (v >= 1_000_000L) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 10_000L) return String.format("%.1fK", v / 1_000.0);
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }

    private static String signedAbbrev(long v) {
        if (v > 0) return "+" + abbrev(v);
        if (v < 0) return "-" + abbrev(-v);
        return "0";
    }

    private static String fmt(long v) {
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }

    private static String diffColor(long delta) {
        if (delta > 0) return DARK_GREEN;
        if (delta < 0) return DARK_RED;
        return DARK_GRAY;
    }

    private static String centerTitle(String text) {
        int totalPad = Math.max(0, LINE_WIDTH - text.length());
        int left = totalPad / 2, right = totalPad - left;
        return repeatEquals(left) + text + repeatEquals(right);
    }

    private static String repeatEquals(int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        Arrays.fill(buf, '=');
        return new String(buf);
    }

    private String getStackKey(IAEStack<?> stack) {
        if (stack instanceof AEItemStack itemStack) {
            final ItemStack is = itemStack.getItemStack();
            String key = Item.itemRegistry.getNameForObject(is.getItem()) + "@" + is.getItemDamage();
            if (is.hasTagCompound()) key += "#" + is.getTagCompound().toString();
            return "item:" + key;
        } else if (stack instanceof AEFluidStack fluidStack) {
            final FluidStack fs = fluidStack.getFluidStack();
            String key = fs.getFluid().getName();
            if (fs.tag != null) key += "#" + fs.tag.toString();
            return "fluid:" + key;
        }
        String str = stack.toString();
        if (str.matches("^\\d+x.*")) str = str.replaceFirst("^\\d+x", "");
        return stack.getStackType().getClass().getSimpleName() + ":" + str;
    }

    private String getStackDisplayName(IAEStack<?> stack) {
        if (stack == null) return GuiText.ReshuffleReportUnknown.getLocal();
        try {
            String name = stack.getDisplayName();
            return (name != null && !name.isEmpty()) ? name : GuiText.ReshuffleReportUnknown.getLocal();
        } catch (Exception e) {
            return GuiText.ReshuffleReportUnknown.getLocal();
        }
    }
}
