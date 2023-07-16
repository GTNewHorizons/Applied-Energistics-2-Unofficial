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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.PatternHelper;
import appeng.helpers.Reflected;
import appeng.tile.inventory.InvOperation;

public class PartPatternTerminal extends AbstractPartPatternTerm {

    private boolean craftingMode = true;

    @Reflected
    public PartPatternTerminal(final ItemStack is) {
        super(is, 9, 3);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.setCraftingRecipe(data.getBoolean("craftingMode"));
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("craftingMode", this.craftingMode);
    }

    @Override
    public GuiBridge getGui(final EntityPlayer p) {
        int x = (int) p.posX;
        int y = (int) p.posY;
        int z = (int) p.posZ;
        if (this.getHost().getTile() != null) {
            x = this.getTile().xCoord;
            y = this.getTile().yCoord;
            z = this.getTile().zCoord;
        }

        if (GuiBridge.GUI_PATTERN_TERMINAL.hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), p)) {
            return GuiBridge.GUI_PATTERN_TERMINAL;
        }
        return GuiBridge.GUI_ME;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {

            final ItemStack stack = this.pattern.getStackInSlot(1);

            if (stack != null && stack.getItem() instanceof ICraftingPatternItem pattern) {
                final NBTTagCompound encodedValue = stack.getTagCompound();

                if (encodedValue != null) {
                    final ICraftingPatternDetails details = pattern
                            .getPatternForItem(stack, this.getHost().getTile().getWorldObj());
                    final boolean substitute = encodedValue.getBoolean("substitute");
                    final boolean beSubstitute = encodedValue.getBoolean("beSubstitute");
                    final boolean isCrafting = encodedValue.getBoolean("crafting");
                    final IAEItemStack[] inItems;
                    final IAEItemStack[] outItems;

                    if (details == null) {
                        inItems = PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems = PatternHelper
                                .loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), true, null);
                    } else {
                        inItems = details.getInputs();
                        outItems = details.getOutputs();
                    }

                    this.setCraftingRecipe(isCrafting);
                    this.substitute = substitute;
                    this.beSubstitute = beSubstitute;

                    for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                        this.crafting.setInventorySlotContents(x, null);
                    }

                    for (int x = 0; x < this.output.getSizeInventory(); x++) {
                        this.output.setInventorySlotContents(x, null);
                    }

                    for (int x = 0; x < this.crafting.getSizeInventory() && x < inItems.length; x++) {
                        if (inItems[x] != null) {
                            this.crafting.setInventorySlotContents(x, inItems[x].getItemStack());
                        }
                    }

                    for (int x = 0; x < this.output.getSizeInventory() && x < outItems.length; x++) {
                        if (outItems[x] != null) {
                            this.output.setInventorySlotContents(x, outItems[x].getItemStack());
                        }
                    }
                }
            }
        } else if (inv == this.crafting) {
            this.fixCraftingRecipes();
        }

        this.getHost().markForSave();
    }

    private void fixCraftingRecipes() {
        if (this.craftingMode) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                final ItemStack is = this.crafting.getStackInSlot(x);
                if (is != null) {
                    is.stackSize = 1;
                }
            }
        }
    }

    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        this.fixCraftingRecipes();
    }

}
