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
import net.minecraft.util.EnumChatFormatting;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * Tracks and reports the results of a reshuffle operation. Records the state of items before and after reshuffling to
 * show what changed.
 */
public class ReshuffleReport {

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

    private Set<IAEStackType<?>> allowedTypes = new HashSet<>();
    private boolean voidProtection;
    private boolean overwriteProtection;
    private long startTime;
    private long endTime;

    public ReshuffleReport() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Takes a snapshot of the current storage state before reshuffling.
     */
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

    /**
     * Compares current storage state with the before snapshot and generates the report.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void generateReport(Map<IAEStackType<?>, IMEMonitor<?>> monitors,
            java.util.Set<IAEStackType<?>> allowedTypes, int processed, int skipped) {
        this.endTime = System.currentTimeMillis();
        this.itemsProcessed = processed;
        this.itemsSkipped = skipped;

        Map<String, Long> afterSnapshot = new HashMap<>();
        totalStacksAfter = 0;
        totalItemTypesAfter = 0;

        // Build after snapshot
        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor monitor = monitors.get(type);
            if (monitor == null) continue;

            IItemList storageList = monitor.getStorageList();
            for (Object obj : storageList) {
                IAEStack<?> stack = (IAEStack<?>) obj;
                if (stack != null && stack.getStackSize() > 0) {
                    String key = getStackKey(stack);
                    afterSnapshot.put(key, stack.getStackSize());
                    if (!stackLookup.containsKey(key)) {
                        stackLookup.put(key, stack.copy());
                    }
                    totalStacksAfter += stack.getStackSize();
                    totalItemTypesAfter++;
                }
            }
        }

        // Compare snapshots
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

        // Sort by absolute difference (largest changes first)
        lostItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
        gainedItems.sort((a, b) -> Long.compare(Math.abs(b.difference), Math.abs(a.difference)));
    }

    /**
     * Gets a unique key for a stack (for comparison purposes). This should identify the item TYPE, not the quantity.
     */
    private String getStackKey(IAEStack<?> stack) {
        // Create a key based on item identity, NOT quantity
        // For AEItemStack: use item ID + damage + NBT hash
        // For AEFluidStack: use fluid ID + NBT hash
        if (stack instanceof appeng.util.item.AEItemStack itemStack) {
            net.minecraft.item.ItemStack is = itemStack.getItemStack();
            if (is != null) {
                String key = net.minecraft.item.Item.itemRegistry.getNameForObject(is.getItem()) + "@"
                        + is.getItemDamage();
                if (is.hasTagCompound()) {
                    key += "#" + is.getTagCompound().hashCode();
                }
                return "item:" + key;
            }
        } else if (stack instanceof appeng.util.item.AEFluidStack fluidStack) {
            net.minecraftforge.fluids.FluidStack fs = fluidStack.getFluidStack();
            if (fs != null && fs.getFluid() != null) {
                String key = fs.getFluid().getName();
                if (fs.tag != null) {
                    key += "#" + fs.tag.hashCode();
                }
                return "fluid:" + key;
            }
        }
        // Fallback for other types - still use toString but try to strip quantity
        String str = stack.toString();
        // Try to remove leading quantity like "64x"
        if (str.matches("^\\d+x.*")) {
            str = str.replaceFirst("^\\d+x", "");
        }
        return stack.getStackType().getClass().getSimpleName() + ":" + str;
    }

