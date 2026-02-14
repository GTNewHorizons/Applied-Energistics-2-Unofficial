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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import appeng.api.config.ReshuffleMode;
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

    private ReshuffleMode mode;
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
     * Sends the report to the player via chat messages.
     */
    public void sendToPlayer(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        long durationMs = endTime - startTime;
        double durationSec = durationMs / 1000.0;

        // Header
        player.addChatMessage(new ChatComponentText(""));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "═══════ Reshuffle Report ═══════"));

        // Summary stats
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.YELLOW + "Duration: "
                                + EnumChatFormatting.WHITE
                                + String.format("%.2fs", durationSec)));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.YELLOW + "Mode: "
                                + EnumChatFormatting.WHITE
                                + mode.name()
                                + EnumChatFormatting.GRAY
                                + " | Void Protection: "
                                + (voidProtection ? EnumChatFormatting.GREEN + "ON" : EnumChatFormatting.RED + "OFF")
                                + EnumChatFormatting.GRAY
                                + " | Overwrite Protection: "
                                + (overwriteProtection ? EnumChatFormatting.GREEN + "ON"
                                        : EnumChatFormatting.RED + "OFF")));

        player.addChatMessage(new ChatComponentText(""));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "── Processing Stats ──"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.WHITE + "  Processed: "
                                + EnumChatFormatting.GREEN
                                + nf.format(itemsProcessed)
                                + EnumChatFormatting.GRAY
                                + " | Skipped: "
                                + EnumChatFormatting.YELLOW
                                + nf.format(itemsSkipped)));

        // Before/After comparison
        player.addChatMessage(new ChatComponentText(""));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "── Storage Totals ──"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.WHITE + "  Item Types: "
                                + EnumChatFormatting.GRAY
                                + nf.format(totalItemTypesBefore)
                                + " → "
                                + EnumChatFormatting.WHITE
                                + nf.format(totalItemTypesAfter)
                                + getDifferenceColor(totalItemTypesAfter - totalItemTypesBefore)
                                + " ("
                                + formatDifference(totalItemTypesAfter - totalItemTypesBefore)
                                + ")"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.WHITE + "  Total Stacks: "
                                + EnumChatFormatting.GRAY
                                + nf.format(totalStacksBefore)
                                + " → "
                                + EnumChatFormatting.WHITE
                                + nf.format(totalStacksAfter)
                                + getDifferenceColor(totalStacksAfter - totalStacksBefore)
                                + " ("
                                + formatDifference(totalStacksAfter - totalStacksBefore)
                                + ")"));

        // Changes summary
        player.addChatMessage(new ChatComponentText(""));
        player.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "── Item Changes ──"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.GREEN + "  Gained: "
                                + nf.format(itemsGained)
                                + " types (+"
                                + nf.format(totalGained)
                                + " items)"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.RED + "  Lost: "
                                + nf.format(itemsLost)
                                + " types (-"
                                + nf.format(totalLost)
                                + " items)"));
        player.addChatMessage(
                new ChatComponentText(
                        EnumChatFormatting.GRAY + "  Unchanged: " + nf.format(itemsUnchanged) + " types"));

        // Show top lost items (if any)
        if (!lostItems.isEmpty()) {
            player.addChatMessage(new ChatComponentText(""));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "── Top Lost Items ──"));
            int shown = 0;
            for (ItemChange change : lostItems) {
                if (shown >= 5) {
                    int remaining = lostItems.size() - 5;
                    if (remaining > 0) {
                        player.addChatMessage(
                                new ChatComponentText(EnumChatFormatting.GRAY + "  ... and " + remaining + " more"));
                    }
                    break;
                }
                player.addChatMessage(
                        new ChatComponentText(
                                EnumChatFormatting.RED + "  • "
                                        + EnumChatFormatting.WHITE
                                        + getStackDisplayName(change.stack)
                                        + EnumChatFormatting.RED
                                        + " -"
                                        + nf.format(Math.abs(change.difference))
                                        + EnumChatFormatting.GRAY
                                        + " ("
                                        + nf.format(change.beforeCount)
                                        + " → "
                                        + nf.format(change.afterCount)
                                        + ")"));
                shown++;
            }
        }

        // Show top gained items (if any - this would indicate new items appeared, which is unusual)
        if (!gainedItems.isEmpty() && totalGained > 0) {
            player.addChatMessage(new ChatComponentText(""));
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "── Top Gained Items ──"));
            int shown = 0;
            for (ItemChange change : gainedItems) {
                if (shown >= 5) {
                    int remaining = gainedItems.size() - 5;
                    if (remaining > 0) {
                        player.addChatMessage(
                                new ChatComponentText(EnumChatFormatting.GRAY + "  ... and " + remaining + " more"));
                    }
                    break;
                }
                player.addChatMessage(
                        new ChatComponentText(
                                EnumChatFormatting.GREEN + "  • "
                                        + EnumChatFormatting.WHITE
                                        + getStackDisplayName(change.stack)
                                        + EnumChatFormatting.GREEN
                                        + " +"
                                        + nf.format(change.difference)
                                        + EnumChatFormatting.GRAY
                                        + " ("
                                        + nf.format(change.beforeCount)
                                        + " → "
                                        + nf.format(change.afterCount)
                                        + ")"));
                shown++;
            }
        }

        // Integrity check
        long netChange = totalStacksAfter - totalStacksBefore;
        if (netChange != 0) {
            player.addChatMessage(new ChatComponentText(""));
            player.addChatMessage(
                    new ChatComponentText(
                            EnumChatFormatting.GOLD + "⚠ "
                                    + EnumChatFormatting.YELLOW
                                    + "Net change: "
                                    + getDifferenceColor(netChange)
                                    + formatDifference(netChange)
                                    + " items"
                                    + EnumChatFormatting.GRAY
                                    + " (may be due to ongoing crafts or network activity)"));
        } else {
            player.addChatMessage(new ChatComponentText(""));
            player.addChatMessage(
                    new ChatComponentText(
                            EnumChatFormatting.GREEN + "✓ "
                                    + EnumChatFormatting.WHITE
                                    + "No net change - storage integrity verified"));
        }

        player.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD + "═══════════════════════════════"));
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
    public void setMode(ReshuffleMode mode) {
        this.mode = mode;
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
