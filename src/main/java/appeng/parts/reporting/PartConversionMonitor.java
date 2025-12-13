/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.reporting;

import static appeng.server.ServerHelper.EXTRA_ACTION_KEY;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.texture.CableBusTextures;
import appeng.core.AELog;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.InventoryAdaptor;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;

public class PartConversionMonitor extends AbstractPartMonitor {

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartConversionMonitor_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartConversionMonitor_Dark;
    private static final CableBusTextures FRONT_DARK_ICON_LOCKED = CableBusTextures.PartConversionMonitor_Dark_Locked;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartConversionMonitor_Colored;

    @Reflected
    public PartConversionMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final Vec3 pos) {
        if (player.worldObj.isRemote) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        IAEStack<?> displayed = this.getDisplayed();
        if (displayed == null) {
            if (!EXTRA_ACTION_KEY.isKeyDown(player)) {
                this.injectItemToMonitor(player);
            } else {
                this.injectExtraToMonitor(player);
            }
        } else {
            if (displayed instanceof IAEItemStack) {
                this.injectItemToMonitor(player);
            } else if (displayed.getStackType().isContainerItemForType(player.getCurrentEquippedItem())) {
                this.injectExtraToMonitor(player);
            }
        }

        return true;
    }

    @Override
    protected void onLockedInteraction(EntityPlayer player, boolean isShiftDown) {
        IAEStack<?> displayed = getDisplayed();
        if (displayed == null) return;

        if (displayed instanceof IAEItemStack) {
            this.extractItemFromMonitor(player);
        } else {
            this.extractExtraFromMonitor(player);
        }
    }

    protected void injectItemToMonitor(final EntityPlayer player) {
        boolean injectFromInventory = false;

        ItemStack item = player.getCurrentEquippedItem();
        if (item == null && this.getDisplayed() != null) {
            injectFromInventory = true;
            item = ((IAEItemStack) this.getDisplayed()).getItemStack();
        }

        if (item != null) {
            try {
                if (!this.getProxy().isActive()) {
                    return;
                }

                final IEnergySource energy = this.getProxy().getEnergy();
                final IMEMonitor<IAEItemStack> cell = this.getProxy().getStorage().getItemInventory();
                final IAEItemStack input = AEItemStack.create(item);

                if (injectFromInventory) { // From player inventory
                    for (int x = 0; x < player.inventory.getSizeInventory(); x++) {
                        final ItemStack targetStack = player.inventory.getStackInSlot(x);
                        if (input.equals(targetStack)) {
                            final IAEItemStack insertItem = input.copy();
                            insertItem.setStackSize(targetStack.stackSize);
                            final IAEItemStack failedToInsert = Platform
                                    .poweredInsert(energy, cell, insertItem, new PlayerSource(player, this));
                            player.inventory.setInventorySlotContents(
                                    x,
                                    failedToInsert == null ? null : failedToInsert.getItemStack());
                        }
                    }
                } else { // From player hand
                    final IAEItemStack failedToInsert = Platform
                            .poweredInsert(energy, cell, input, new PlayerSource(player, this));
                    player.inventory.setInventorySlotContents(
                            player.inventory.currentItem,
                            failedToInsert == null ? null : failedToInsert.getItemStack());
                }
            } catch (final GridAccessException e) {
                AELog.debug(e);
            }
        }
    }

    protected void extractItemFromMonitor(final EntityPlayer player) {
        if (this.getDisplayed() instanceof IAEItemStack input) {
            try {
                if (!this.getProxy().isActive()) {
                    return;
                }

                final IEnergySource energy = this.getProxy().getEnergy();
                final IMEMonitor<IAEItemStack> cell = this.getProxy().getStorage().getItemInventory();

                final ItemStack is = input.getItemStack();
                input.setStackSize(is.getMaxStackSize());

                final IAEItemStack retrieved = Platform
                        .poweredExtraction(energy, cell, input, new PlayerSource(player, this));
                if (retrieved != null) {
                    ItemStack newItems = retrieved.getItemStack();
                    final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player, ForgeDirection.UNKNOWN);
                    newItems = adaptor.addItems(newItems);
                    if (newItems != null) {
                        final TileEntity te = this.getTile();
                        final List<ItemStack> list = Collections.singletonList(newItems);
                        Platform.spawnDrops(
                                player.worldObj,
                                te.xCoord + this.getSide().offsetX,
                                te.yCoord + this.getSide().offsetY,
                                te.zCoord + this.getSide().offsetZ,
                                list);
                    }

                    if (player.openContainer != null) {
                        player.openContainer.detectAndSendChanges();
                    }
                }
            } catch (final GridAccessException e) {
                AELog.debug(e);
            }
        }
    }

    private void injectExtraToMonitor(final EntityPlayer player) {
        final ItemStack hand = player.getCurrentEquippedItem();
        if (hand == null) return;

        for (IAEStackType type : AEStackTypeRegistry.getAllTypes()) {
            if (!type.isContainerItemForType(hand)) continue;

            IAEStack<?> stack = type.getStackFromContainerItem(hand);
            if (stack == null || stack.getStackSize() <= 0
                    || (this.getDisplayed() != null && !this.getDisplayed().isSameType(stack)))
                return;

            try {
                final IEnergySource energy = this.getProxy().getEnergy();
                final IMEMonitor monitor = this.getProxy().getStorage().getMEMonitor(type);
                if (monitor == null) return;

                IAEStack<?> leftover = Platform.poweredInsert(energy, monitor, stack, new PlayerSource(player, this));
                ItemStack result;
                if (leftover == null) {
                    result = type.clearFilledContainer(hand);
                } else {
                    stack.decStackSize(leftover.getStackSize());
                    type.drainStackFromContainer(hand, stack);
                    result = hand;
                }

                if (hand.stackSize == 1) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
                } else {
                    hand.stackSize--;
                    if (result != null && !player.inventory.addItemStackToInventory(result)) {
                        player.entityDropItem(result, 0);
                    }
                }
            } catch (GridAccessException e) {
                AELog.error(e);
            }

            return;
        }
    }

    private void extractExtraFromMonitor(final EntityPlayer player) {
        IAEStack<?> displayed = getDisplayed();
        if (displayed == null) return;

        final ItemStack hand = player.getCurrentEquippedItem();
        if (hand == null) return;

        for (IAEStackType type : AEStackTypeRegistry.getAllTypes()) {
            if (!type.isContainerItemForType(hand)) continue;

            try {
                final IEnergySource energy = this.getProxy().getEnergy();
                final IMEMonitor monitor = this.getProxy().getStorage().getMEMonitor(type);
                if (monitor == null) return;

                IAEStack<?> stored = monitor.getAvailableItem(displayed, IterationCounter.fetchNewId());
                long amountToFill = type.fillContainer(hand.copy(), stored).rightLong();

                IAEStack<?> extracted = Platform.poweredExtraction(
                        energy,
                        monitor,
                        stored.copy().setStackSize(amountToFill),
                        new PlayerSource(player, this));
                if (extracted == null) return;

                ObjectLongPair<ItemStack> filled = type.fillContainer(hand.copy(), extracted);
                ItemStack result = filled.left();
                if (hand.stackSize == 1) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, result);
                } else {
                    hand.stackSize--;
                    if (result != null && !player.inventory.addItemStackToInventory(result)) {
                        player.entityDropItem(result, 0);
                    }

                    if (player.openContainer != null) {
                        player.openContainer.detectAndSendChanges();
                    }
                }
            } catch (final GridAccessException e) {
                AELog.error(e);
            }

            return;
        }
    }

    @Override
    public CableBusTextures getFrontBright() {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return this.isLocked() ? FRONT_DARK_ICON_LOCKED : FRONT_DARK_ICON;
    }
}
