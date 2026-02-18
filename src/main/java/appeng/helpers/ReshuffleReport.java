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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * Tracks and reports the results of a reshuffle operation. Records the state of items before and after reshuffling to
 * show what changed.
 */
public class ReshuffleReport {

    // Approximate character width budget for the GUI text area.
    // Minecraft's default font: most chars ~6px wide, GUI panel ~660px wide -> ~38 chars.
    // Adjust this constant if your GUI panel is wider/narrower.
    private static final int LINE_WIDTH = 38;

    // Color codes matching scan report for consistency in both GUI and tooltips
    // Designed for good contrast against Minecraft's light-gray GUI background
    private static final String C_HEADER = "§3"; // dark aqua – section headers / decorators
    private static final String C_LABEL = "§3"; // dark aqua – field labels (matching scan report)
    private static final String C_VALUE = "§0"; // black – normal values (matching scan report)
    private static final String C_GAIN = "§2"; // dark green – positive / gained
    private static final String C_LOSS = "§4"; // dark red – negative / lost
    private static final String C_NEUTRAL = "§8"; // dark gray – secondary/footnote text (matching scan report)
    private static final String C_WARN = "§6"; // gold – warnings/percentages (matching scan report)
    private static final String C_ON = "§2"; // dark green – ON state (matching gain color)
    private static final String C_OFF = "§4"; // dark red – OFF state (matching loss color)
    private static final String C_RESET = "§r"; // reset

    // Tooltip colors - brighter/more vibrant for dark purple tooltip background
    // These match the scan report tooltip style for consistency
    private static final String CT_HEADER = "§b"; // bright aqua – section headers
    private static final String CT_LABEL = "§b"; // bright aqua – labels
    private static final String CT_VALUE = "§f"; // white – values
    private static final String CT_GAIN = "§a"; // bright green – gains
    private static final String CT_LOSS = "§c"; // bright red – losses
    private static final String CT_NEUTRAL = "§7"; // light gray – secondary text
    private static final String CT_WARN = "§e"; // yellow – warnings
    private static final String CT_DIVIDER = "§7"; // light gray – divider lines

    /**
     * Represents a single item's change during reshuffle
     */
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

    // Snapshot of storage before reshuffle (stack hash -> count)
    private final Map<String, Long> beforeSnapshot = new HashMap<>();
    private final Map<String, IAEStack<?>> stackLookup = new HashMap<>();

    // Statistics
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

    // Maps for tooltip support
    private final Map<String, String> truncatedToFullName = new HashMap<>(); // truncated -> full name
    private final Map<String, List<String>> hiddenItemsMap = new HashMap<>(); // "lost" or "gained" -> hidden items

    private Set<IAEStackType<?>> allowedTypes = new HashSet<>();
    private boolean voidProtection;
    private boolean overwriteProtection;
    private long startTime;
    private long endTime;

    public ReshuffleReport() {
        this.startTime = System.currentTimeMillis();
    }

    // -------------------------------------------------------------------------
    // Snapshot / generation logic (unchanged from original)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Formatted report lines (the main improvement)
    // -------------------------------------------------------------------------

