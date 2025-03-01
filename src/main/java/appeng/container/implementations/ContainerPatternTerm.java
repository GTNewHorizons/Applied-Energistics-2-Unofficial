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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IDefinitions;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.core.sync.packets.PacketUpdateAESlot;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.item.AEItemStack;

public class ContainerPatternTerm extends ContainerMEMonitorable
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket {

    public static final int MULTIPLE_OF_BUTTON_CLICK = 2;
    public static final int MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT = 8;
    private final PartPatternTerminal patternTerminal;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);
    private final IInventory crafting;
    private final SlotFakeCraftingMatrix[] craftingSlots = new SlotFakeCraftingMatrix[9];
    private final OptionalSlotFake[] outputSlots = new OptionalSlotFake[3];
    private final SlotPatternTerm craftSlot;
    private final SlotRestrictedInput patternSlotIN;
    private final SlotRestrictedInput patternSlotOUT;

    @GuiSync(97)
    public boolean craftingMode = true;

    @GuiSync(96)
    public boolean substitute = false;

    @GuiSync(95)
    public boolean beSubstitute = true;

    public ContainerPatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);
        this.patternTerminal = (PartPatternTerminal) monitorable;

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
            this.outputSlots[y].setRenderDisabled(false);
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

    private ItemStack getAndUpdateOutput() {
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        for (int x = 0; x < ic.getSizeInventory(); x++) {
            ic.setInventorySlotContents(x, this.crafting.getStackInSlot(x));
        }

        final ItemStack is = CraftingManager.getInstance().findMatchingRecipe(ic, this.getPlayerInv().player.worldObj);
        this.cOut.setInventorySlotContents(0, is);
        return is;
    }

    @Override
    public void saveChanges() {}

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {}

    public void encodeAndMoveToInventory(boolean encodeWholeStack) {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (output != null) {
            if (encodeWholeStack) {
                ItemStack blanks = this.patternSlotIN.getStack();
                this.patternSlotIN.putStack(null);
                if (blanks != null) output.stackSize += blanks.stackSize;
            }
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.entityDropItem(output, 0);
            }
            this.patternSlotOUT.putStack(null);
            if (getPatternTerminal().hasRefillerUpgrade()) refillBlankPatterns(patternSlotIN);
        }
    }

    public void encode() {
        ItemStack output = this.patternSlotOUT.getStack();

        final IAEItemStack[] in = this.getInputs();
        final IAEItemStack[] out = this.getOutputs();

        // if there is no input, this would be silly.
        if (in == null || out == null) {
            return;
        }

        // first check the output slots, should either be null, or a pattern
        if (output != null && !this.isPattern(output)) {
            return;
        } // if nothing is there we should snag a new pattern.
        else if (output == null) {
            output = this.patternSlotIN.getStack();
            if (!this.isPattern(output)) {
                return; // no blanks.
            }

            // remove one, and clear the input slot.
            output.stackSize--;
            if (output.stackSize == 0) {
                this.patternSlotIN.putStack(null);
            }

            // add a new encoded pattern.
            for (final ItemStack encodedPatternStack : AEApi.instance().definitions().items().encodedPattern()
                    .maybeStack(1).asSet()) {
                output = encodedPatternStack;
                this.patternSlotOUT.putStack(output);
            }
            if (getPatternTerminal().hasRefillerUpgrade()) refillBlankPatterns(patternSlotIN);
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final IAEItemStack i : in) {
            tagIn.appendTag(this.createAEItemTag(i));
        }

        for (final IAEItemStack i : out) {
            tagOut.appendTag(this.createAEItemTag(i));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());
        encodedValue.setBoolean("beSubstitute", this.canBeSubstitute());
        encodedValue.setBoolean("isLong", isLongPattern());
        encodedValue.setString("author", this.getPlayerInv().player.getCommandSenderName());

        output.setTagCompound(encodedValue);
    }

    private boolean isLongPattern() {
        final IAEItemStack[] in = this.getInputs();
        final IAEItemStack[] out = this.getOutputs();

        if (in == null || out == null) {
            return false;
        }

        for (IAEItemStack ais : in) {
            if (ais != null && ais.getStackSize() > Integer.MAX_VALUE) return true;
        }

        for (IAEItemStack ais : out) {
            if (ais != null && ais.getStackSize() > Integer.MAX_VALUE) return true;
        }
        return false;
    }

    private IAEItemStack[] getInputs() {
        final IAEItemStack[] input = new IAEItemStack[9];
        boolean hasValue = false;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            input[x] = this.craftingSlots[x].getAEStack();
            if (input[x] != null) {
                hasValue = true;
            }
        }

        if (hasValue) {
            return input;
        }

        return null;
    }

    private IAEItemStack[] getOutputs() {
        if (this.isCraftingMode()) {
            final ItemStack out = this.getAndUpdateOutput();

            if (out != null && out.stackSize > 0) {
                return new IAEItemStack[] { AEItemStack.create(out) };
            }
        } else {
            final List<IAEItemStack> list = new ArrayList<>(3);
            boolean hasValue = false;

            for (final OptionalSlotFake outputSlot : this.outputSlots) {
                final IAEItemStack out = outputSlot.getAEStack();

                if (out != null && out.getStackSize() > 0) {
                    list.add(out);
                    hasValue = true;
                }
            }

            if (hasValue) {
                return list.toArray(new IAEItemStack[0]);
            }
        }

        return null;
    }

    private boolean isPattern(final ItemStack output) {
        if (output == null) {
            return false;
        }

        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs(output);
        isPattern |= definitions.materials().blankPattern().isSameAs(output);

        return isPattern;
    }

    private NBTBase createAEItemTag(final IAEItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();

        if (i != null) {
            i.writeToNBT(c);
        }

        return c;
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
    public void addCraftingToCrafters(ICrafting c) {
        super.addCraftingToCrafters(c);
        updateSlots();
    }

    @Override
    public void onSlotChange(final Slot s) {
        if (Platform.isServer()) {
            if (s == this.patternSlotOUT) {
                if (s.getHasStack()) updateSlotsOnPatternInject();
                for (final Object crafter : this.crafters) {
                    final ICrafting icrafting = (ICrafting) crafter;

                    for (final Object g : this.inventorySlots) {
                        if (g instanceof OptionalSlotFake || g instanceof SlotFakeCraftingMatrix) {
                            final Slot sri = (Slot) g;
                            icrafting.sendSlotContents(this, sri.slotNumber, sri.getStack());
                        }
                    }
                    ((EntityPlayerMP) icrafting).isChangingQuantityOnly = false;
                }
                this.detectAndSendChanges();
            } else if (s == patternRefiller && patternRefiller.getStack() != null) {
                refillBlankPatterns(patternSlotIN);
                detectAndSendChanges();
            } else if (s instanceof SlotFake sf) {
                for (final Object crafter : this.crafters) {
                    final EntityPlayerMP emp = (EntityPlayerMP) crafter;
                    if (sf.getHasStack()) {
                        try {
                            NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                        } catch (IOException ignored) {}
                    }
                }
            }
        }
    }

    public void updateSlotsOnPatternInject() {
        for (final Object crafter : this.crafters) {
            final EntityPlayerMP emp = (EntityPlayerMP) crafter;
            for (final SlotFakeCraftingMatrix sfcm : this.craftingSlots) {
                if (sfcm.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("crafting");
                    sfcm.putAEStack(inv.getAEStackInSlot(sfcm.getSlotIndex()));
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sfcm.slotNumber, sfcm.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }

            for (final OptionalSlotFake osf : this.outputSlots) {
                if (osf.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("output");
                    osf.putAEStack(inv.getAEStackInSlot(osf.getSlotIndex()));
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(osf.slotNumber, osf.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public void updateSlots() {
        for (final Object crafter : this.crafters) {
            final EntityPlayerMP emp = (EntityPlayerMP) crafter;

            for (final SlotFakeCraftingMatrix sfcm : this.craftingSlots) {
                if (sfcm.getHasStack()) {
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sfcm.slotNumber, sfcm.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }

            for (final OptionalSlotFake osf : this.outputSlots) {
                if (osf.getHasStack()) {
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(osf.slotNumber, osf.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    @Override
    public void onCraftMatrixChanged(IInventory p_75130_1_) {
        super.onCraftMatrixChanged(p_75130_1_);
        if (Platform.isServer()) {
            p_75130_1_.markDirty();
        }
    }

    public void clear() {
        for (final Slot s : this.craftingSlots) {
            s.putStack(null);
        }

        for (final Slot s : this.outputSlots) {
            s.putStack(null);
        }

        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("player")) {
            return this.getInventoryPlayer();
        }
        return this.getPatternTerminal().getInventoryByName(name);
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    public void toggleSubstitute() {
        this.substitute = !this.substitute;

        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    public boolean isCraftingMode() {
        return this.craftingMode;
    }

    private void setCraftingMode(final boolean craftingMode) {
        this.craftingMode = craftingMode;
    }

    public PartPatternTerminal getPatternTerminal() {
        return this.patternTerminal;
    }

    private boolean isSubstitute() {
        return this.substitute;
    }

    private boolean canBeSubstitute() {
        return this.beSubstitute;
    }

    public void setSubstitute(final boolean substitute) {
        this.substitute = substitute;
    }

    public void setCanBeSubstitute(final boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
    }

    public void doubleStacks(int val) {
        multiplyOrDivideStacks(
                ((val & 1) != 0 ? MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT : MULTIPLE_OF_BUTTON_CLICK)
                        * ((val & 2) != 0 ? -1 : 1));
    }

    static boolean canMultiplyOrDivide(SlotFake[] slots, long mult) {
        if (mult > 0) {
            for (SlotFake s : slots) {
                if (s.getAEStack() != null) {
                    double val = (double) s.getAEStack().getStackSize() * mult;
                    if (val > Long.MAX_VALUE) return false;
                }
            }
            return true;
        } else if (mult < 0) {
            mult = -mult;
            for (SlotFake s : slots) {
                if (s.getAEStack() != null) { // Although % is a very inefficient algorithm, it is not a performance
                                              // issue
                                              // here. :>
                    if (s.getAEStack().getStackSize() % mult != 0) return false;
                }
            }
            return true;
        }
        return false;
    }

    static void multiplyOrDivideStacksInternal(SlotFake[] slots, long mult) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        if (mult > 0) {
            for (final SlotFake s : enabledSlots) {
                IAEItemStack st = s.getAEStack();
                if (st != null) {
                    st.setStackSize(st.getStackSize() * mult);
                }
            }
        } else if (mult < 0) {
            mult = -mult;
            for (final SlotFake s : enabledSlots) {
                IAEItemStack st = s.getAEStack();
                if (st != null) {
                    st.setStackSize(st.getStackSize() / mult);
                }
            }
        }
    }

    /**
     * Multiply or divide a number
     * 
     * @param multi Positive numbers are multiplied and negative numbers are divided
     */
    public void multiplyOrDivideStacks(long multi) {
        if (!isCraftingMode()) {
            if (canMultiplyOrDivide(this.craftingSlots, multi) && canMultiplyOrDivide(this.outputSlots, multi)) {
                multiplyOrDivideStacksInternal(this.craftingSlots, multi);
                multiplyOrDivideStacksInternal(this.outputSlots, multi);
            }
            this.detectAndSendChanges();
        }
    }

    public boolean isAPatternTerminal() {
        return true;
    }
}
