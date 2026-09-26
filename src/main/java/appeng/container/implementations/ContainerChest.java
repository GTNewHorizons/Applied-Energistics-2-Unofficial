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

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.handlers.ConfigEnumSyncHandler;
import appeng.tile.storage.TileChest;
import appeng.util.Platform;

public class ContainerChest extends AEBaseContainer {

    private final TileChest chest;
    private final ConfigEnumSyncHandler<AccessRestriction> reshuffleAccessSync;

    public ContainerChest(final InventoryPlayer ip, final TileChest chest) {
        super(ip, chest);
        this.chest = chest;
        this.reshuffleAccessSync = this.syncRegistrar().configEnum(
                "reshuffleAccess",
                Settings.RESHUFFLE_ACCESS,
                AccessRestriction.class,
                chest.getConfigManager());

        this.addSlotToContainer(
                new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.STORAGE_CELLS,
                        this.chest,
                        1,
                        80,
                        37,
                        this.getInventoryPlayer()));

        this.bindPlayerInventory(ip, 0, 166 - /* height of player inventory */ 82);
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.reshuffleAccessSync.syncFromConfig();
        }
        super.detectAndSendChanges();
    }

    public AccessRestriction getReshuffleAccess() {
        return this.reshuffleAccessSync.get();
    }
}
