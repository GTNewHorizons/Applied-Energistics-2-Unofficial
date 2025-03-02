package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import appeng.api.storage.ITerminalHost;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.IContainerCraftingPacket;
import appeng.parts.reporting.PartPatternTerminalEx;
import appeng.util.Platform;

public class ContainerPatternTermEx extends ContainerPatternTermBase
        implements IOptionalSlotHost, IContainerCraftingPacket {

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

    @GuiSync(96 + (17 - 9) + 16)
    public boolean inverted;

    @GuiSync(96 + (17 - 9) + 17)
    public int activePage = 0;

    public ContainerPatternTermEx(final InventoryPlayer ip, final ITerminalHost monitorable) {
        super(ip, monitorable);

        this.CRAFTING_GRID_PAGES = 2;
        this.CRAFTING_GRID_WIDTH = 4;
        this.CRAFTING_GRID_HEIGHT = 4;
        this.CRAFTING_GRID_SLOTS = CRAFTING_GRID_WIDTH * CRAFTING_GRID_HEIGHT;

        this.patternTerminal = (PartPatternTerminalEx) monitorable;

        this.craftingMode = false;

        this.craftingSlots = new ProcessingSlotFake[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];
        this.outputSlots = new ProcessingSlotFake[CRAFTING_GRID_SLOTS * CRAFTING_GRID_PAGES];

        inverted = patternTerminal.isInverted();

        final IInventory patternInv = this.getPatternTerminal().getInventoryByName("pattern");
        final IInventory output = this.getPatternTerminal().getInventoryByName("output");
        final IInventory crafting = this.getPatternTerminal().getInventoryByName("crafting");

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
        if (getPatternTerminal().hasRefillerUpgrade()) refillBlankPatterns(patternSlotIN);
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

            if (inverted != patternTerminal.isInverted()
                    || activePage != ((PartPatternTerminalEx) patternTerminal).getActivePage()) {
                inverted = patternTerminal.isInverted();
                activePage = ((PartPatternTerminalEx) patternTerminal).getActivePage();
                offsetSlots();
            }
        }
    }

    private void offsetSlots() {

        for (int page = 0; page < CRAFTING_GRID_PAGES; page++) {
            for (int y = 0; y < CRAFTING_GRID_HEIGHT; y++) {
                for (int x = 0; x < CRAFTING_GRID_WIDTH; x++) {
                    ((ProcessingSlotFake) this.craftingSlots[x + y * CRAFTING_GRID_WIDTH + page * CRAFTING_GRID_SLOTS])
                            .setHidden(page != activePage || x > 0 && inverted);
                    ((ProcessingSlotFake) this.outputSlots[x * CRAFTING_GRID_HEIGHT + y + page * CRAFTING_GRID_SLOTS])
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
    public PartPatternTerminalEx getPatternTerminal() {
        return (PartPatternTerminalEx) this.patternTerminal;
    }
}
