package appeng.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IContainerCraftingPacket;
import appeng.parts.reporting.AbstractPartPatternTerm;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

public abstract class ContainerBasePatternTerm extends ContainerMEMonitorable
        implements IOptionalSlotHost, IContainerCraftingPacket {

    protected final AbstractPartPatternTerm patternTerminal;

    protected final SlotRestrictedInput patternSlotIN;
    protected final SlotRestrictedInput patternSlotOUT;

    @GuiSync(96)
    public boolean substitute = false;

    @GuiSync(95)
    public boolean beSubstitute = true;
    protected final SlotRestrictedInput patternRefiller;

    public ContainerBasePatternTerm(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable, false);
        this.patternTerminal = (AbstractPartPatternTerm) monitorable;
        final IInventory patternInv = this.patternTerminal.getInventoryByName("pattern");
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
        this.addSlotToContainer(
                this.patternRefiller = new SlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.UPGRADES,
                        this.patternTerminal.getInventoryByName("upgrades"),
                        0,
                        206,
                        5 * 18 + 11,
                        this.getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);
        this.bindPlayerInventory(ip, 0, 0);

        if (patternTerminal.hasRefillerUpgrade() && Platform.isServer()) {
            refillBlankPatterns();
        }
    }

    protected boolean isPattern(final ItemStack output) {
        if (output == null) {
            return false;
        }

        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs(output);
        isPattern |= definitions.materials().blankPattern().isSameAs(output);

        return isPattern;
    }

    public void encode() {
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();
        ItemStack patternOut = patternSlotOUT.getStack();

        // if there is no input, this would be silly.
        if (in == null || out == null) {
            return;
        }

        // first check the output slots, should either be null, or a pattern
        if (patternOut != null && !this.isPattern(patternOut)) {
            return;
        } // if nothing is there we should snag a new pattern.
        else if (patternOut == null) {
            patternOut = this.patternSlotIN.getStack();
            if (!this.isPattern(patternOut)) {
                return; // no blanks.
            }

            // remove one, and clear the input slot.
            patternOut.stackSize--;
            if (patternOut.stackSize == 0) {
                this.patternSlotIN.putStack(null);
            }

            // add a new encoded pattern.
            for (final ItemStack encodedPatternStack : AEApi.instance().definitions().items().encodedPattern()
                    .maybeStack(1).asSet()) {
                patternOut = encodedPatternStack;
                this.patternSlotOUT.putStack(patternOut);
            }
        }

        // encode the slot.
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final ItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }

        for (final ItemStack i : out) {
            tagOut.appendTag(this.createItemTag(i));
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("substitute", this.substitute);
        encodedValue.setBoolean("beSubstitute", this.beSubstitute);

        patternOut.setTagCompound(encodedValue);
        if (patternTerminal.hasRefillerUpgrade()) refillBlankPatterns();
    }

    public void encodeAndMoveToInventory(boolean encodeWholeStack) {
        ItemStack output = this.patternSlotOUT.getStack();
        encode();
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
        }
    }

    protected abstract void clear();

    protected abstract ItemStack[] getOutputs();

    protected abstract ItemStack[] getInputs();

    protected NBTBase createItemTag(final ItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();

        if (i != null) {
            i.writeToNBT(c);
            c.setInteger("Count", i.stackSize);
        }

        return c;
    }

    /**
     * Refill blank patterns by extracting from the ME inventory and putting it into the slot. Must be called from the
     * server side only, so you must check {@link Platform#isServer()} before, for example.
     */
    protected void refillBlankPatterns() {
        ItemStack blanks = patternSlotIN.getStack();
        int blanksToRefill = 64;
        if (blanks != null) blanksToRefill -= blanks.stackSize;
        if (blanksToRefill <= 0) return;
        final AEItemStack request = AEItemStack
                .create(AEApi.instance().definitions().materials().blankPattern().maybeStack(blanksToRefill).get());
        final IAEItemStack extracted = Platform
                .poweredExtraction(this.getPowerSource(), this.getCellInventory(), request, this.getActionSource());
        if (extracted != null) {
            if (blanks != null) blanks.stackSize += extracted.getStackSize();
            else {
                blanks = extracted.getItemStack();
            }
            patternSlotIN.putStack(blanks);
        }
    }

    protected boolean canDoubleStacks(SlotFake[] slots) {
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        long emptyStots = enabledSlots.stream().filter(s -> s.getStack() == null).count();
        long fullSlots = enabledSlots.stream().filter(s -> s.getStack() != null && s.getStack().stackSize * 2 > 127)
                .count();
        return fullSlots <= emptyStots && emptyStots < enabledSlots.size();
    }

    protected void doubleStacksInternal(SlotFake[] slots) {
        List<ItemStack> overFlowStacks = new ArrayList<>();
        List<SlotFake> enabledSlots = Arrays.stream(slots).filter(SlotFake::isEnabled).collect(Collectors.toList());
        for (final Slot s : enabledSlots) {
            ItemStack st = s.getStack();
            if (st == null) continue;
            if (st.stackSize * 2 > 127) {
                overFlowStacks.add(st.copy());
            } else {
                st.stackSize *= 2;
                s.putStack(st);
            }
        }
        Iterator<ItemStack> ow = overFlowStacks.iterator();
        for (final Slot s : enabledSlots) {
            if (!ow.hasNext()) break;
            if (s.getStack() != null) continue;
            s.putStack(ow.next());
        }
        assert !ow.hasNext();
    }

    public abstract void doubleStacks(boolean isShift);

    @Override
    public IInventory getInventoryByName(final String name) {
        if (name.equals("player")) {
            return this.getInventoryPlayer();
        }
        return this.patternTerminal.getInventoryByName(name);
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    public AbstractPartPatternTerm getPatternTerminal() {
        return patternTerminal;
    }
}
