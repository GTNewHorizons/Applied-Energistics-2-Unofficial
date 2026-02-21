/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import appeng.api.storage.ICellCacheRegistry;
import appeng.core.localization.GuiText;
import appeng.helpers.CellScanTask.CellKey;
import appeng.helpers.CellScanTask.CellRecord;
import appeng.helpers.CellScanTask.Summary;

public class CellScanReport {

    private static final String DARK_BLUE = "§1";
    private static final String DARK_GRAY = "§8";
    private static final String GOLD = "§6";
    private static final String DARK_RED = "§4";
    private static final String BLUE = "§9";
    private static final String DARK_PURPLE = "§5";

    private final List<CellRecord> cells;

    public CellScanReport(List<CellRecord> cells) {
        this.cells = cells;
    }

    public List<String> generateReportLines() {
        final List<String> lines = new ArrayList<>();
        final NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        final Summary overall = CellScanTask.summarize(cells);
        final List<CellRecord> regularCells = CellScanTask.filterNonSingularityCells(cells);
        final List<CellRecord> itemCells = CellScanTask.filterByType(cells, ICellCacheRegistry.TYPE.ITEM);
        final List<CellRecord> fluidCells = CellScanTask.filterByType(cells, ICellCacheRegistry.TYPE.FLUID);
        final List<CellRecord> essentiaCells = CellScanTask.filterByType(cells, ICellCacheRegistry.TYPE.ESSENTIA);

        lines.add(DARK_BLUE + "═══════ " + t(GuiText.StorageScan) + " ═══════");
        lines.add("");

        lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanSummary) + " ──");
        lines.add(
                DARK_BLUE + t(GuiText.StorageScanCells)
                        + ": "
                        + DARK_GRAY
                        + nf.format(overall.numCells)
                        + " ("
                        + nf.format(overall.numEmpty)
                        + " "
                        + t(GuiText.StorageScanEmpty)
                        + ")");
        lines.add("");

        addTypeSummaryLine(lines, nf, GuiText.Items, itemCells);
        addTypeSummaryLine(lines, nf, GuiText.Fluids, fluidCells);
        addTypeSummaryLine(lines, nf, GuiText.Essentias, essentiaCells);
        lines.add("");

        lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanUtilization) + " ──");
        addBytesUtilLines(lines, GuiText.StorageScanBytesAll, overall);
        if (!regularCells.isEmpty()) {
            addBytesUtilLines(lines, GuiText.StorageScanBytesExclSing, CellScanTask.summarize(regularCells));
        }
        lines.add("");

        lines.add(
                DARK_BLUE + t(GuiText.Types)
                        + ": "
                        + DARK_GRAY
                        + nf.format((long) overall.sumTypesUsed)
                        + " / "
                        + nf.format((long) overall.sumTypesTotal));
        lines.add(
                DARK_GRAY + "  "
                        + t(GuiText.StorageScanUtil)
                        + ": "
                        + GOLD
                        + String.format("%.1f%%", overall.weightedTypesUtil * 100)
                        + DARK_GRAY
                        + " ("
                        + t(GuiText.StorageScanMedian)
                        + ": "
                        + GOLD
                        + String.format("%.1f%%)", overall.typesP50 * 100));
        lines.add("");

        final List<CellRecord> framedCells = regularCells.stream().filter(CellRecord::typeLocked)
                .collect(Collectors.toList());
        if (!framedCells.isEmpty()) {
            final long totalWasted = framedCells.stream().mapToLong(c -> (long) c.typeLockedBytes()).sum();
            lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanFragmentation) + " ──");
            lines.add(
                    DARK_BLUE + t(GuiText.StorageScanLocked)
                            + ": "
                            + DARK_RED
                            + nf.format(framedCells.size())
                            + DARK_GRAY
                            + " "
                            + t(GuiText.StorageScanCells));
            lines.add(
                    DARK_BLUE + t(GuiText.StorageScanWasted) + ": " + DARK_RED + CellScanTask.formatBytes(totalWasted));
            lines.add(DARK_GRAY + t(GuiText.StorageScanSingularityExcluded));
            lines.add("");
        }

        final Map<CellKey, List<CellRecord>> byType = CellScanTask.groupByType(cells);
        if (!byType.isEmpty()) {
            final List<Map.Entry<CellKey, List<CellRecord>>> sorted = new ArrayList<>(byType.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

            lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanCellTypes) + " ──");
            for (Map.Entry<CellKey, List<CellRecord>> entry : sorted) {
                lines.add(
                        DARK_BLUE + entry.getKey().displayName
                                + channelSuffix(entry.getKey().cellType)
                                + ": "
                                + DARK_GRAY
                                + nf.format(entry.getValue().size()));
            }
            lines.add("");

            lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanCellTypesUtilization) + " ──");
            for (Map.Entry<CellKey, List<CellRecord>> entry : sorted) {
                final Summary s = CellScanTask.summarize(entry.getValue());
                lines.add(
                        DARK_BLUE + entry.getKey().displayName
                                + channelSuffix(entry.getKey().cellType)
                                + ": "
                                + GOLD
                                + String.format("%.1f%%", s.weightedBytesUtil * 100));
            }
            lines.add("");
        }

        final Map<String, List<CellRecord>> duplicates = CellScanTask.findDuplicatePartitionedCells(cells);
        if (!duplicates.isEmpty()) {
            lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanDuplicatePartitions) + " ──");
            for (List<CellRecord> dupes : duplicates.values()) {
                final String partitionList = dupes.get(0).partitionedItems.isEmpty() ? "Empty"
                        : String.join(", ", dupes.get(0).partitionedItems);
                lines.add(
                        DARK_RED + "• "
                                + DARK_GRAY
                                + dupes.size()
                                + " "
                                + t(GuiText.StorageScanCells)
                                + " "
                                + t(GuiText.StorageScanLockedTo)
                                + ":");
                lines.add(DARK_GRAY + "  " + partitionList);
                lines.add(DARK_BLUE + "  -- " + t(GuiText.StorageScanLocations) + " --");
                for (CellRecord cell : dupes) {
                    final String device = cell.deviceType.equals("DRIVE") ? t(GuiText.StorageScanDrive)
                            : t(GuiText.StorageScanChest);
                    lines.add(
                            DARK_GRAY + "    "
                                    + device
                                    + " "
                                    + cell.deviceId.substring(cell.deviceId.indexOf('@') + 1)
                                    + " #"
                                    + cell.slot);
                }
            }
            lines.add("");
        }

        final List<CellRecord> fragmented = CellScanTask.getTopFragmented(regularCells, 3);
        if (!fragmented.isEmpty()) {
            lines.add(DARK_BLUE + "── " + t(GuiText.StorageScanMostFragmented) + " ──");
            for (CellRecord cell : fragmented) {
                final String device = cell.deviceType.equals("DRIVE") ? t(GuiText.StorageScanDrive)
                        : t(GuiText.StorageScanChest);
                lines.add(
                        DARK_GRAY + "• "
                                + device
                                + " "
                                + cell.deviceId.substring(cell.deviceId.indexOf('@') + 1)
                                + " #"
                                + cell.slot
                                + ": "
                                + DARK_RED
                                + CellScanTask.formatBytes(cell.typeLockedBytes()));
            }
            lines.add("");
        }

        lines.add(DARK_BLUE + "═════════════════════");
        lines.add(DARK_GRAY + t(GuiText.StorageScanExplainLocked));
        lines.add(DARK_GRAY + t(GuiText.StorageScanExplainWasted));
        lines.add(DARK_GRAY + t(GuiText.StorageScanExplainFragmented));
        return lines;
    }

    private void addTypeSummaryLine(List<String> lines, NumberFormat nf, GuiText label, List<CellRecord> typeCells) {
        if (typeCells.isEmpty()) return;
        final Summary s = CellScanTask.summarize(typeCells);
        lines.add(
                DARK_BLUE + t(label)
                        + ": "
                        + DARK_GRAY
                        + nf.format(typeCells.size())
                        + " "
                        + t(GuiText.StorageScanCells)
                        + " "
                        + GOLD
                        + String.format("(%.1f%% " + t(GuiText.StorageScanUtil) + ")", s.weightedBytesUtil * 100));
    }

    private void addBytesUtilLines(List<String> lines, GuiText label, Summary s) {
        lines.add(
                DARK_BLUE + t(label)
                        + ": "
                        + DARK_GRAY
                        + CellScanTask.formatBytes(s.sumBytesUsed)
                        + " / "
                        + CellScanTask.formatBytes(s.sumBytesTotal));
        lines.add(
                DARK_GRAY + "  "
                        + t(GuiText.StorageScanUtil)
                        + ": "
                        + GOLD
                        + String.format("%.1f%%", s.weightedBytesUtil * 100)
                        + DARK_GRAY
                        + " ("
                        + t(GuiText.StorageScanMedian)
                        + ": "
                        + GOLD
                        + String.format("%.1f%%)", s.bytesP50 * 100));
    }

    private static String t(GuiText text) {
        return text.getLocal();
    }

    private static String channelSuffix(ICellCacheRegistry.TYPE type) {
        switch (type) {
            case FLUID:
                return BLUE + " (F)";
            case ESSENTIA:
                return DARK_PURPLE + " (E)";
            default:
                return " (I)";
        }
    }
}
