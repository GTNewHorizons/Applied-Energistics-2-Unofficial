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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

public class ReshuffleReport {

    private static final int LINE_WIDTH = 38;
    
    private static final String C_HEADER = "§3";
    private static final String C_LABEL = "§3";
    private static final String C_VALUE = "§0";
    private static final String C_GAIN = "§2";
    private static final String C_LOSS = "§4";
    private static final String C_NEUTRAL = "§8";
    private static final String C_WARN = "§6";
    private static final String C_ON = "§2";
    private static final String C_OFF = "§4";

    private static final String CT_HEADER = "§b";
    private static final String CT_LABEL = "§b";
    private static final String CT_VALUE = "§f";
    private static final String CT_GAIN = "§a";
    private static final String CT_LOSS = "§c";
    private static final String CT_NEUTRAL = "§7";
    private static final String CT_WARN = "§e";
    private static final String CT_DIVIDER = "§7";

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

    private final List<ItemChange> changes = new ArrayList<>();
    private final List<ItemChange> lostItems = new ArrayList<>();
    private final List<ItemChange> gainedItems = new ArrayList<>();

    private final Map<String, String> truncatedToFullName = new HashMap<>();
    private final Map<String, List<String>> hiddenItemsMap = new HashMap<>();

    private Set<IAEStackType<?>> allowedTypes = new HashSet<>();
    private boolean voidProtection;
    private long startTime;
    private long endTime;

