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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.SecurityPermissions;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.implementations.GuiStorageReshuffle;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
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

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        this.voidProtection = this.tile.isVoidProtection();

        final boolean wasRunning = this.reshuffleRunning;
        this.reshuffleRunning = this.tile.isReshuffleRunning();
        this.reshuffleTotalItems = this.tile.getReshuffleTotalItems();
        this.reshuffleProgress = this.tile.getReshuffleProgress();
        this.reshuffleProcessedItems = this.tile.getReshuffleProcessedItems();

        if (wasRunning && !this.reshuffleRunning) {
            this.reportData = String.join("\n", this.tile.getReshuffleReport());
        } else {
            final String current = String.join("\n", this.tile.getReshuffleReport());
            if (!current.equals(this.reportData)) {
                this.reportData = current;
            }
        }

        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("reportData")) {
            if (Minecraft.getMinecraft().currentScreen instanceof GuiStorageReshuffle gui) {
                gui.onReportUpdated();
            }
        }
    }

    public boolean startReshuffle(EntityPlayer player, boolean confirmed) {
        return this.tile.startReshuffle(player, confirmed);
    }

    public void cancelReshuffle() {
        this.tile.cancelReshuffle();
    }

    public void performNetworkScan() {
        if (this.reshuffleRunning) return;
        if (!this.hasAccess(SecurityPermissions.BUILD, false)) return;
        this.tile.scanNetwork();
        this.reportData = String.join("\n", this.tile.getReshuffleReport());
    }

    public List<String> getReportLines() {
        if (this.reportData.isEmpty()) return new ArrayList<>();
        return Arrays.asList(this.reportData.split("\n", -1));
    }
}
