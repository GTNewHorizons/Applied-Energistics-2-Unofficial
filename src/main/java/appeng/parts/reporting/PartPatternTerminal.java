/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.reporting;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.PatternHelper;
import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.BiggerAppEngInventory;
import appeng.tile.inventory.InvOperation;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class PartPatternTerminal extends AbstractPartTerminal {
    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartPatternTerm_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartPatternTerm_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartPatternTerm_Colored;

    private final AppEngInternalInventory crafting = new BiggerAppEngInventory(this, 9) {};

    private final AppEngInternalInventory output = new BiggerAppEngInventory(this, 3) {};

    private final AppEngInternalInventory pattern = new AppEngInternalInventory(this, 2);

    private boolean craftingMode = true;
    private boolean substitute = false;
    private boolean beSubstitute = false;

    @Reflected
    public PartPatternTerminal(final ItemStack is) {
        super(is);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (final ItemStack is : this.pattern) {
            if (is != null) {
                drops.add(is);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.setCraftingRecipe(data.getBoolean("craftingMode"));
        this.setSubstitution(data.getBoolean("substitute"));
        this.setCanBeSubstitution(data.getBoolean("beSubstitute"));
        this.pattern.readFromNBT(data, "pattern");
        this.output.readFromNBT(data, "outputList");
        this.crafting.readFromNBT(data, "craftingGrid");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("craftingMode", this.craftingMode);
        data.setBoolean("substitute", this.substitute);
        data.setBoolean("beSubstitute", this.beSubstitute);
        this.pattern.writeToNBT(data, "pattern");
        this.output.writeToNBT(data, "outputList");
        this.crafting.writeToNBT(data, "craftingGrid");
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
    public void onChangeInventory(
            final IInventory inv,
            final int slot,
            final InvOperation mc,
            final ItemStack removedStack,
            final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {

            final ItemStack stack = this.pattern.getStackInSlot(1);

            if (stack != null && stack.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) stack.getItem();
                final NBTTagCompound encodedValue = stack.getTagCompound();

                if (encodedValue != null) {
                    final ICraftingPatternDetails details = pattern.getPatternForItem(
                            stack, this.getHost().getTile().getWorldObj());
                    final boolean substitute = encodedValue.getBoolean("substitute");
                    final boolean beSubstitute = encodedValue.getBoolean("beSubstitute");
                    final boolean isCrafting = encodedValue.getBoolean("crafting");
                    final IAEItemStack[] inItems;
                    final IAEItemStack[] outItems;

                    if (details == null) {
                        inItems = PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), true, null);
                        outItems =
                                PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), true, null);
                    } else {
                        inItems = details.getInputs();
                        outItems = details.getOutputs();
                    }

                    this.setCraftingRecipe(isCrafting);
                    this.setSubstitution(substitute);
                    this.setCanBeSubstitution(beSubstitute);

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

    public boolean isSubstitution() {
        return this.substitute;
    }

    public boolean canBeSubstitution() {
        return this.beSubstitute;
    }

    public void setSubstitution(boolean canSubstitute) {
        this.substitute = canSubstitute;
    }

    public void setCanBeSubstitution(boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("crafting")) {
            return this.crafting;
        }

        if (name.equals("output")) {
            return this.output;
        }

        if (name.equals("pattern")) {
            return this.pattern;
        }

        return super.getInventoryByName(name);
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
        return FRONT_DARK_ICON;
    }
}