    public ReshuffleReport() {
        this.startTime = System.currentTimeMillis();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void snapshotBefore(Map<IAEStackType<?>, IMEMonitor<?>> monitors,
            java.util.Set<IAEStackType<?>> allowedTypes) {
        beforeSnapshot.clear();
        stackLookup.clear();
        totalStacksBefore = 0;
        totalItemTypesBefore = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList storageList = monitor.getStorageList();
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void generateReport(Map<IAEStackType<?>, IMEMonitor<?>> monitors,
            java.util.Set<IAEStackType<?>> allowedTypes, int processed, int skipped) {
        this.endTime = System.currentTimeMillis();
        this.itemsProcessed = processed;
        this.itemsSkipped = skipped;

        Map<String, Long> afterSnapshot = new HashMap<>();
        totalStacksAfter = 0;
        totalItemTypesAfter = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList storageList = monitor.getStorageList();
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

        java.util.Set<String> allKeys = new java.util.HashSet<>();
        allKeys.addAll(beforeSnapshot.keySet());
        allKeys.addAll(afterSnapshot.keySet());

        for (String key : allKeys) {
            long before = beforeSnapshot.getOrDefault(key, 0L);
            long after = afterSnapshot.getOrDefault(key, 0L);
            IAEStack<?> stack = stackLookup.get(key);
            if (stack == null) continue;

            ItemChange change = new ItemChange(stack, before, after);
            changes.add(change);

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
    
    public List<String> generateReportLines() {
        List<String> lines = new ArrayList<>();
        long durationMs = endTime - startTime;

        lines.add(C_HEADER + center("= Reshuffle Report =", LINE_WIDTH, '='));

        String modeStr = buildModeString();
        lines.add(
                C_LABEL + "Time: "
                        + C_VALUE
                        + String.format("%.2fs", durationMs / 1000.0)
                        + C_LABEL
                        + "  Mode: "
                        + C_VALUE
                        + modeStr);

        lines.add(
                C_LABEL + "Void: "
                        + (voidProtection ? C_ON + "ON" : C_OFF + "OFF"));

        lines.add("");
        lines.add(C_HEADER + "-- Processing --");
        lines.add(
                C_LABEL + "Done: " + C_GAIN + fmt(itemsProcessed) + C_LABEL + "  Skip: " + C_WARN + fmt(itemsSkipped));

        lines.add("");
        lines.add(C_HEADER + "-- Storage Totals --");
        lines.add(buildTotalLine("Types:", totalItemTypesBefore, totalItemTypesAfter));
        lines.add(buildTotalLine("Stacks:", totalStacksBefore, totalStacksAfter));

        lines.add("");
        lines.add(C_HEADER + "-- Item Changes --");
        lines.add(C_GAIN + "+" + fmt(itemsGained) + " types  +" + abbrev(totalGained) + " items");
        lines.add(C_LOSS + "-" + fmt(itemsLost) + " types  -" + abbrev(totalLost) + " items");
        lines.add(C_NEUTRAL + "=" + fmt(itemsUnchanged) + " types unchanged");

        if (!lostItems.isEmpty()) {
            lines.add("");
            lines.add(C_LOSS + "-- Top Lost Items --");
            int shown = 0;
            for (ItemChange c : lostItems) {
                if (shown >= 5) break;
                lines.addAll(buildItemLines(c, C_LOSS, "-"));
                shown++;
            }
            int rem = lostItems.size() - 5;
            if (rem > 0) {
                lines.add(C_NEUTRAL + "  ...and " + rem + " more");

                List<String> hiddenLostItems = new ArrayList<>();
                for (int i = 5; i < lostItems.size(); i++) {
                    ItemChange c = lostItems.get(i);
                    String fullName = getStackDisplayName(c.stack);
                    String delta = abbrev(Math.abs(c.difference));
                    hiddenLostItems.add(CT_LOSS + "• " + CT_VALUE + fullName + " " + CT_LOSS + "-" + delta);
                    hiddenLostItems
                            .add(CT_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
                }
                setHiddenItems("lost", hiddenLostItems);
            }
        }

        if (!gainedItems.isEmpty() && totalGained > 0) {
            lines.add("");
            lines.add(C_GAIN + "-- Top Gained Items --");
            int shown = 0;
            for (ItemChange c : gainedItems) {
                if (shown >= 5) break;
                lines.addAll(buildItemLines(c, C_GAIN, "+"));
                shown++;
            }
            int rem = gainedItems.size() - 5;
            if (rem > 0) {
                lines.add(C_NEUTRAL + "  ...and " + rem + " more");

                List<String> hiddenGainedItems = new ArrayList<>();
                for (int i = 5; i < gainedItems.size(); i++) {
                    ItemChange c = gainedItems.get(i);
                    String fullName = getStackDisplayName(c.stack);
                    String delta = abbrev(Math.abs(c.difference));
                    hiddenGainedItems.add(CT_GAIN + "• " + CT_VALUE + fullName + " " + CT_GAIN + "+" + delta);
                    hiddenGainedItems
                            .add(CT_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
                }
                setHiddenItems("gained", hiddenGainedItems);
            }
        }

        lines.add("");
        long net = totalStacksAfter - totalStacksBefore;
        if (net != 0) {
            lines.add(C_WARN + "! Net: " + diffColor(net) + signedAbbrev(net) + C_NEUTRAL + " (network/crafts)");
        } else {
            lines.add(C_GAIN + "✓ Integrity OK – no net change");
        }

        lines.add(C_HEADER + repeat('=', LINE_WIDTH));
        return lines;
    }

    public List<String> generateTooltipReportLines() {
        List<String> lines = new ArrayList<>();
        long durationMs = endTime - startTime;

        lines.add(CT_HEADER + "═══════ Reshuffle Report ═══════");

        String modeStr = buildModeString();
        lines.add(
                CT_LABEL + "Time: "
                        + CT_VALUE
                        + String.format("%.2fs", durationMs / 1000.0)
                        + CT_LABEL
                        + "  Mode: "
                        + CT_VALUE
                        + modeStr);

        lines.add(
                CT_LABEL + "Void: "
                        + (voidProtection ? CT_GAIN + "ON" : CT_LOSS + "OFF"));

        lines.add("");
        lines.add(CT_DIVIDER + "-- Processing --");
        lines.add(
                CT_LABEL + "Done: "
                        + CT_GAIN
                        + fmt(itemsProcessed)
                        + CT_LABEL
                        + "  Skip: "
                        + CT_WARN
                        + fmt(itemsSkipped));

        lines.add("");
        lines.add(CT_DIVIDER + "-- Storage Totals --");
        lines.add(buildTooltipTotalLine("Types:", totalItemTypesBefore, totalItemTypesAfter));
        lines.add(buildTooltipTotalLine("Stacks:", totalStacksBefore, totalStacksAfter));

        lines.add("");
        lines.add(CT_DIVIDER + "-- Item Changes --");
        lines.add(CT_GAIN + "+" + fmt(itemsGained) + " types  +" + abbrev(totalGained) + " items");
        lines.add(CT_LOSS + "-" + fmt(itemsLost) + " types  -" + abbrev(totalLost) + " items");
        lines.add(CT_NEUTRAL + "=" + fmt(itemsUnchanged) + " types unchanged");

        if (!lostItems.isEmpty()) {
            lines.add("");
            lines.add(CT_LOSS + "-- Top Lost Items --");
            for (ItemChange c : lostItems) {
                lines.addAll(buildTooltipItemLines(c, CT_LOSS, "-"));
            }
        }

        if (!gainedItems.isEmpty() && totalGained > 0) {
            lines.add("");
            lines.add(CT_GAIN + "-- Top Gained Items --");
            for (ItemChange c : gainedItems) {
                lines.addAll(buildTooltipItemLines(c, CT_GAIN, "+"));
            }
        }

        lines.add("");
        long net = totalStacksAfter - totalStacksBefore;
        if (net != 0) {
            lines.add(
                    CT_WARN + "! Net: " + diffColorTooltip(net) + signedAbbrev(net) + CT_NEUTRAL + " (network/crafts)");
        } else {
            lines.add(CT_GAIN + "✓ Integrity OK – no net change");
        }

        lines.add(CT_DIVIDER + "════════════════════════");
        return lines;
    }

    private String buildTotalLine(String label, long before, long after) {
        long delta = after - before;
        return C_LABEL + label
                + " "
                + C_NEUTRAL
                + abbrev(before)
                + C_VALUE
                + " -> "
                + C_VALUE
                + abbrev(after)
                + " "
                + diffColor(delta)
                + "("
                + signedAbbrev(delta)
                + ")";
    }

    private List<String> buildItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String fullName = getStackDisplayName(c.stack);
        String displayName = truncate(fullName, 20);
        String delta = abbrev(Math.abs(c.difference));

        if (!fullName.equals(displayName)) {
            addTruncatedNameMapping(displayName, fullName);
        }

        out.add(accentColor + "• " + C_VALUE + displayName + " " + accentColor + sign + delta);

        out.add(C_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    private String buildModeString() {
        if (allowedTypes.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (IAEStackType<?> type : allowedTypes) {
            if (sb.length() > 0) sb.append('/');
            String n = type.getClass().getSimpleName().replace("AE", "").replace("StackType", "").replace("Stack", "");
            sb.append(n.isEmpty() ? "?" : n);
        }
        return sb.toString();
    }

    private static String abbrev(long v) {
        if (v < 0) return "-" + abbrev(-v);
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
        if (delta > 0) return C_GAIN;
        if (delta < 0) return C_LOSS;
        return C_NEUTRAL;
    }

    private static String diffColorTooltip(long delta) {
        if (delta > 0) return CT_GAIN;
        if (delta < 0) return CT_LOSS;
        return CT_NEUTRAL;
    }

    private String buildTooltipTotalLine(String label, long before, long after) {
        long delta = after - before;
        return CT_LABEL + label
                + " "
                + CT_NEUTRAL
                + abbrev(before)
                + CT_VALUE
                + " -> "
                + CT_VALUE
                + abbrev(after)
                + " "
                + diffColorTooltip(delta)
                + "("
                + signedAbbrev(delta)
                + ")";
    }

    private List<String> buildTooltipItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String fullName = getStackDisplayName(c.stack);
        String delta = abbrev(Math.abs(c.difference));

        out.add(accentColor + "• " + CT_VALUE + fullName + " " + accentColor + sign + delta);

        out.add(CT_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    private static String center(String text, int width, char pad) {
        int totalPad = Math.max(0, width - text.length());
        int left = totalPad / 2, right = totalPad - left;
        return repeat(pad, left) + text + repeat(pad, right);
    }

    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "Unknown";
        String plain = s.replaceAll("§.", "");
        if (plain.length() <= maxLen) return s;
        return plain.substring(0, maxLen - 1) + "…";
    }

    private String getStackKey(IAEStack<?> stack) {
        if (stack instanceof appeng.util.item.AEItemStack itemStack) {
            net.minecraft.item.ItemStack is = itemStack.getItemStack();
            if (is != null) {
                String key = net.minecraft.item.Item.itemRegistry.getNameForObject(is.getItem()) + "@"
                        + is.getItemDamage();
                if (is.hasTagCompound()) key += "#" + is.getTagCompound().hashCode();
                return "item:" + key;
            }
        } else if (stack instanceof appeng.util.item.AEFluidStack fluidStack) {
            net.minecraftforge.fluids.FluidStack fs = fluidStack.getFluidStack();
            if (fs != null && fs.getFluid() != null) {
                String key = fs.getFluid().getName();
                if (fs.tag != null) key += "#" + fs.tag.hashCode();
                return "fluid:" + key;
            }
        }
        String str = stack.toString();
        if (str.matches("^\\d+x.*")) str = str.replaceFirst("^\\d+x", "");
        return stack.getStackType().getClass().getSimpleName() + ":" + str;
    }

    private String getStackDisplayName(IAEStack<?> stack) {
        if (stack == null) return "Unknown";
        try {
            String name = stack.getDisplayName();
            return (name != null && !name.isEmpty()) ? name : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    public void setAllowedTypes(Set<IAEStackType<?>> allowedTypes) {
        this.allowedTypes = new HashSet<>(allowedTypes);
    }

    public void setVoidProtection(boolean v) {
        this.voidProtection = v;
    }

    public int getItemsProcessed() {
        return itemsProcessed;
    }

    public int getItemsSkipped() {
        return itemsSkipped;
    }

    public int getItemsGained() {
        return itemsGained;
    }

    public int getItemsLost() {
        return itemsLost;
    }

    public int getItemsUnchanged() {
        return itemsUnchanged;
    }

    public long getTotalGained() {
        return totalGained;
    }

    public long getTotalLost() {
        return totalLost;
    }

    public long getTotalStacksBefore() {
        return totalStacksBefore;
    }

    public long getTotalStacksAfter() {
        return totalStacksAfter;
    }

    public List<ItemChange> getLostItems() {
        return lostItems;
    }

    public List<ItemChange> getGainedItems() {
        return gainedItems;
    }

    public List<ItemChange> getAllChanges() {
        return changes;
    }

    public String getFullNameForTruncated(String truncatedName) {
        if (truncatedName == null) {
            return null;
        }

        String cleanTruncated = truncatedName.replace("…", "").replace("...", "").trim();

        return this.truncatedToFullName.get(cleanTruncated);
    }

    public void addTruncatedNameMapping(String truncatedName, String fullName) {
        if (truncatedName != null && fullName != null) {
            String cleanTruncated = truncatedName.replace("…", "").replace("...", "").trim();
            this.truncatedToFullName.put(cleanTruncated, fullName);
        }
    }

    public List<String> getHiddenItems(String sectionType) {
        return this.hiddenItemsMap.get(sectionType);
    }

    public void setHiddenItems(String sectionType, List<String> hiddenItems) {
        if (sectionType != null && hiddenItems != null) {
            this.hiddenItemsMap.put(sectionType, new ArrayList<>(hiddenItems));
        }
    }
}
