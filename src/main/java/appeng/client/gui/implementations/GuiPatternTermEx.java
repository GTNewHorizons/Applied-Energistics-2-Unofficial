package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.PatternSlotConfig;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

public class GuiPatternTermEx extends GuiPatternTerm {

    private final ContainerPatternTermEx container;
    private GuiImgButton invertBtn;
    private final GuiScrollbar processingScrollBar = new GuiScrollbar();

    private boolean isInverted;
    private int activePage;

    public GuiPatternTermEx(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTermEx(inventoryPlayer, te));
        this.container = (ContainerPatternTermEx) this.inventorySlots;
        this.setReservedSpace(81);

        processingScrollBar.setHeight(70).setWidth(7).setLeft(6).setRange(0, 1, 1);
        processingScrollBar.setTexture(AppEng.MOD_ID, "guis/pattern3.png", 242, 0);

        this.isInverted = this.container.inverted;
        this.activePage = this.container.activePage;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (this.invertBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("PatternTerminalEx.Invert", container.inverted ? "0" : "1"));
                container.getExPatternTerminal().setInverted(container.inverted);
                this.updateSlotVisibility();
            }
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        invertBtn = new GuiImgButton(
                this.guiLeft + 87,
                this.guiTop + this.ySize - 153,
                Settings.ACTIONS,
                container.inverted ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
        invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        processingScrollBar.setTop(this.ySize - 164);
        this.updateSlotVisibility();
    }

    protected int getInputSlotOffsetX() {
        return 15;
    }

    protected int getInputSlotOffsetY() {
        return 32;
    }

    protected int getOutputSlotOffsetX() {
        return 58;
    }

    protected int getOutputSlotOffsetY() {
        return 32;
    }

    @Override
    protected void drawTitle() {
        this.fontRendererObj.drawString(
                GuiText.PatternTerminalEx.getLocal(),
                8,
                this.ySize - 96 + 2 - this.getReservedSpace(),
                GuiColors.PatternTerminalEx.getColor());
        this.processingScrollBar.draw(this);
    }

    @Override
    protected String getBackground() {
        return container.inverted ? "guis/pattern4.png" : "guis/pattern3.png";
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        final int offset = container.inverted ? 18 * -3 : 0;

        substitutionsEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        substitutionsDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        beSubstitutionsEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        beSubstitutionsDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        doubleBtn.xPosition = this.guiLeft + 97 + offset;
        clearBtn.xPosition = this.guiLeft + 87 + offset;
        invertBtn.xPosition = this.guiLeft + 87 + offset;

        processingScrollBar.setCurrentScroll(container.activePage);

        if (this.isInverted != this.container.inverted || this.activePage != this.container.activePage) {
            this.isInverted = this.container.inverted;
            this.activePage = this.container.activePage;
            this.updateSlotVisibility();
        }

        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        final int currentScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);

        super.mouseClicked(xCoord, yCoord, btn);

        if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
            changeActivePage();
        }
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        final int currentScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.clickMove(y - this.guiTop);
        super.mouseClickMove(x, y, c, d);

        if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
            changeActivePage();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();

        final int wheel = Mouse.getEventDWheel();

        if (wheel != 0) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight;

            if (this.processingScrollBar.contains(x - this.guiLeft, y - this.guiTop)) {
                final int currentScroll = this.processingScrollBar.getCurrentScroll();
                this.processingScrollBar.wheel(wheel);

                if (currentScroll != this.processingScrollBar.getCurrentScroll()) {
                    changeActivePage();
                }
            }
        }
    }

    private void changeActivePage() {
        try {
            NetworkHandler.instance.sendToServer(
                    new PacketValueConfig(
                            "PatternTerminalEx.ActivePage",
                            String.valueOf(this.processingScrollBar.getCurrentScroll())));
            container.activePage = this.processingScrollBar.getCurrentScroll();
            this.updateSlotVisibility();
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    @Override
    protected void updateSlotVisibility() {
        final int inputSlotRows = container.getPatternInputsHeigh();
        final int inputSlotsPerRow = container.getPatternInputsWidth();
        final int inputSlotPages = container.getPatternInputPages();

        for (int page = 0; page < inputSlotPages; page++) {
            for (int y = 0; y < inputSlotRows; y++) {
                for (int x = 0; x < inputSlotsPerRow; x++) {
                    final int slotIndex = x + y * inputSlotsPerRow + page * (inputSlotsPerRow * inputSlotRows);
                    VirtualMEPhantomSlot slot = this.craftingSlots[slotIndex];

                    slot.setHidden(this.isInverted ? y != this.activePage || page > 0 : page != this.activePage);

                    slot.setX(getInputSlotOffsetX() + 18 * (this.isInverted ? 0 : x));
                    slot.setY(this.rows * 18 + getInputSlotOffsetY() + 18 * (this.isInverted ? x : y));
                }
            }
        }

        final int outputSlotRows = container.getPatternOutputsHeigh();
        final int outputSlotsPerRow = container.getPatternOutputsWidth();
        final int outputSlotPages = container.getPatternOutputPages();

        for (int page = 0; page < outputSlotPages; page++) {
            for (int y = 0; y < outputSlotRows; y++) {
                for (int x = 0; x < outputSlotsPerRow; x++) {
                    final int slotIndex = x + y * outputSlotsPerRow + page * (outputSlotsPerRow * outputSlotRows);
                    VirtualMEPhantomSlot slot = this.outputSlots[slotIndex];

                    slot.setHidden(!this.isInverted ? y != this.activePage || page != 0 : page != this.activePage);

                    slot.setX((this.isInverted ? getOutputSlotOffsetX() : 112) + 18 * (!this.isInverted ? 0 : x));
                    slot.setY(this.rows * 18 + getOutputSlotOffsetY() + 18 * (!this.isInverted ? x : y));
                }
            }
        }
    }
}