    /**
     * Generates the report as a list of formatted strings for GUI display.
     *
     * @return List of formatted strings with color codes
     */
    public List<String> generateReportLines() {
        List<String> lines = new ArrayList<>();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        long durationMs = endTime - startTime;
        double durationSec = durationMs / 1000.0;

        // Header
        lines.add("");
        lines.add("§3═══════ Reshuffle Report ═══════"); // Dark aqua instead of gold

        // Summary stats
        lines.add("§bDuration: §7" + String.format("%.2fs", durationSec)); // Aqua -> gray

        // Format allowed types string
        StringBuilder typesStr = new StringBuilder();
        for (IAEStackType<?> type : allowedTypes) {
            if (typesStr.length() > 0) typesStr.append(", ");
            String typeName = type.getClass().getSimpleName().replace("AE", "").replace("StackType", "");
            typesStr.append(typeName);
        }

        lines.add(
                "§bMode: §7" + (typesStr.length() > 0 ? typesStr.toString() : "None") // Aqua -> gray
                        + "§8 | Void Protection: " // Dark gray
                        + (voidProtection ? "§2ON" : "§cOFF") // Dark green / red
                        + "§8 | Overwrite Protection: "
                        + (overwriteProtection ? "§2ON" : "§cOFF"));

        lines.add("");
        lines.add("§3── Processing Stats ──"); // Dark aqua
        lines.add("§7  Processed: §2" + nf.format(itemsProcessed) + "§8 | Skipped: §6" + nf.format(itemsSkipped)); // Gray,
                                                                                                                   // dark
                                                                                                                   // green,
                                                                                                                   // dark
                                                                                                                   // gray,
                                                                                                                   // gold

        // Before/After comparison
        lines.add("");
        lines.add("§3── Storage Totals ──"); // Dark aqua
        lines.add(
                "§7  Item Types: §8" + nf.format(totalItemTypesBefore) // Gray, dark gray
                        + " → §7"
                        + nf.format(totalItemTypesAfter)
                        + getDifferenceColorCode(totalItemTypesAfter - totalItemTypesBefore)
                        + " ("
                        + formatDifference(totalItemTypesAfter - totalItemTypesBefore)
                        + ")");
        lines.add(
                "§7  Total Stacks: §8" + nf.format(totalStacksBefore) // Gray, dark gray
                        + " → §7"
                        + nf.format(totalStacksAfter)
                        + getDifferenceColorCode(totalStacksAfter - totalStacksBefore)
                        + " ("
                        + formatDifference(totalStacksAfter - totalStacksBefore)
                        + ")");

        // Changes summary
        lines.add("");
        lines.add("§3── Item Changes ──"); // Dark aqua
        lines.add("§2  Gained: " + nf.format(itemsGained) + " types (+" + nf.format(totalGained) + " items)"); // Dark
                                                                                                               // green
        lines.add("§c  Lost: " + nf.format(itemsLost) + " types (-" + nf.format(totalLost) + " items)"); // Red
        lines.add("§8  Unchanged: " + nf.format(itemsUnchanged) + " types"); // Dark gray

        // Show top lost items (if any)
        if (!lostItems.isEmpty()) {
            lines.add("");
            lines.add("§c── Top Lost Items ──");
            int shown = 0;
            for (ItemChange change : lostItems) {
                if (shown >= 5) {
                    int remaining = lostItems.size() - 5;
                    if (remaining > 0) {
                        lines.add("§8  ... and " + remaining + " more"); // Dark gray
                    }
                    break;
                }
                lines.add(
                        "§c  • §7" + getStackDisplayName(change.stack) // Red, gray
                                + "§c -"
                                + nf.format(Math.abs(change.difference))
                                + "§8 (" // Dark gray
                                + nf.format(change.beforeCount)
                                + " → "
                                + nf.format(change.afterCount)
                                + ")");
                shown++;
            }
        }

        // Show top gained items
        if (!gainedItems.isEmpty() && totalGained > 0) {
            lines.add("");
            lines.add("§2── Top Gained Items ──"); // Dark green
            int shown = 0;
            for (ItemChange change : gainedItems) {
                if (shown >= 5) {
                    int remaining = gainedItems.size() - 5;
                    if (remaining > 0) {
                        lines.add("§8  ... and " + remaining + " more"); // Dark gray
                    }
                    break;
                }
                lines.add(
                        "§2  • §7" + getStackDisplayName(change.stack) // Dark green, gray
                                + "§2 +"
                                + nf.format(change.difference)
                                + "§8 (" // Dark gray
                                + nf.format(change.beforeCount)
                                + " → "
                                + nf.format(change.afterCount)
                                + ")");
                shown++;
            }
        }

        // Integrity check
        long netChange = totalStacksAfter - totalStacksBefore;
        if (netChange != 0) {
            lines.add("");
            lines.add(
                    "§6⚠ §7Net change: " + getDifferenceColorCode(netChange) // Gold warning, gray text
                            + formatDifference(netChange)
                            + " items§8 (may be due to ongoing crafts or network activity)"); // Dark gray
        } else {
            lines.add("");
            lines.add("§2✓ §7No net change - storage integrity verified"); // Dark green, gray
        }

        lines.add("§3═══════════════════════════════"); // Dark aqua
        return lines;
    }

    /**
     * Sends the report to the player via chat messages (deprecated - use generateReportLines for GUI).
     *
     * @deprecated Use generateReportLines() instead and display in GUI
     */
    @Deprecated
    public void sendToPlayer(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;

        // Use the new method to generate lines, then send to chat
        List<String> lines = generateReportLines();
        for (String line : lines) {
            player.addChatMessage(new ChatComponentText(line));
        }
    }

    private String getDifferenceColorCode(long diff) {
        if (diff > 0) return "§a";
        else if (diff < 0) return "§c";
        else return "§7";
    }

    private String getStackDisplayName(IAEStack<?> stack) {
        if (stack == null) return "Unknown";
        try {
            String displayName = stack.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            // Fallback for items without proper display name
            return "Unknown Item";
        } catch (Exception e) {
            return "Unknown Item";
        }
    }

    private String formatDifference(long diff) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        if (diff > 0) return "+" + nf.format(diff);
        else if (diff < 0) return nf.format(diff);
        else return "0";
    }

    private EnumChatFormatting getDifferenceColor(long diff) {
        if (diff > 0) return EnumChatFormatting.GREEN;
        else if (diff < 0) return EnumChatFormatting.RED;
        else return EnumChatFormatting.GRAY;
    }

    // Setters for configuration info
    public void setAllowedTypes(Set<IAEStackType<?>> allowedTypes) {
        this.allowedTypes = new HashSet<>(allowedTypes);
    }

    public void setVoidProtection(boolean voidProtection) {
        this.voidProtection = voidProtection;
    }

    public void setOverwriteProtection(boolean overwriteProtection) {
        this.overwriteProtection = overwriteProtection;
    }

    // Getters for statistics
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
}
