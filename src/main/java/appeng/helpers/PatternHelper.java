/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public class PatternHelper implements ICraftingPatternDetails, Comparable<PatternHelper> {

    private final ItemStack patternItem;
    private final InventoryCrafting crafting = new InventoryCrafting(new ContainerNull(), 3, 3);
    private final InventoryCrafting testFrame = new InventoryCrafting(new ContainerNull(), 3, 3);
    private final ItemStack correctOutput;
    private final IRecipe standardRecipe;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final boolean isCrafting;
    private final boolean canSubstitute;
    private final boolean canBeSubstitute;
    private final Set<TestLookup> failCache = new HashSet<>();
    private final Set<TestLookup> passCache = new HashSet<>();
    private final IAEItemStack pattern;
    private int priority = 0;

    public PatternHelper(final ItemStack is, final World w) {
        final NBTTagCompound encodedValue = is.getTagCompound();

        if (encodedValue == null) {
            throw new IllegalArgumentException("No pattern here!");
        }

        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        this.isCrafting = encodedValue.getBoolean("crafting");

        this.canSubstitute = encodedValue.getBoolean("substitute");
        this.canBeSubstitute = encodedValue.getBoolean("beSubstitute");
        this.patternItem = is;
        this.pattern = AEItemStack.create(is);

        final List<IAEItemStack> in = new ArrayList<>();
        final List<IAEItemStack> out = new ArrayList<>();

        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            final ItemStack gs = Platform.loadItemStackFromNBT(tag);

            if (gs == null && !tag.hasNoTags()) {
                throw new IllegalStateException("No pattern here!");
            }

            if (this.isCrafting) // processing recipes are not looked up
            {
                this.crafting.setInventorySlotContents(x, gs);
            }

            if (gs != null && (!this.isCrafting || !gs.hasTagCompound())) {
                this.markItemAs(x, gs, TestStatus.ACCEPT);
            }

            in.add(AEApi.instance().storage().createItemStack(gs));
            if (this.isCrafting) // processing recipes are not tested anyway
            {
                this.testFrame.setInventorySlotContents(x, gs);
            }
        }

        if (this.isCrafting) {
            this.standardRecipe = Platform.findMatchingRecipe(this.crafting, w);

            if (this.standardRecipe != null) {
                this.correctOutput = this.standardRecipe.getCraftingResult(this.crafting);
                out.add(AEApi.instance().storage().createItemStack(this.correctOutput));
            } else {
                throw new IllegalStateException("No pattern here!");
            }
        } else {
            this.standardRecipe = null;
            this.correctOutput = null;

            for (int x = 0; x < outTag.tagCount(); x++) {
                final NBTTagCompound tag = outTag.getCompoundTagAt(x);
                final ItemStack gs = Platform.loadItemStackFromNBT(tag);

                if (gs != null) {
                    out.add(AEApi.instance().storage().createItemStack(gs));
                } else if (!tag.hasNoTags()) {
                    throw new IllegalStateException("No pattern here!");
                }
            }
        }

        this.outputs = out.toArray(new IAEItemStack[0]);
        this.inputs = in.toArray(new IAEItemStack[0]);

        this.condensedInputs = convertToCondensedList(this.inputs);
        this.condensedOutputs = convertToCondensedList(this.outputs);

        if (condensedInputs.length == 0 || condensedOutputs.length == 0) {
            throw new IllegalStateException("No pattern here!");
        }
    }

    private void markItemAs(final int slotIndex, final ItemStack i, final TestStatus b) {
        if (b == TestStatus.TEST || i.hasTagCompound()) {
            return;
        }

        (b == TestStatus.ACCEPT ? this.passCache : this.failCache).add(new TestLookup(slotIndex, i));
    }

    @Override
    public ItemStack getPattern() {
        return this.patternItem;
    }

    @Override
    public synchronized boolean isValidItemForSlot(final int slotIndex, final ItemStack i, final World w) {
        if (!this.isCrafting) {
            throw new IllegalStateException("Only crafting recipes supported.");
        }

        final TestStatus result = this.getStatus(slotIndex, i);

        switch (result) {
            case ACCEPT -> {
                return true;
            }
            case DECLINE -> {
                return false;
            }
            default -> {}
        }

        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            this.testFrame.setInventorySlotContents(x, this.crafting.getStackInSlot(x));
        }

        this.testFrame.setInventorySlotContents(slotIndex, i);

        if (this.standardRecipe.matches(this.testFrame, w)) {
            final ItemStack testOutput = this.standardRecipe.getCraftingResult(this.testFrame);

            if (Platform.isSameItemPrecise(this.correctOutput, testOutput)) {
                this.testFrame.setInventorySlotContents(slotIndex, this.crafting.getStackInSlot(slotIndex));
                this.markItemAs(slotIndex, i, TestStatus.ACCEPT);
                return true;
            }
        } else {
            final ItemStack testOutput = CraftingManager.getInstance().findMatchingRecipe(this.testFrame, w);

            if (Platform.isSameItemPrecise(this.correctOutput, testOutput)) {
                this.testFrame.setInventorySlotContents(slotIndex, this.crafting.getStackInSlot(slotIndex));
                this.markItemAs(slotIndex, i, TestStatus.ACCEPT);
                return true;
            }
        }

        this.markItemAs(slotIndex, i, TestStatus.DECLINE);
        return false;
    }

    @Override
    public boolean isCraftable() {
        return this.isCrafting;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.condensedInputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.condensedOutputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public boolean canSubstitute() {
        return this.canSubstitute;
    }

    @Override
    public boolean canBeSubstitute() {
        return this.canBeSubstitute;
    }

    @Override
    public ItemStack getOutput(final InventoryCrafting craftingInv, final World w) {
        if (!this.isCrafting) {
            throw new IllegalStateException("Only crafting recipes supported.");
        }

        for (int x = 0; x < craftingInv.getSizeInventory(); x++) {
            if (!this.isValidItemForSlot(x, craftingInv.getStackInSlot(x), w)) {
                return null;
            }
        }

        if (this.outputs != null && this.outputs.length > 0) {
            return this.outputs[0].getItemStack();
        }

        return null;
    }

    private TestStatus getStatus(final int slotIndex, final ItemStack i) {
        if (this.crafting.getStackInSlot(slotIndex) == null) {
            return i == null ? TestStatus.ACCEPT : TestStatus.DECLINE;
        }

        if (i == null) {
            return TestStatus.DECLINE;
        }

        if (i.hasTagCompound()) {
            return TestStatus.TEST;
        }

        if (this.passCache.contains(new TestLookup(slotIndex, i))) {
            return TestStatus.ACCEPT;
        }

        if (this.failCache.contains(new TestLookup(slotIndex, i))) {
            return TestStatus.DECLINE;
        }

        return TestStatus.TEST;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public int compareTo(final PatternHelper o) {
        return ItemSorters.compareInt(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }

        final PatternHelper other = (PatternHelper) obj;

        if (this.pattern != null && other.pattern != null) {
            return this.pattern.equals(other.pattern);
        }
        return false;
    }

    private enum TestStatus {
        ACCEPT,
        DECLINE,
        TEST
    }

    private static final class TestLookup {

        private final int slot;
        private final int ref;
        private final int hash;

        public TestLookup(final int slot, final ItemStack i) {
            this(slot, i.getItem(), i.getItemDamage());
        }

        public TestLookup(final int slot, final Item item, final int dmg) {
            this.slot = slot;
            this.ref = (dmg << Platform.DEF_OFFSET) | (Item.getIdFromItem(item) & 0xffff);
            final int offset = 3 * slot;
            this.hash = (this.ref << offset) | (this.ref >> (offset + 32));
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(final Object obj) {
            final boolean equality;

            if (obj instanceof TestLookup b) {

                equality = b.slot == this.slot && b.ref == this.ref;
            } else {
                equality = false;
            }

            return equality;
        }
    }

    public static IAEItemStack[] loadIAEItemStackFromNBT(final NBTTagList tags, boolean saveOrder,
            final ItemStack unknownItem) {
        final List<IAEItemStack> items = new ArrayList<>();

        for (int x = 0; x < tags.tagCount(); x++) {
            final NBTTagCompound tag = tags.getCompoundTagAt(x);

            if (tag.hasNoTags()) {
                continue;
            }

            ItemStack gs = Platform.loadItemStackFromNBT(tag);

            if (gs == null && unknownItem != null) {
                gs = unknownItem.copy();
            }

            final IAEItemStack ae = AEApi.instance().storage().createItemStack(gs);

            if (ae != null || saveOrder) {
                items.add(ae);
            }
        }

        return items.toArray(new IAEItemStack[0]);
    }

    public static IAEItemStack[] convertToCondensedList(final IAEItemStack[] items) {
        final Map<IAEItemStack, IAEItemStack> tmp = new HashMap<>();

        for (final IAEItemStack io : items) {

            if (io == null) {
                continue;
            }

            final IAEItemStack g = tmp.get(io);

            if (g == null) {
                tmp.put(io, io.copy());
            } else {
                g.add(io);
            }
        }

        return tmp.values().toArray(new IAEItemStack[0]);
    }
}
