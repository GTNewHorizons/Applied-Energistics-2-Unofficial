package appeng.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.core.localization.GuiText;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.IAEStackList;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class ReshuffleReport implements IGuiPacketWritable {

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

        public void write(ByteBuf out) {
            Platform.writeStackByte(this.stack, out);
            out.writeLong(this.beforeCount);
            out.writeLong(this.afterCount);
        }

        public static ItemChange read(ByteBuf in) {
            return new ItemChange(Platform.readStackByte(in), in.readLong(), in.readLong());
        }
    }

    public enum ChangeType {
        GAINED,
        LOST,
        UNCHANGED
    }

    public final Set<IAEStackType<?>> allowedTypes;
    public final boolean voidProtection;
    public final long startTime;
    public long endTime;

    public int totalItemTypesBefore = 0;
    public int totalItemTypesAfter = 0;

    public long totalStacksBefore = 0;
    public long totalStacksAfter = 0;

    public int itemsProcessed = 0;
    public int itemsSkipped = 0;

    public final IItemList<IAEStack<?>> beforeSnapshot = new IAEStackList(), afterSnapshot = new IAEStackList(),
            stackLookup = new IAEStackList();

    public final IItemList<IAEStack<?>> skippedItemsList = new IAEStackList();

    public int itemsGained = 0;
    public int itemsLost = 0;
    public int itemsUnchanged = 0;
    public long totalGained = 0;
    public long totalLost = 0;

    public final List<ItemChange> lostItems = new ArrayList<>();
    public final List<ItemChange> gainedItems = new ArrayList<>();

    public ReshuffleReport(final Set<IAEStackType<?>> allowedTypes, final boolean voidProtection) {
        this.allowedTypes = allowedTypes;
        this.voidProtection = voidProtection;
        this.startTime = System.currentTimeMillis();
    }

    // For IGuiPacketWritable
    public ReshuffleReport(final ByteBuf buf) {
        this.allowedTypes = new HashSet<>();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            if (buf.readBoolean()) {
                this.allowedTypes.add(AEStackTypeRegistry.getType(typeId));
            }
        }

        this.voidProtection = buf.readBoolean();
        this.startTime = buf.readLong();
        this.endTime = buf.readLong();

        this.totalItemTypesBefore = buf.readInt();
        this.totalItemTypesAfter = buf.readInt();

        this.totalStacksBefore = buf.readLong();
        this.totalStacksAfter = buf.readLong();

        this.itemsProcessed = buf.readInt();
        this.itemsSkipped = buf.readInt();

        for (int i = 0; i < buf.readInt(); i++) {
            this.skippedItemsList.add(Platform.readStackByte(buf));
        }

        for (int i = 0; i < buf.readInt(); i++) {
            this.lostItems.add(ItemChange.read(buf));
        }

        for (int i = 0; i < buf.readInt(); i++) {
            this.gainedItems.add(ItemChange.read(buf));
        }
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        buf.writeInt(AEStackTypeRegistry.getAllTypes().size());
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            ByteBufUtils.writeUTF8String(buf, type.getId());
            buf.writeBoolean(this.allowedTypes.contains(type));
        }

        buf.writeBoolean(this.voidProtection);
        buf.writeLong(this.startTime);
        buf.writeLong(this.endTime);

        buf.writeInt(this.totalItemTypesBefore);
        buf.writeInt(this.totalItemTypesAfter);

        buf.writeLong(this.totalStacksBefore);
        buf.writeLong(this.totalStacksAfter);

        buf.writeInt(this.itemsProcessed);
        buf.writeInt(this.itemsSkipped);

        buf.writeInt(this.skippedItemsList.size());
        this.skippedItemsList.forEach(stack -> Platform.writeStackByte(stack, buf));

        buf.writeInt(this.lostItems.size());
        this.lostItems.forEach(stack -> stack.write(buf));

        buf.writeInt(this.gainedItems.size());
        this.gainedItems.forEach(stack -> stack.write(buf));
    }

    public void snapshotBefore(Map<IAEStackType<?>, IMEMonitor<?>> monitors, Set<IAEStackType<?>> allowedTypes) {
        beforeSnapshot.resetStatus();
        stackLookup.resetStatus();
        totalStacksBefore = 0;
        totalItemTypesBefore = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor<?> monitor = monitors.get(type);
            if (monitor == null) continue;

            for (IAEStack<?> stack : monitor.getStorageList()) {
                if (stack != null && stack.getStackSize() > 0) {
                    beforeSnapshot.add(stack);
                    stackLookup.add(stack);
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
        this.skippedItemsList.resetStatus();
        if (skippedStacks != null) {
            for (IAEStack<?> s : skippedStacks) {
                this.skippedItemsList.add(s.copy());
            }
        }

        totalStacksAfter = 0;
        totalItemTypesAfter = 0;

        for (IAEStackType<?> type : allowedTypes) {
            IMEMonitor<?> monitor = monitors.get(type);
            if (monitor == null) continue;

            final IItemList<?> storageList = monitor.getStorageList();
            for (IAEStack<?> stack : storageList) {
                if (stack != null && stack.getStackSize() > 0) {
                    this.afterSnapshot.add(stack);
                    if (stackLookup.findPrecise(stack) == null) stackLookup.add(stack);
                    totalStacksAfter += stack.getStackSize();
                    totalItemTypesAfter++;
                }
            }
        }

        for (IAEStack<?> lookup : stackLookup) {
            final IAEStack<?> before = beforeSnapshot.findPrecise(lookup);
            final IAEStack<?> after = afterSnapshot.findPrecise(lookup);

            ItemChange change = new ItemChange(lookup, before.getStackSize(), after.getStackSize());

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

    @SideOnly(Side.CLIENT)
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

    final static ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;

    private static String abbrev(long v) {
        return converter.toWideReadableForm(v);
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
