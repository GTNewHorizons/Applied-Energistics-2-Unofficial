package appeng.container.implementations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketUpdateAESlot;
import appeng.helpers.IContainerCraftingPacket;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public abstract class ContainerPatternTermBase extends ContainerMEMonitorable
        implements IOptionalSlotHost, IContainerCraftingPacket {

    public int MULTIPLE_OF_BUTTON_CLICK = 2;
    public int MULTIPLE_OF_BUTTON_CLICK_ON_SHIFT = 8;

    protected int CRAFTING_GRID_PAGES;
    protected int CRAFTING_GRID_WIDTH;
    protected int CRAFTING_GRID_HEIGHT;
    protected int CRAFTING_GRID_SLOTS;

    protected PartPatternTerminal patternTerminal;

    protected IInventory crafting;
    protected AppEngInternalInventory cOut;

    protected SlotFake[] craftingSlots;
    protected SlotFake[] outputSlots;
    protected SlotRestrictedInput patternSlotIN;
    protected SlotRestrictedInput patternSlotOUT;

    @GuiSync(97)
    public boolean craftingMode = true;

    @GuiSync(96)
    public boolean substitute = false;

    @GuiSync(95)
    public boolean beSubstitute = true;

    public ContainerPatternTermBase(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);
    }

    protected ItemStack getAndUpdateOutput() {
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        for (int x = 0; x < ic.getSizeInventory(); x++) {
            ic.setInventorySlotContents(x, this.crafting.getStackInSlot(x));
        }

        final ItemStack is = CraftingManager.getInstance().findMatchingRecipe(ic, this.getPlayerInv().player.worldObj);
        this.cOut.setInventorySlotContents(0, is);
        return is;
    }

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
        if (output != null && this.isNotPattern(output)) {
            return;
        } // if nothing is there we should snag a new pattern.
        else if (output == null) {
            output = this.patternSlotIN.getStack();
            if (this.isNotPattern(output)) {
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
        encodedValue.setBoolean("crafting", false);
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
        final IAEItemStack[] input = new IAEItemStack[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];
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
            final List<IAEItemStack> list = new ArrayList<>(this.CRAFTING_GRID_SLOTS * this.CRAFTING_GRID_PAGES);
            boolean hasValue = false;

            for (final SlotFake outputSlot : this.outputSlots) {
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

    private boolean isNotPattern(final ItemStack output) {
        if (output == null) {
            return true;
        }

        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs(output);
        isPattern |= definitions.materials().blankPattern().isSameAs(output);

        return !isPattern;
    }

    private NBTBase createAEItemTag(final IAEItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();

        if (i != null) {
            i.writeToNBT(c);
        }

        return c;
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
                for (final Object crafter : this.crafters) {
                    final ICrafting icrafting = (ICrafting) crafter;

                    for (final Slot g : this.inventorySlots) {
                        if (g instanceof OptionalSlotFake || g instanceof SlotFakeCraftingMatrix) {
                            icrafting.sendSlotContents(this, g.slotNumber, g.getStack());
                        }
                    }
                    ((EntityPlayerMP) icrafting).isChangingQuantityOnly = false;
                }
                this.detectAndSendChanges();
                if (s.getHasStack()) updateSlotsOnPatternInject();
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
            for (final SlotFake sf : this.craftingSlots) {
                if (sf.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("crafting");
                    sf.putAEStack(inv.getAEStackInSlot(sf.getSlotIndex()));
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }

            for (final SlotFake sf : this.outputSlots) {
                if (sf.getHasStack()) {
                    AppEngInternalAEInventory inv = (AppEngInternalAEInventory) this.patternTerminal
                            .getInventoryByName("output");
                    sf.putAEStack(inv.getAEStackInSlot(sf.getSlotIndex()));
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public void updateSlots() {
        for (final Object crafter : this.crafters) {
            final EntityPlayerMP emp = (EntityPlayerMP) crafter;

            for (final SlotFake sf : this.craftingSlots) {
                if (sf.getHasStack()) {
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
                    } catch (IOException ignored) {}
                }
            }

            for (final SlotFake sf : this.outputSlots) {
                if (sf.getHasStack()) {
                    try {
                        NetworkHandler.instance.sendTo(new PacketUpdateAESlot(sf.slotNumber, sf.getAEStack()), emp);
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

    public void setPatternValue(int index, long amount) {
        SlotFake sf = (SlotFake) this.inventorySlots.get(index);
        sf.getAEStack().setStackSize(amount);
        this.inventoryItemStacks.set(index, sf.getStack());
        onSlotChange(sf);
    }

    public void clear() {
        for (final Slot s : this.craftingSlots) {
            s.putStack(null);
        }

        for (final Slot s : this.outputSlots) {
            s.putStack(null);
        }

        this.detectAndSendChanges();
        if (isCraftingMode()) this.getAndUpdateOutput();
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

    public boolean isCraftingMode() {
        return this.craftingMode;
    }

    protected void setCraftingMode(final boolean craftingMode) {}

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
                this.updateSlots();
            }
            this.detectAndSendChanges();
        }
    }

    public boolean isAPatternTerminal() {
        return true;
    }
}
