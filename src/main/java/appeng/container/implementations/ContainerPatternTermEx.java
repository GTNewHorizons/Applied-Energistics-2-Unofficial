package appeng.container.implementations;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.storage.ITerminalHost;
import appeng.container.ContainerBasePatternTerm;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.util.Platform;

public class ContainerPatternTermEx extends ContainerBasePatternTerm {

    private static final int CRAFTING_GRID_PAGES = 2;
    private static final int CRAFTING_GRID_WIDTH = 4;
    private static final int CRAFTING_GRID_HEIGHT = 4;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

    private static class ProcessingSlotFake extends OptionalSlotFake {

        private static final int POSITION_SHIFT = 9000;
        private boolean hidden = false;

        public ProcessingSlotFake(IInventory inv, IOptionalSlotHost containerBus, int idx, int x, int y, int offX,
                int offY, int groupNum) {
            super(inv, containerBus, idx, x, y, offX, offY, groupNum);
            this.setRenderDisabled(false);
        }

        public void setHidden(boolean hide) {
            if (this.hidden != hide) {
                this.hidden = hide;
                this.xDisplayPosition += (hide ? -1 : 1) * POSITION_SHIFT;
            }
        }
    }

    private final ProcessingSlotFake[] craftingSlots = new ProcessingSlotFake[CRAFTING_GRID_SLOTS
            * CRAFTING_GRID_PAGES];
    private final ProcessingSlotFake[] outputSlots = new ProcessingSlotFake[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];

    // @GuiSync(96 + (17 - 9) + 12)
    // public boolean substitute = false;
    //
    // @GuiSync(96 + (17 - 9) + 13)
    // public boolean beSubstitute = false;

    @GuiSync(96 + (17 - 9) + 16)
    public boolean inverted;

    @GuiSync(96 + (17 - 9) + 17)
    public int activePage = 0;

    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);
        inverted = ((PartPatternTerminalEx) patternTerminal).isInverted();
        final IInventory output = this.patternTerminal.getInventoryByName("output");
        final IInventory crafting = this.patternTerminal.getInventoryByName("crafting");

        for (int page = 0; page < CRAFTING_GRID_PAGES; page++) {
            for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                    this.addSlotToContainer(
                            this.craftingSlots[x + y * CRAFTING_GRID_WIDTH
                                    + page * CRAFTING_GRID_SLOTS] = new ProcessingSlotFake(
                                            crafting,
                                            this,
                                            x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS,
                                            15,
                                            -83,
                                            x,
                                            y,
                                            x + 4));
                }
            }

            for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                    this.addSlotToContainer(
                            this.outputSlots[x * CRAFTING_GRID_HEIGHT + y
                                    + page * CRAFTING_GRID_SLOTS] = new ProcessingSlotFake(
                                            output,
                                            this,
                                            x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS,
                                            112,
                                            -83,
                                            -x,
                                            y,
                                            x));
                }
            }
        }
    }

    @Override
    protected ItemStack[] getInputs() {
        final ItemStack[] input = new ItemStack[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];
        boolean hasValue = false;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            input[x] = this.craftingSlots[x].getStack();
            if (input[x] != null) {
                hasValue = true;
            }
        }

        if (hasValue) {
            return input;
        }

        return null;
    }

    @Override
    protected ItemStack[] getOutputs() {
        final List<ItemStack> list = new ArrayList<>(CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES);
        boolean hasValue = false;

        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();

            if (out != null && out.stackSize > 0) {
                list.add(out);
                hasValue = true;
            }
        }

        if (hasValue) {
            return list.toArray(new ItemStack[0]);
        }

        return null;
    }

    @Override
    public boolean isSlotEnabled(final int idx) {

        if (idx < 4) // outputs
        {
            return inverted || idx == 0;
        } else {
            return !inverted || idx == 4;
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            substitute = patternTerminal.isSubstitution();
            beSubstitute = patternTerminal.canBeSubstitution();
            PartPatternTerminalEx term = (PartPatternTerminalEx) patternTerminal;
            if (inverted != term.isInverted() || activePage != term.getActivePage()) {
                inverted = term.isInverted();
                activePage = term.getActivePage();
                offsetSlots();
            }
        }
    }

    private void offsetSlots() {
        for (int page = 0; page < CRAFTING_GRID_PAGES; page++) {
            for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                    this.craftingSlots[x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS]
                            .setHidden(page != activePage || x > 0 && inverted);
                    this.outputSlots[x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS]
                            .setHidden(page != activePage || x > 0 && !inverted);
                }
            }
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);

        if (field.equals("inverted") || field.equals("activePage")) {
            offsetSlots();
        }
    }

    @Override
    public void onSlotChange(final Slot s) {
        if (!Platform.isServer()) return;
        if (s == this.patternSlotOUT) {
            inverted = ((PartPatternTerminalEx) patternTerminal).isInverted();

            for (final ICrafting crafter : this.crafters) {
                for (final Slot slot : this.inventorySlots) {
                    if (slot instanceof OptionalSlotFake || slot instanceof SlotFakeCraftingMatrix) {
                        crafter.sendSlotContents(this, slot.slotNumber, slot.getStack());
                    }
                }
                ((EntityPlayerMP) crafter).isChangingQuantityOnly = false;
            }
            this.detectAndSendChanges();
        } else if (s == patternRefiller && patternRefiller.getStack() != null) {
            refillBlankPatterns();
            detectAndSendChanges();
        }
    }

    @Override
    public void clear() {
        for (final Slot s : this.craftingSlots) {
            s.putStack(null);
        }

        for (final Slot s : this.outputSlots) {
            s.putStack(null);
        }

        this.detectAndSendChanges();
    }

    public void doubleStacks(boolean isShift) {
        if (canDoubleStacks(craftingSlots) && canDoubleStacks(outputSlots)) {
            doubleStacksInternal(this.craftingSlots);
            doubleStacksInternal(this.outputSlots);
            if (isShift) {
                while (canDoubleStacks(craftingSlots) && canDoubleStacks(outputSlots)) {
                    doubleStacksInternal(this.craftingSlots);
                    doubleStacksInternal(this.outputSlots);
                }
            }
            this.detectAndSendChanges();
        }
    }

    @Override
    public PartPatternTerminalEx getPatternTerminal() {
        return (PartPatternTerminalEx) patternTerminal;
    }
}
