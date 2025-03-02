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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.item.AEItemStack;

public class ContainerPatternTerm extends ContainerPatternTermBase
        implements IOptionalSlotHost, IContainerCraftingPacket {

    private final SlotPatternTerm craftSlot;

    public ContainerPatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);

        this.CRAFTING_GRID_PAGES = 1;
        this.CRAFTING_GRID_WIDTH = 3;
        this.CRAFTING_GRID_HEIGHT = 3;
        this.CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

        this.patternTerminal = (PartPatternTerminal) monitorable;

        this.craftingSlots = new SlotFakeCraftingMatrix[9];
        this.outputSlots = new OptionalSlotFake[3];
        this.cOut = new AppEngInternalInventory(null, 1);

        final IInventory patternInv = this.getPatternTerminal().getInventoryByName("pattern");
        final IInventory output = this.getPatternTerminal().getInventoryByName("output");

        this.crafting = this.getPatternTerminal().getInventoryByName("crafting");

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                this.addSlotToContainer(
                        this.craftingSlots[x + y * 3] = new SlotFakeCraftingMatrix(
                                this.crafting,
                                x + y * 3,
                                18 + x * 18,
                                -76 + y * 18));
            }
        }

        this.addSlotToContainer(
                this.craftSlot = new SlotPatternTerm(
                        ip.player,
                        this.getActionSource(),
                        this.getPowerSource(),
                        monitorable,
                        this.crafting,
                        patternInv,
                        this.cOut,
                        110,
                        -76 + 18,
                        this,
                        2,
                        this));
        this.craftSlot.setIIcon(-1);

        for (int y = 0; y < 3; y++) {
            this.addSlotToContainer(
                    this.outputSlots[y] = new SlotPatternOutputs(output, this, y, 110, -76 + y * 18, 0, 0, 1));
            ((OptionalSlotFake) this.outputSlots[y]).setRenderDisabled(false);
        }

        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternInv,
                        0,
                        147,
                        -72 - 9,
                        this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternInv,
                        1,
                        147,
                        -72 + 34,
                        this.getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);

        this.bindPlayerInventory(ip, 0, 0);
        this.updateOrderOfOutputSlots();
        if (getPatternTerminal().hasRefillerUpgrade()) refillBlankPatterns(patternSlotIN);
    }

    private void updateOrderOfOutputSlots() {
        if (!this.isCraftingMode()) {
            this.craftSlot.xDisplayPosition = -9000;

            for (int y = 0; y < 3; y++) {
                this.outputSlots[y].xDisplayPosition = this.outputSlots[y].getX();
            }
        } else {
            this.craftSlot.xDisplayPosition = this.craftSlot.getX();

            for (int y = 0; y < 3; y++) {
                this.outputSlots[y].xDisplayPosition = -9000;
            }
        }
    }

    @Override
    public void putStackInSlot(final int par1, final ItemStack par2ItemStack) {
        super.putStackInSlot(par1, par2ItemStack);
        this.getAndUpdateOutput();
    }

    @Override
    public void putStacksInSlots(final ItemStack[] par1ArrayOfItemStack) {
        super.putStacksInSlots(par1ArrayOfItemStack);
        this.getAndUpdateOutput();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if (idx == 1) {
            return Platform.isServer() ? !this.getPatternTerminal().isCraftingRecipe() : !this.isCraftingMode();
        } else if (idx == 2) {
            return Platform.isServer() ? this.getPatternTerminal().isCraftingRecipe() : this.isCraftingMode();
        } else {
            return false;
        }
    }

    public void craftOrGetItem(final PacketPatternSlot packetPatternSlot) {
        if (packetPatternSlot.slotItem != null && this.getCellInventory() != null) {
            final IAEItemStack out = packetPatternSlot.slotItem.copy();
            InventoryAdaptor inv = new AdaptorPlayerHand(this.getPlayerInv().player);
            final InventoryAdaptor playerInv = InventoryAdaptor
                    .getAdaptor(this.getPlayerInv().player, ForgeDirection.UNKNOWN);

            if (packetPatternSlot.shift) {
                inv = playerInv;
            }

            if (inv.simulateAdd(out.getItemStack()) != null) {
                return;
            }

            final IAEItemStack extracted = Platform
                    .poweredExtraction(this.getPowerSource(), this.getCellInventory(), out, this.getActionSource());
            final EntityPlayer p = this.getPlayerInv().player;

            if (extracted != null) {
                inv.addItems(extracted.getItemStack());
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
                return;
            }

            final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
            final InventoryCrafting real = new InventoryCrafting(new ContainerNull(), 3, 3);

            for (int x = 0; x < 9; x++) {
                ic.setInventorySlotContents(
                        x,
                        packetPatternSlot.pattern[x] == null ? null : packetPatternSlot.pattern[x].getItemStack());
            }

            final IRecipe r = Platform.findMatchingRecipe(ic, p.worldObj);

            if (r == null) {
                return;
            }

            final IMEMonitor<IAEItemStack> storage = this.getPatternTerminal().getItemInventory();
            final IItemList<IAEItemStack> all = storage.getStorageList();

            final ItemStack is = r.getCraftingResult(ic);

            for (int x = 0; x < ic.getSizeInventory(); x++) {
                if (ic.getStackInSlot(x) != null) {
                    final ItemStack pulled = Platform.extractItemsByRecipe(
                            this.getPowerSource(),
                            this.getActionSource(),
                            storage,
                            p.worldObj,
                            r,
                            is,
                            ic,
                            ic.getStackInSlot(x),
                            x,
                            all,
                            Actionable.MODULATE,
                            ItemViewCell.createFilter(this.getViewCells()));
                    real.setInventorySlotContents(x, pulled);
                }
            }

            final IRecipe rr = Platform.findMatchingRecipe(real, p.worldObj);

            if (rr == r && Platform.isSameItemPrecise(rr.getCraftingResult(real), is)) {
                final SlotCrafting sc = new SlotCrafting(p, real, this.cOut, 0, 0, 0);
                sc.onPickupFromSlot(p, is);

                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = playerInv.addItems(real.getStackInSlot(x));

                    if (failed != null) {
                        p.dropPlayerItemWithRandomChoice(failed, false);
                    }
                }

                inv.addItems(is);
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
            } else {
                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = real.getStackInSlot(x);
                    if (failed != null) {
                        this.getCellInventory().injectItems(
                                AEItemStack.create(failed),
                                Actionable.MODULATE,
                                new MachineSource(this.getPatternTerminal()));
                    }
                }
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            if (this.isCraftingMode() != this.getPatternTerminal().isCraftingRecipe()) {
                this.setCraftingMode(this.getPatternTerminal().isCraftingRecipe());
                this.updateOrderOfOutputSlots();
            }

            this.substitute = this.patternTerminal.isSubstitution();
            this.beSubstitute = this.patternTerminal.canBeSubstitution();
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);

        if (field.equals("craftingMode")) {
            this.getAndUpdateOutput();
            this.updateOrderOfOutputSlots();
        }
    }

    @Override
    protected void setCraftingMode(boolean craftingMode) {
        this.craftingMode = craftingMode;
    }
}