    /**
     * Generates compact, color-coded report lines suitable for the GUI scroll panel.
     * <p>
     * Design goals:
     * <ul>
     * <li>Every logical line fits within {@value #LINE_WIDTH} visible characters so the GUI never wraps mid-word.</li>
     * <li>Large numbers are abbreviated (K / M / B) on lines that are already long, keeping full precision only where
     * the value stands alone.</li>
     * <li>Color scheme: dark-aqua headers, gray labels, white values, green/red for positive/negative deltas, dark-gray
     * for footnotes.</li>
     * </ul>
     */
    public List<String> generateReportLines() {
        List<String> lines = new ArrayList<>();
        long durationMs = endTime - startTime;

        // ── Header ──────────────────────────────────────────────────────────
        lines.add(C_HEADER + center("= Reshuffle Report =", LINE_WIDTH, '='));

        // Duration + mode on one line, abbrev if needed
        String modeStr = buildModeString();
        lines.add(
                C_LABEL + "Time: "
                        + C_VALUE
                        + String.format("%.2fs", durationMs / 1000.0)
                        + C_LABEL
                        + "  Mode: "
                        + C_VALUE
                        + modeStr);

        // Protection flags – two short tokens, always fit
        lines.add(
                C_LABEL + "Void: "
                        + (voidProtection ? C_ON + "ON" : C_OFF + "OFF")
                        + C_LABEL
                        + "  Overwrite: "
                        + (overwriteProtection ? C_ON + "ON" : C_OFF + "OFF"));

        // ── Processing ──────────────────────────────────────────────────────
        lines.add("");
        lines.add(C_HEADER + "-- Processing --");
        lines.add(
                C_LABEL + "Done: " + C_GAIN + fmt(itemsProcessed) + C_LABEL + "  Skip: " + C_WARN + fmt(itemsSkipped));

        // ── Storage totals ───────────────────────────────────────────────────
        // Each stat gets its own line so long numbers never wrap.
        lines.add("");
        lines.add(C_HEADER + "-- Storage Totals --");
        lines.add(buildTotalLine("Types:", totalItemTypesBefore, totalItemTypesAfter));
        lines.add(buildTotalLine("Stacks:", totalStacksBefore, totalStacksAfter));

        // ── Item changes ─────────────────────────────────────────────────────
        lines.add("");
        lines.add(C_HEADER + "-- Item Changes --");
        lines.add(C_GAIN + "+" + fmt(itemsGained) + " types  +" + abbrev(totalGained) + " items");
        lines.add(C_LOSS + "-" + fmt(itemsLost) + " types  -" + abbrev(totalLost) + " items");
        lines.add(C_NEUTRAL + "=" + fmt(itemsUnchanged) + " types unchanged");

        // ── Top lost ─────────────────────────────────────────────────────────
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

                // Store hidden items for tooltip (using tooltip colors for dark background)
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

        // ── Top gained ───────────────────────────────────────────────────────
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

                // Store hidden items for tooltip (using tooltip colors for dark background)
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

        // ── Integrity ────────────────────────────────────────────────────────
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

    /**
     * Generates tooltip-specific report lines with brighter colors optimized for dark tooltip background. This method
     * uses the same structure as generateReportLines() but with tooltip-appropriate colors.
     */
    public List<String> generateTooltipReportLines() {
        List<String> lines = new ArrayList<>();
        long durationMs = endTime - startTime;

        // ── Header ──────────────────────────────────────────────────────────
        lines.add(CT_HEADER + "═══════ Reshuffle Report ═══════");

        // Duration + mode on one line
        String modeStr = buildModeString();
        lines.add(
                CT_LABEL + "Time: "
                        + CT_VALUE
                        + String.format("%.2fs", durationMs / 1000.0)
                        + CT_LABEL
                        + "  Mode: "
                        + CT_VALUE
                        + modeStr);

        // Protection flags
        lines.add(
                CT_LABEL + "Void: "
                        + (voidProtection ? CT_GAIN + "ON" : CT_LOSS + "OFF")
                        + CT_LABEL
                        + "  Overwrite: "
                        + (overwriteProtection ? CT_GAIN + "ON" : CT_LOSS + "OFF"));

        // ── Processing ──────────────────────────────────────────────────────
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

        // ── Storage totals ───────────────────────────────────────────────────
        lines.add("");
        lines.add(CT_DIVIDER + "-- Storage Totals --");
        lines.add(buildTooltipTotalLine("Types:", totalItemTypesBefore, totalItemTypesAfter));
        lines.add(buildTooltipTotalLine("Stacks:", totalStacksBefore, totalStacksAfter));

        // ── Item changes ─────────────────────────────────────────────────────
        lines.add("");
        lines.add(CT_DIVIDER + "-- Item Changes --");
        lines.add(CT_GAIN + "+" + fmt(itemsGained) + " types  +" + abbrev(totalGained) + " items");
        lines.add(CT_LOSS + "-" + fmt(itemsLost) + " types  -" + abbrev(totalLost) + " items");
        lines.add(CT_NEUTRAL + "=" + fmt(itemsUnchanged) + " types unchanged");

        // ── Top lost (ALL items, not truncated) ─────────────────────────────
        if (!lostItems.isEmpty()) {
            lines.add("");
            lines.add(CT_LOSS + "-- Top Lost Items --");
            for (ItemChange c : lostItems) {
                lines.addAll(buildTooltipItemLines(c, CT_LOSS, "-"));
            }
        }

        // ── Top gained (ALL items, not truncated) ───────────────────────────
        if (!gainedItems.isEmpty() && totalGained > 0) {
            lines.add("");
            lines.add(CT_GAIN + "-- Top Gained Items --");
            for (ItemChange c : gainedItems) {
                lines.addAll(buildTooltipItemLines(c, CT_GAIN, "+"));
            }
        }

        // ── Integrity ────────────────────────────────────────────────────────
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

    // -------------------------------------------------------------------------
    // Private formatting helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a "before → after (delta)" line where numbers are abbreviated so the line stays within
     * {@value #LINE_WIDTH} visible chars.
     */
    private String buildTotalLine(String label, long before, long after) {
        long delta = after - before;
        // e.g. "Types: 6,897 → 6,895 (-2)"
        // Use abbreviated numbers to guarantee it fits.
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

    /**
     * Builds 1-2 lines for a single item change entry. Line 1: "• ItemName +delta" Line 2 (indented): "(before →
     * after)" – only if both numbers > abbrev threshold
     */
    private List<String> buildItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String fullName = getStackDisplayName(c.stack);
        String displayName = truncate(fullName, 20);
        String delta = abbrev(Math.abs(c.difference));

        // Store mapping if name was truncated
        if (!fullName.equals(displayName)) {
            addTruncatedNameMapping(displayName, fullName);
        }

        // Primary line: bullet + name + delta
        out.add(accentColor + "• " + C_VALUE + displayName + " " + accentColor + sign + delta);

        // Secondary line: full before/after for context (abbreviated)
        out.add(C_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    /** Builds the comma-separated mode string (abbreviated to short names). */
    private String buildModeString() {
        if (allowedTypes.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (IAEStackType<?> type : allowedTypes) {
            if (sb.length() > 0) sb.append('/');
            // Shorten class names: AEItemStackType -> Item, AEFluidStackType -> Fluid, etc.
            String n = type.getClass().getSimpleName().replace("AE", "").replace("StackType", "").replace("Stack", "");
            sb.append(n.isEmpty() ? "?" : n);
        }
        return sb.toString();
    }

    /**
     * Abbreviates large numbers: 1,200 -> 1.2K | 1,500,000 -> 1.5M | 2,000,000,000 -> 2.0B Numbers below 10 000 are
     * formatted with commas (full precision).
     */
    private static String abbrev(long v) {
        if (v < 0) return "-" + abbrev(-v);
        if (v >= 1_000_000_000L) return String.format("%.1fB", v / 1_000_000_000.0);
        if (v >= 1_000_000L) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 10_000L) return String.format("%.1fK", v / 1_000.0);
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }

    /** Like {@link #abbrev} but always prefixes with + or – sign. */
    private static String signedAbbrev(long v) {
        if (v > 0) return "+" + abbrev(v);
        if (v < 0) return "-" + abbrev(-v);
        return "0";
    }

    /** Full comma-formatted integer (for small standalone numbers). */
    private static String fmt(long v) {
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }

    /** Returns the color code appropriate for a delta value. */
    private static String diffColor(long delta) {
        if (delta > 0) return C_GAIN;
        if (delta < 0) return C_LOSS;
        return C_NEUTRAL;
    }

    /** Returns the tooltip color code appropriate for a delta value. */
    private static String diffColorTooltip(long delta) {
        if (delta > 0) return CT_GAIN;
        if (delta < 0) return CT_LOSS;
        return CT_NEUTRAL;
    }

    /**
     * Builds a "before → after (delta)" line for tooltips using bright colors.
     */
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

    /**
     * Builds 1-2 lines for a single item change entry in tooltip (bright colors).
     */
    private List<String> buildTooltipItemLines(ItemChange c, String accentColor, String sign) {
        List<String> out = new ArrayList<>();
        String fullName = getStackDisplayName(c.stack);
        String delta = abbrev(Math.abs(c.difference));

        // Primary line: bullet + name + delta (no truncation for tooltip)
        out.add(accentColor + "• " + CT_VALUE + fullName + " " + accentColor + sign + delta);

        // Secondary line: full before/after for context
        out.add(CT_NEUTRAL + "  (" + abbrev(c.beforeCount) + " -> " + abbrev(c.afterCount) + ")");
        return out;
    }

    /** Centers {@code text} within {@code width} chars, padding with {@code pad}. */
    private static String center(String text, int width, char pad) {
        int totalPad = Math.max(0, width - text.length());
        int left = totalPad / 2, right = totalPad - left;
        return repeat(pad, left) + text + repeat(pad, right);
    }

    /** Returns a string of {@code count} copies of character {@code c}. */
    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        char[] buf = new char[count];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }

    /**
     * Truncates a string to {@code maxLen} visible characters, appending "…" if cut. Color codes (§X) are not counted
     * toward the length.
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "Unknown";
        // Strip color codes for length measurement
        String plain = s.replaceAll("§.", "");
        if (plain.length() <= maxLen) return s;
        return plain.substring(0, maxLen - 1) + "…";
    }

    // -------------------------------------------------------------------------
    // Stack key + display name (unchanged from original)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Legacy / deprecated
    // -------------------------------------------------------------------------

    /** @deprecated Use {@link #generateReportLines()} and render in GUI. */
    @Deprecated
    public void sendToPlayer(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        for (String line : generateReportLines()) {
            player.addChatMessage(new ChatComponentText(line));
        }
    }

    // -------------------------------------------------------------------------
    // Setters / Getters
    // -------------------------------------------------------------------------

    public void setAllowedTypes(Set<IAEStackType<?>> allowedTypes) {
        this.allowedTypes = new HashSet<>(allowedTypes);
    }

    public void setVoidProtection(boolean v) {
        this.voidProtection = v;
    }

    public void setOverwriteProtection(boolean v) {
        this.overwriteProtection = v;
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

    /**
     * Get the full item name from a truncated version (used for tooltips).
     *
     * @param truncatedName The truncated name (without "...")
     * @return The full item name, or null if not found
     */
    public String getFullNameForTruncated(String truncatedName) {
        if (truncatedName == null) {
            return null;
        }

        // Clean up the truncated name (remove Unicode ellipsis "…" or ASCII "..." if present)
        String cleanTruncated = truncatedName.replace("…", "").replace("...", "").trim();

        return this.truncatedToFullName.get(cleanTruncated);
    }

    /**
     * Store a mapping from truncated name to full name (called during report generation).
     *
     * @param truncatedName The truncated name shown in the report
     * @param fullName      The full item name
     */
    public void addTruncatedNameMapping(String truncatedName, String fullName) {
        if (truncatedName != null && fullName != null) {
            String cleanTruncated = truncatedName.replace("…", "").replace("...", "").trim();
            this.truncatedToFullName.put(cleanTruncated, fullName);
        }
    }

    /**
     * Get the list of hidden items for a section ("lost" or "gained").
     *
     * @param sectionType Either "lost" or "gained"
     * @return List of hidden item lines, or null if not found
     */
    public List<String> getHiddenItems(String sectionType) {
        return this.hiddenItemsMap.get(sectionType);
    }

    /**
     * Store the list of hidden items for a section (called during report generation).
     *
     * @param sectionType Either "lost" or "gained"
     * @param hiddenItems List of formatted item lines that were hidden
     */
    public void setHiddenItems(String sectionType, List<String> hiddenItems) {
        if (sectionType != null && hiddenItems != null) {
            this.hiddenItemsMap.put(sectionType, new ArrayList<>(hiddenItems));
        }
    }
}
