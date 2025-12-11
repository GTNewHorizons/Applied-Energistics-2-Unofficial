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

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.inventory.InvOperation;

public class PartPatternTerminal extends AbstractPartTerminal implements IIAEStackInventory, IPatternTerminal {

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartPatternTerm_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartPatternTerm_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartPatternTerm_Colored;

    public static final int patternInputsWidth = 3;
    public static final int patternInputsHeigh = 3;
    public static final int patternInputsPages = 1;

    public static final int patternOutputsWidth = 1;
    public static final int patternOutputsHeigh = 3;
    public static final int patternOutputPages = 1;

    private final IAEStackInventory crafting = new IAEStackInventory(
            this,
            getPatternInputsWidth() * getPatternInputsHeigh() * getPatternInputPages(),
            StorageName.CRAFTING_INPUT);
    private final IAEStackInventory output = new IAEStackInventory(
            this,
            getPatternOutputsWidth() * getPatternOutputsHeigh() * getPatternOutputPages(),
            StorageName.CRAFTING_OUTPUT);
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
        super.getDrops(drops, wrenched);

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

        if (getPatternGui().hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), p)) {
            return getPatternGui();
        }
        return GuiBridge.GUI_ME;
    }

    protected GuiBridge getPatternGui() {
        return GuiBridge.GUI_PATTERN_TERMINAL;
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {
            final ItemStack stack = this.pattern.getStackInSlot(1);
            loadPatternFromItem(stack, this.host.getTile().getWorldObj(), this.crafting, this.output);
        }

        this.getHost().markForSave();
    }

    @Override
    public void saveAEStackInv() {
        this.getHost().markForSave();
    }

    public void exPatternTerminalCall(IAEStack<?>[] in, IAEStack<?>[] out) {}

    @Override
    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
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
        if (name.equals("pattern")) {
            return this.pattern;
        }

        return super.getInventoryByName(name);
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        switch (name) {
            case CRAFTING_INPUT -> {
                return this.crafting;
            }
            case CRAFTING_OUTPUT -> {
                return this.output;
            }
        }
        return null;
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

    protected int getPatternInputsWidth() {
        return patternInputsWidth;
    }

    protected int getPatternInputsHeigh() {
        return patternInputsHeigh;
    }

    protected int getPatternInputPages() {
        return patternInputsPages;
    }

    protected int getPatternOutputsWidth() {
        return patternOutputsWidth;
    }

    protected int getPatternOutputsHeigh() {
        return patternOutputsHeigh;
    }

    protected int getPatternOutputPages() {
        return patternOutputPages;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return AEApi.instance().definitions().parts().patternTerminal().maybeStack(1).orNull();
    }
}
