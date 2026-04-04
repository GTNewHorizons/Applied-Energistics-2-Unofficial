/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.inv.IUpgradeInventory;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public abstract class UpgradeInventory extends AppEngInternalInventory
        implements IAEAppEngInventory, IUpgradeInventory {

    private final IAEAppEngInventory parent;

    private boolean cached = false;
    private final Object2IntOpenHashMap<Upgrades> installedCounts = new Object2IntOpenHashMap<>();

    public UpgradeInventory(final IAEAppEngInventory parent, final int s) {
        super(null, s);
        this.setTileEntity(this);
        this.parent = parent;
    }

    @Override
    protected boolean eventsEnabled() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isItemValidForSlot(final int i, final ItemStack itemstack) {
        if (itemstack == null) {
            return false;
        }
        final Item it = itemstack.getItem();
        if (it instanceof IUpgradeModule) {
            final Upgrades u = ((IUpgradeModule) it).getType(itemstack);
            if (u != null) {
                return this.getInstalledUpgrades(u) < this.getMaxInstalled(u);
            }
        }
        return false;
    }

    public int getInstalledUpgrades(final Upgrades u) {
        if (!this.cached) {
            this.updateUpgradeInfo();
        }

        return this.installedCounts.getInt(u);
    }

    private void updateUpgradeInfo() {
        this.cached = true;
        this.installedCounts.clear();

        for (final ItemStack is : this) {
            if (is == null || is.getItem() == null || !(is.getItem() instanceof IUpgradeModule card)) {
                continue;
            }

            final Upgrades myUpgrade = card.getType(is);
            this.installedCounts.addTo(myUpgrade, 1);
        }

        for (final var entry : this.installedCounts.object2IntEntrySet()) {
            entry.setValue(Math.min(entry.getIntValue(), this.getMaxInstalled(entry.getKey())));
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound target) {
        super.readFromNBT(target);
        this.updateUpgradeInfo();
    }

    @Override
    public void saveChanges() {
        this.parent.saveChanges();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        this.cached = false;
        if (this.parent != null && Platform.isServer()) {
            this.parent.onChangeInventory(inv, slot, mc, removedStack, newStack);
        }
    }
}
