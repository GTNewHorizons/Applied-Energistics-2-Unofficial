/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import static net.minecraft.item.Item.itemRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.helpers.CellScanTask;
import appeng.tile.misc.TileStorageReshuffle;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class ContainerStorageReshuffle extends AEBaseContainer {

    private final TileStorageReshuffle tile;

    @GuiSync(0)
    public boolean voidProtection;

    @GuiSync(1)
    public boolean reshuffleRunning = false;

    @GuiSync(2)
    public int reshuffleTotalItems = 0;

    @GuiSync(3)
    public String reportData = "";

    @GuiSync(4)
    public int reshuffleProgress = 0;

    @GuiSync(5)
    public int reshuffleProcessedItems = 0;

    @GuiSync(6)
    public boolean scanMode = false;

    @GuiSync(7)
    public boolean reshuffleFailed = false;

    @GuiSync(8)
    public boolean reshuffleCancelled = false;

    @GuiSync(9)
    public boolean reshuffleComplete = false;

    @GuiSync(10)
    public String scanData = "";

    public ContainerStorageReshuffle(final InventoryPlayer ip, final TileStorageReshuffle te) {
        super(ip, te);
        this.tile = te;
        this.voidProtection = this.tile.isVoidProtection();
    }

    public Reference2BooleanMap<IAEStackType<?>> getTypeFilters() {
        return this.tile.getTypeFilter(this.getPlayerInv().player);
    }

    public void toggleTypeFilter(final String typeId) {
        final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
        if (type == null) return;
        final Reference2BooleanMap<IAEStackType<?>> map = this.tile.getTypeFilter(this.getPlayerInv().player);
        map.put(type, !map.getBoolean(type));
        this.tile.saveTypeFilter();
    }

    public void toggleVoidProtection() {
        this.tile.getConfigManager()
                .putSetting(Settings.VOID_PROTECTION, this.tile.isVoidProtection() ? YesNo.NO : YesNo.YES);
    }

    public void setScanMode(final boolean scan) {
        this.scanMode = scan;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        this.voidProtection = this.tile.isVoidProtection();

        this.reshuffleRunning = this.tile.isReshuffleRunning();
        this.reshuffleFailed = this.tile.isReshuffleFailed();
        this.reshuffleCancelled = this.tile.isReshuffleCancelled();
        this.reshuffleComplete = this.tile.isReshuffleComplete();
        this.reshuffleTotalItems = this.tile.getReshuffleTotalItems();
        this.reshuffleProgress = this.tile.getReshuffleProgress();
        this.reshuffleProcessedItems = this.tile.getReshuffleProcessedItems();

        final String current = this.tile.getReshuffleReport();
        if (!current.equals(this.reportData)) {
            this.reportData = current;
        }

        final String currentScan = encodeScanData(this.tile.getScanDuplicates());
        if (!currentScan.equals(this.scanData)) {
            this.scanData = currentScan;
        }

        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
            if (field.equals("reportData")) {
                gui.onReportUpdated();
            } else if (field.equals("scanData")) {
                gui.onScanUpdated();
            }
        }
    }

    public void startReshuffle(EntityPlayer player, boolean confirmed) {
        this.tile.startReshuffle(player, confirmed);
    }

    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    public void performNetworkScan() {
        if (this.reshuffleRunning) return;
        if (!this.hasAccess(SecurityPermissions.BUILD, false)) return;
        this.tile.scanNetwork();
    }

    private static String encodeScanData(final Map<String, List<CellScanTask.CellRecord>> duplicates) {
        final List<String> lines = new ArrayList<>();
        for (final List<CellScanTask.CellRecord> cells : duplicates.values()) {
            if (cells.isEmpty()) continue;
            final CellScanTask.CellRecord first = cells.get(0);
            if (first.partitionedItemStacks.isEmpty()) continue;

            final ItemStack repItem = first.partitionedItemStacks.get(0);
            final String repId = itemRegistry.getNameForObject(repItem.getItem());
            if (repId == null) continue;

            final StringBuilder sb = new StringBuilder();
            sb.append(repId).append('@').append(repItem.getItemDamage()).append('@').append(cells.size());

            for (final CellScanTask.CellRecord cell : cells) {
                sb.append('@').append(cell.x).append(',').append(cell.y).append(',').append(cell.z).append(',')
                        .append(cell.dim).append(',').append(cell.slot).append(',').append((int) cell.typesUsed)
                        .append('|').append(cell.cellDisplayName);
            }
            lines.add(sb.toString());
        }
        return String.join("\n", lines);
    }

    public List<String> getReportLines() {
        if (this.reportData.isEmpty()) return new ArrayList<>();
        return Arrays.asList(this.reportData.split("\n", -1));
    }

    public String getScanData() {
        return this.scanData;
    }
}
