package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.PatternBeSubstitution;
import appeng.api.config.PatternSlotConfig;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.slot.AppEngSlot;
import appeng.core.AppEng;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

public class GuiPatternTermEx extends GuiMEMonitorable {

    private static final String SUBSITUTION_DISABLE = "0";
    private static final String SUBSITUTION_ENABLE = "1";

    private final ContainerPatternTermEx container;

    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton beSubstitutionsEnabledBtn;
    private GuiImgButton beSubstitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton invertBtn;
    private GuiImgButton doubleBtn;
    private final GuiScrollbar processingScrollBar = new GuiScrollbar();

    public GuiPatternTermEx(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTermEx(inventoryPlayer, te));
        this.container = (ContainerPatternTermEx) this.inventorySlots;
        this.setReservedSpace(81);

        processingScrollBar.setHeight(70).setWidth(7).setLeft(6).setRange(0, 1, 1);
        processingScrollBar.setTexture(AppEng.MOD_ID, "guis/pattern3.png", 242, 0);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (this.encodeBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.Encode",
                                isCtrlKeyDown() ? (isShiftKeyDown() ? "6" : "1") : (isShiftKeyDown() ? "2" : "1")));
            } else if (this.clearBtn == btn) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("PatternTerminalEx.Clear", "1"));
            } else if (this.invertBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("PatternTerminalEx.Invert", container.inverted ? "0" : "1"));
            } else if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.Substitute",
                                this.substitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            } else if (this.beSubstitutionsEnabledBtn == btn || this.beSubstitutionsDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.BeSubstitute",
                                this.beSubstitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            } else if (doubleBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.Double",
                                Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? "1" : "0"));
            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.substitutionsEnabledBtn = new GuiImgButton(
                this.guiLeft + 97,
                this.guiTop + this.ySize - 163,
                Settings.ACTIONS,
                ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(
                this.guiLeft + 97,
                this.guiTop + this.ySize - 163,
                Settings.ACTIONS,
                ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.beSubstitutionsEnabledBtn = new GuiImgButton(
                this.guiLeft + 97,
                this.guiTop + this.ySize - 143,
                Settings.ACTIONS,
                PatternBeSubstitution.ENABLED);
        this.beSubstitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsEnabledBtn);

        this.beSubstitutionsDisabledBtn = new GuiImgButton(
                this.guiLeft + 97,
                this.guiTop + this.ySize - 143,
                Settings.ACTIONS,
                PatternBeSubstitution.DISABLED);
        this.beSubstitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(
                this.guiLeft + 87,
                this.guiTop + this.ySize - 163,
                Settings.ACTIONS,
                ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        this.encodeBtn = new GuiImgButton(
                this.guiLeft + 147,
                this.guiTop + this.ySize - 142,
                Settings.ACTIONS,
                ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        invertBtn = new GuiImgButton(
                this.guiLeft + 87,
                this.guiTop + this.ySize - 153,
                Settings.ACTIONS,
                container.inverted ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
        invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        this.doubleBtn = new GuiImgButton(
                this.guiLeft + 97,
                this.guiTop + this.ySize - 153,
                Settings.ACTIONS,
                ActionItems.DOUBLE);
        this.doubleBtn.setHalfSize(true);
        this.buttonList.add(this.doubleBtn);

        processingScrollBar.setTop(this.ySize - 164);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
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
    protected void repositionSlot(final AppEngSlot s) {
        if (s.isPlayerSide()) {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 5;
        } else {
            s.yDisplayPosition = s.getY() + this.ySize - 78 - 3;
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {

        if (container.substitute) {
            substitutionsEnabledBtn.visible = true;
            substitutionsDisabledBtn.visible = false;
        } else {
            substitutionsEnabledBtn.visible = false;
            substitutionsDisabledBtn.visible = true;
        }

        this.beSubstitutionsEnabledBtn.visible = this.container.beSubstitute;
        this.beSubstitutionsDisabledBtn.visible = !this.container.beSubstitute;

        final int offset = container.inverted ? 18 * -3 : 0;

        substitutionsEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        substitutionsDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        beSubstitutionsEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        beSubstitutionsDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        doubleBtn.xPosition = this.guiLeft + 97 + offset;
        clearBtn.xPosition = this.guiLeft + 87 + offset;
        invertBtn.xPosition = this.guiLeft + 87 + offset;

        processingScrollBar.setCurrentScroll(container.activePage);

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
        this.processingScrollBar.click(this, x - this.guiLeft, y - this.guiTop);
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
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
