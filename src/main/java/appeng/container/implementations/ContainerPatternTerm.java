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

import static appeng.parts.reporting.PartPatternTerminal.*;
import static appeng.util.Platform.isServer;
import static appeng.util.Platform.writeStackNBT;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.MachineSource;
import appeng.api.parts.IPatternTerminal;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.contents.WirelessPatternTerminalGuiObject;
import appeng.items.storage.ItemViewCell;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorPlayerHand;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerPatternTerm extends ContainerMEMonitorable implements IAEAppEngInventory, IOptionalSlotHost,
        IContainerCraftingPacket, IVirtualSlotHolder, IVirtualSlotSource {

    public static final int MULTIPLE_OF_BUTTON_CLICK = 2;
    public static final int MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT = 8;
    protected final IPatternTerminal patternTerminal;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);
    private final IAEStackInventory inputs;
    private final IAEStackInventory outputs;
    public final IAEStack<?>[] craftingSlotsClient = new IAEStack<?>[getPatternInputsWidth() * getPatternInputsHeigh()
            * getPatternOutputPages()];
    private final IInventory craftingMatrix;
    public final IAEStack<?>[] outputSlotsClient = new IAEStack<?>[getPatternOutputsWidth() * getPatternOutputsHeigh()
            * getPatternOutputPages()];

    private final SlotPatternTerm craftSlot;
    private final SlotRestrictedInput patternSlotIN;
    private final SlotRestrictedInput patternSlotOUT;
    private final boolean craftingModeSupport;

    boolean isFirstUpdate = true;

    @GuiSync(97)
    public boolean craftingMode = true;

    @GuiSync(96)
    public boolean substitute = false;

    @GuiSync(95)
    public boolean beSubstitute = true;

    public ContainerPatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable) {
        this(ip, monitorable, true);
    }

    public ContainerPatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable,
            boolean craftingModeSupport) {
        super(ip, monitorable, false);
        this.patternTerminal = (IPatternTerminal) monitorable;

        if (monitorable instanceof WirelessPatternTerminalGuiObject wptgo) {
            wptgo.setInventorySize(
                    getPatternInputsWidth() * getPatternInputsHeigh() * getPatternInputPages(),
                    getPatternOutputsWidth() * getPatternOutputsHeigh() * getPatternOutputPages());
            wptgo.readInventory();
        }

        this.craftingModeSupport = craftingModeSupport;

        final IInventory patternInv = this.getPatternTerminal()
                .getInventoryByName(StorageName.CRAFTING_PATTERN.getName());

        this.inputs = this.getPatternTerminal().getAEInventoryByName(StorageName.CRAFTING_INPUT);
        this.outputs = this.getPatternTerminal().getAEInventoryByName(StorageName.CRAFTING_OUTPUT);

        if (craftingModeSupport) {
            this.craftingMatrix = new AppEngInternalInventory(null, 9);
            this.addSlotToContainer(
                    this.craftSlot = new SlotPatternTerm(
                            ip.player,
                            this.getActionSource(),
                            this.getPowerSource(),
                            monitorable,
                            this.craftingMatrix,
                            patternInv,
                            this.cOut,
                            110,
                            -76 + 18,
                            this,
                            2,
                            this));
            this.craftSlot.setIIcon(-1);
        } else {
            this.craftingMatrix = null;
            this.craftSlot = null;
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

        this.updateOrderOfOutputSlots();
        refillBlankPatterns(patternSlotIN);

        // need because InventoryBogoSorter looking for specific slot number for bind buttons
        // bindPlayerInventory in MEMonitorable break it
        this.bindPlayerInventory(ip, 0, 0);
    }

    private void updateOrderOfOutputSlots() {
        if (craftingModeSupport) {
            if (!this.isCraftingMode()) {
                this.craftSlot.xDisplayPosition = -9000;
            } else {
                this.craftSlot.xDisplayPosition = this.craftSlot.getX();
            }
        }
    }

    private void copyToMatrix() {
        for (int i = 0; i < this.craftingMatrix.getSizeInventory(); i++) {
            IAEStack<?> aes = this.inputs.getAEStackInSlot(i);
            if (aes instanceof IAEItemStack ais) {
                ais.setStackSize(1);
                ItemStack is = ais.getItemStack();
                this.craftingMatrix.setInventorySlotContents(i, is);
            } else {
                this.inputs.putAEStackInSlot(i, null);
                this.craftingMatrix.setInventorySlotContents(i, null);
            }
        }
        this.getAndUpdateOutput();
        this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
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
    public void onCraftMatrixChanged(IInventory p_75130_1_) {}

    private ItemStack getAndUpdateOutput() {
        if (!craftingModeSupport || !isCraftingMode() || !isServer()) return null;
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        for (int x = 0; x < ic.getSizeInventory(); x++) {
            ic.setInventorySlotContents(x, this.craftingMatrix.getStackInSlot(x));
        }

        final ItemStack is = CraftingManager.getInstance().findMatchingRecipe(ic, this.getPlayerInv().player.worldObj);
        this.cOut.setInventorySlotContents(0, is);
        super.detectAndSendChanges();

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
            refillBlankPatterns(patternSlotIN);
        }
    }

    public void encode() {
        ItemStack output = this.patternSlotOUT.getStack();

        final IAEStack<?>[] in = this.getInputs();
        final IAEStack<?>[] out = this.getOutputs();

        // if there is no input, this would be silly.
        if (in == null || out == null) {
            return;
        }

        // first check the output slots, should either be null, or a pattern
        if (output != null) {
            if (!this.isEncodedPattern(output) && !this.isUltimatePattern(output)) {
                return;
            }
        } // if nothing is there we should snag a new pattern.
        else {
            ItemStack blank = this.patternSlotIN.getStack();
            if (!this.isBlankPattern(blank)) {
                return;
            }

            // remove one, and clear the input slot.
            blank.stackSize--;
            if (blank.stackSize == 0) {
                this.patternSlotIN.putStack(null);
            }

            refillBlankPatterns(patternSlotIN);

            // add a new encoded pattern.
            if (isCraftingMode()) {
                output = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).orNull();
            } else {
                output = AEApi.instance().definitions().items().encodedUltimatePattern().maybeStack(1).orNull();;
            }
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final IAEStack<?> i : in) {
            tagIn.appendTag(writeStackNBT(i, new NBTTagCompound(), true));
        }

        for (final IAEStack<?> i : out) {
            tagOut.appendTag(writeStackNBT(i, new NBTTagCompound(), true));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        if (isCraftingMode()) encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());
        encodedValue.setBoolean("beSubstitute", this.canBeSubstitute());
        encodedValue.setString("author", this.getPlayerInv().player.getCommandSenderName());

        output.setTagCompound(encodedValue);

        this.patternSlotOUT.putStack(output);
    }

    private IAEStack<?>[] getInputs() {
        final IAEStack<?>[] input = new IAEStack<?>[this.inputs.getSizeInventory()];
        boolean hasValue = false;

        for (int i = 0; i < this.inputs.getSizeInventory(); i++) {
            input[i] = this.inputs.getAEStackInSlot(i);
            if (input[i] != null) {
                hasValue = true;
            }
        }

        if (hasValue) {
            return input;
        }

        return null;
    }

    private IAEStack<?>[] getOutputs() {
        if (this.isCraftingMode()) {
            final IAEStack<?> out = AEItemStack.create(this.getAndUpdateOutput());

            if (out != null && out.getStackSize() > 0) {
                return new IAEStack<?>[] { out };
            }
        } else {
            final List<IAEStack<?>> list = new ArrayList<>(3);
            boolean hasValue = false;

            for (int i = 0; i < this.outputs.getSizeInventory(); i++) {
                final IAEStack<?> out = this.outputs.getAEStackInSlot(i);

                if (out != null && out.getStackSize() > 0) {
                    list.add(out);
                    hasValue = true;
                }
            }

            if (hasValue) {
                return list.toArray(new IAEStack<?>[0]);
            }
        }

        return null;
    }

    private boolean isBlankPattern(final ItemStack stack) {
        return stack != null && AEApi.instance().definitions().materials().blankPattern().isSameAs(stack);
    }

    private boolean isEncodedPattern(final ItemStack stack) {
        return stack != null && AEApi.instance().definitions().items().encodedPattern().isSameAs(stack);
    }

    private boolean isUltimatePattern(final ItemStack stack) {
        return stack != null && AEApi.instance().definitions().items().encodedUltimatePattern().isSameAs(stack);
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        if (idx == 1) {
            return isServer() ? !this.getPatternTerminal().isCraftingRecipe() : !this.isCraftingMode();
        } else if (idx == 2) {
            return isServer() ? this.getPatternTerminal().isCraftingRecipe() : this.isCraftingMode();
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
        if (isServer()) {
            if (this.isCraftingMode() != this.getPatternTerminal().isCraftingRecipe()) {
                this.setCraftingMode(this.getPatternTerminal().isCraftingRecipe());
            }

            this.substitute = this.patternTerminal.isSubstitution();
            this.beSubstitute = this.patternTerminal.canBeSubstitution();

            if (this.isFirstUpdate) {
                if (isCraftingMode()) {
                    this.copyToMatrix();
                } else {
                    this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
                }
                this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.outputs, outputSlotsClient);
                this.isFirstUpdate = false;
            }
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
    public void onSlotChange(final Slot s) {
        if (!isServer()) return;
        if (s == this.patternSlotOUT) {
            this.isFirstUpdate = true;
            this.detectAndSendChanges();
        } else if (s == patternRefiller && patternRefiller.getStack() != null) {
            refillBlankPatterns(patternSlotIN);
            detectAndSendChanges();
        }
    }

    public void clear() {
        for (int i = 0; i < this.inputs.getSizeInventory(); ++i) {
            this.inputs.putAEStackInSlot(i, null);
        }
        for (int i = 0; i < this.outputs.getSizeInventory(); ++i) {
            this.outputs.putAEStackInSlot(i, null);
        }
        if (this.craftingMatrix != null) {
            for (int i = 0; i < this.craftingMatrix.getSizeInventory(); ++i) {
                this.craftingMatrix.setInventorySlotContents(i, null);
            }
        }

        this.getAndUpdateOutput();
        this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
        this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.outputs, outputSlotsClient);
    }

    protected void refillBlankPatterns(Slot slot) {
        if (Platform.isServer()) {
            ItemStack blanks = slot.getStack();
            int blanksToRefill = 64;
            if (blanks != null) blanksToRefill -= blanks.stackSize;
            if (blanksToRefill <= 0) return;
            final AEItemStack request = AEItemStack
                    .create(AEApi.instance().definitions().materials().blankPattern().maybeStack(blanksToRefill).get());
            final IAEItemStack extracted = Platform
                    .poweredExtraction(this.getPowerSource(), this.getCellInventory(), request, this.getActionSource());
            if (extracted != null) {
                if (blanks != null) blanks.stackSize += (int) extracted.getStackSize();
                else {
                    blanks = extracted.getItemStack();
                }
                slot.putStack(blanks);
            }
        }
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

    public void setCraftingMode(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        this.patternTerminal.setCraftingRecipe(craftingMode);
        if (craftingMode && craftingModeSupport) copyToMatrix();
        this.updateOrderOfOutputSlots();
    }

    public IPatternTerminal getPatternTerminal() {
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

    static boolean canMultiplyOrDivide(IAEStackInventory inventory, int mult) {
        if (mult > 0) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                IAEStack<?> aes = inventory.getAEStackInSlot(i);
                if (aes != null) {
                    double val = (double) aes.getStackSize() * mult;
                    if (val > Long.MAX_VALUE) return false;
                }
            }

            return true;
        } else if (mult < 0) {
            mult = -mult;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                IAEStack<?> aes = inventory.getAEStackInSlot(i);
                if (aes != null) { // Although % is a very inefficient algorithm, it is not a performance
                    // issue
                    // here. :>
                    if (aes.getStackSize() % mult != 0) return false;
                }
            }

            return true;
        }
        return false;
    }

    static void multiplyOrDivideStacksInternal(IAEStackInventory inventory, int mult) {
        if (mult > 0) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                IAEStack<?> st = inventory.getAEStackInSlot(i);
                if (st != null) {
                    st.setStackSize(st.getStackSize() * mult);
                    inventory.putAEStackInSlot(i, st);
                }
            }
        } else if (mult < 0) {
            mult = -mult;
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                IAEStack<?> st = inventory.getAEStackInSlot(i);
                if (st != null) {
                    st.setStackSize(st.getStackSize() / mult);
                    inventory.putAEStackInSlot(i, st);
                }
            }
        }
    }

    /**
     * Multiply or divide a number
     *
     * @param multi Positive numbers are multiplied and negative numbers are divided
     */
    public void multiplyOrDivideStacks(int multi) {
        if (!isCraftingMode()) {
            if (canMultiplyOrDivide(this.inputs, multi) && canMultiplyOrDivide(this.outputs, multi)) {
                multiplyOrDivideStacksInternal(this.inputs, multi);
                multiplyOrDivideStacksInternal(this.outputs, multi);

                this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
                this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.outputs, outputSlotsClient);
            }
        }
    }

    public boolean isAPatternTerminal() {
        return true;
    }

    public boolean getCraftingModeSupport() {
        return craftingModeSupport;
    }

    public int getPatternInputsWidth() {
        return patternInputsWidth;
    }

    public int getPatternInputsHeigh() {
        return patternInputsHeigh;
    }

    public int getPatternInputPages() {
        return patternInputsPages;
    }

    public int getPatternOutputsWidth() {
        return patternOutputsWidth;
    }

    public int getPatternOutputsHeigh() {
        return patternOutputsHeigh;
    }

    public int getPatternOutputPages() {
        return patternOutputPages;
    }

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        switch (invName) {
            case CRAFTING_INPUT -> {
                if (isCraftingMode() && aes != null) {
                    aes.setStackSize(1);
                }
                this.inputs.putAEStackInSlot(slotId, aes);
                if (isServer()) {
                    this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
                    if (isCraftingMode()) {
                        if (aes != null) {
                            IAEItemStack ais = ((IAEItemStack) aes);
                            this.craftingMatrix.setInventorySlotContents(slotId, ais.getItemStack());
                        } else {
                            this.craftingMatrix.setInventorySlotContents(slotId, null);
                        }
                        this.getAndUpdateOutput();
                    }
                }
            }
            case CRAFTING_OUTPUT -> {
                this.outputs.putAEStackInSlot(slotId, aes);
                if (isServer()) {
                    this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.outputs, outputSlotsClient);
                }
            }
        }
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        switch (invName) {
            case CRAFTING_INPUT -> {
                for (var entry : slotStacks.int2ObjectEntrySet()) {
                    IAEStack<?> aes = entry.getValue();
                    if (isServer() && isCraftingMode() && aes != null) {
                        aes.setStackSize(1);
                    }
                    this.inputs.putAEStackInSlot(entry.getIntKey(), aes);
                    if (isServer() && isCraftingMode()) {
                        this.craftingMatrix.setInventorySlotContents(
                                entry.getIntKey(),
                                aes != null ? ((IAEItemStack) aes).getItemStack() : null);
                    }
                }
                if (isServer()) {
                    this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.inputs, craftingSlotsClient);
                    if (isCraftingMode()) {
                        this.getAndUpdateOutput();
                    }
                }
            }
            case CRAFTING_OUTPUT -> {
                for (var entry : slotStacks.int2ObjectEntrySet()) {
                    this.outputs.putAEStackInSlot(entry.getIntKey(), entry.getValue());
                }
                if (isServer()) {
                    this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.outputs, outputSlotsClient);
                }
            }
        }
    }
}
