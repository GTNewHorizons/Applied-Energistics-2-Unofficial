/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.misc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StringUtils;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.event.ForgeEventFactory;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.render.items.ItemEncodedPatternRenderer;
import appeng.core.CommonHelper;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.helpers.PatternHelper;
import appeng.items.AEBaseItem;
import appeng.util.Platform;
import cpw.mods.fml.common.registry.GameRegistry;

public class ItemEncodedPattern extends AEBaseItem implements ICraftingPatternItem {

    // rather simple client side caching.
    private static final Map<ItemStack, ItemStack> SIMPLE_CACHE = new WeakHashMap<>();

    public ItemEncodedPattern() {
        this.setFeature(EnumSet.of(AEFeature.Patterns));
        this.setMaxStackSize(64);
        if (Platform.isClient()) {
            MinecraftForgeClient.registerItemRenderer(this, new ItemEncodedPatternRenderer());
        }
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World w, final EntityPlayer player) {
        this.clearPattern(stack, player);

        return stack;
    }

    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
            final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        if (ForgeEventFactory.onItemUseStart(player, stack, 1) <= 0) return true;

        return this.clearPattern(stack, player);
    }

    private boolean clearPattern(final ItemStack stack, final EntityPlayer player) {
        if (player.isSneaking()) {
            if (Platform.isClient()) {
                return false;
            }

            final InventoryPlayer inv = player.inventory;

            for (int s = 0; s < player.inventory.getSizeInventory(); s++) {
                if (inv.getStackInSlot(s) == stack) {
                    for (final ItemStack blankPattern : AEApi.instance().definitions().materials().blankPattern()
                            .maybeStack(stack.stackSize).asSet()) {
                        inv.setInventorySlotContents(s, blankPattern);
                    }

                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        final NBTTagCompound encodedValue = stack.getTagCompound();

        if (encodedValue == null) {
            lines.add(EnumChatFormatting.RED + GuiText.InvalidPattern.getLocal());
            return;
        }

        final ICraftingPatternDetails details = this.getPatternForItem(stack, player.worldObj);
        final boolean substitute = encodedValue.getBoolean("substitute");
        final boolean beSubstitute = encodedValue.getBoolean("beSubstitute");
        final String author = encodedValue.getString("author");
        IAEItemStack[] inItems;
        IAEItemStack[] outItems;

        if (details == null) {
            final ItemStack unknownItem = new ItemStack(Blocks.fire);
            unknownItem.setStackDisplayName(GuiText.UnknownItem.getLocal());

            inItems = PatternHelper.convertToCondensedList(
                    PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("in", 10), false, unknownItem));
            outItems = PatternHelper.convertToCondensedList(
                    PatternHelper.loadIAEItemStackFromNBT(encodedValue.getTagList("out", 10), false, unknownItem));
        } else {
            inItems = details.getCondensedInputs();
            outItems = details.getCondensedOutputs();
        }

        boolean recipeIsBroken = details == null;
        final List<String> in = new ArrayList<>();
        final List<String> out = new ArrayList<>();

        final String substitutionLabel = EnumChatFormatting.YELLOW + GuiText.Substitute.getLocal()
                + " "
                + EnumChatFormatting.RESET;
        final String beSubstitutionLabel = EnumChatFormatting.YELLOW + GuiText.BeSubstitute.getLocal()
                + " "
                + EnumChatFormatting.RESET;
        final String canSubstitute = substitute ? GuiText.Yes.getLocal() : GuiText.No.getLocal();
        final String canBeSubstitute = beSubstitute ? GuiText.Yes.getLocal() : GuiText.No.getLocal();
        final String result = (outItems.length > 1 ? EnumChatFormatting.DARK_AQUA + GuiText.Results.getLocal()
                : EnumChatFormatting.DARK_AQUA + GuiText.Result.getLocal()) + ":" + EnumChatFormatting.RESET;
        final String ingredients = (inItems.length > 1 ? EnumChatFormatting.DARK_GREEN + GuiText.Ingredients.getLocal()
                : EnumChatFormatting.DARK_GREEN + GuiText.Ingredient.getLocal()) + ": " + EnumChatFormatting.RESET;
        final String holdShift = EnumChatFormatting.GRAY + GuiText.HoldShift.getLocal() + EnumChatFormatting.RESET;

        recipeIsBroken = addInformation(player, inItems, in, ingredients, displayMoreInfo, EnumChatFormatting.GREEN)
                || recipeIsBroken;
        recipeIsBroken = addInformation(player, outItems, out, result, displayMoreInfo, EnumChatFormatting.AQUA)
                || recipeIsBroken;

        if (recipeIsBroken) {
            lines.add(EnumChatFormatting.RED + GuiText.InvalidPattern.getLocal());
        } else {
            lines.addAll(out);
            if (GuiScreen.isShiftKeyDown()) {
                lines.addAll(in);
            } else {
                lines.add(holdShift);
            }

            lines.add(substitutionLabel + canSubstitute);
            lines.add(beSubstitutionLabel + canBeSubstitute);

            if (!StringUtils.isNullOrEmpty(author)) {
                lines.add(
                        EnumChatFormatting.LIGHT_PURPLE + GuiText.EncodedBy.getLocal(author)
                                + EnumChatFormatting.RESET);
            }
        }
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(final ItemStack is, final World w) {
        try {
            return new PatternHelper(is, w);
        } catch (final Throwable t) {
            return null;
        }
    }

    public ItemStack getOutput(final ItemStack item) {
        ItemStack out = SIMPLE_CACHE.get(item);

        if (out != null) {
            return out;
        }

        final World w = CommonHelper.proxy.getWorld();

        if (w == null) {
            return null;
        }

        final ICraftingPatternDetails details = this.getPatternForItem(item, w);

        if (details == null) {
            return null;
        }

        SIMPLE_CACHE.put(item, out = details.getCondensedOutputs()[0].getItemStack());
        return out;
    }

    private boolean addInformation(final EntityPlayer player, final IAEItemStack[] items, final List<String> lines,
            String label, final boolean displayMoreInfo, EnumChatFormatting color) {
        final ItemStack unknownItem = new ItemStack(Blocks.fire);
        boolean recipeIsBroken = false;
        boolean first = true;
        List<IAEItemStack> itemsList = Arrays.asList(items);
        List<IAEItemStack> sortedItems = itemsList.stream()
                .sorted(Comparator.comparingLong(IAEItemStack::getStackSize).reversed()).collect(Collectors.toList());
        boolean isFluid = false;

        for (final IAEItemStack item : sortedItems) {

            if (!recipeIsBroken && item.equals(unknownItem)) {
                recipeIsBroken = true;
            }

            if (item.getItemStack().getItem() == GameRegistry.findItem("ae2fc", "fluid_drop")) {
                label = EnumChatFormatting.GOLD + label;
                color = EnumChatFormatting.GOLD;
                isFluid = true;
            }

            if (first) {
                lines.add(label);
                lines.add(
                        "   " + EnumChatFormatting.WHITE
                                + NumberFormat.getNumberInstance(Locale.US).format(item.getStackSize())
                                + EnumChatFormatting.RESET
                                + (isFluid ? EnumChatFormatting.WHITE + "L" : " ")
                                + EnumChatFormatting.RESET
                                + color
                                + (isFluid ? Platform.getItemDisplayName(item).replace("drop of", "")
                                        : Platform.getItemDisplayName(item)));
            }
            if (!first) {
                lines.add(
                        "   " + EnumChatFormatting.WHITE
                                + NumberFormat.getNumberInstance(Locale.US).format(item.getStackSize())
                                + EnumChatFormatting.RESET
                                + (isFluid ? EnumChatFormatting.WHITE + "L" : " ")
                                + EnumChatFormatting.RESET
                                + color
                                + (isFluid ? Platform.getItemDisplayName(item).replace("drop of", "")
                                        : Platform.getItemDisplayName(item)));
            }

            first = false;
        }

        return recipeIsBroken;
    }
}
