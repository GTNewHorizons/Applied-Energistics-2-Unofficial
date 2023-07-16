package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.config.*;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.core.AppEng;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.Reflected;

@SuppressWarnings("unused")
public class GuiPatternTermEx extends GuiBasePatternTerm {

    private final GuiImgButton invertBtn;
    private final GuiScrollbar processingScrollBar = new GuiScrollbar();

    @Reflected
    @SuppressWarnings("unused")
    public GuiPatternTermEx(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTermEx(inventoryPlayer, te));

        processingScrollBar.setHeight(70).setWidth(7).setLeft(6).setRange(0, 1, 1);
        processingScrollBar.setTexture(AppEng.MOD_ID, "guis/pattern3.png", 242, 0);

        invertBtn = new GuiImgButton(
                this.guiLeft + 87,
                this.guiTop + this.ySize - 153,
                Settings.ACTIONS,
                ((ContainerPatternTermEx) container).inverted ? PatternSlotConfig.C_4_16 : PatternSlotConfig.C_16_4);
        invertBtn.setHalfSize(true);
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
                        new PacketValueConfig(
                                "PatternTerminalEx.Invert",
                                ((ContainerPatternTermEx) container).inverted ? "0" : "1"));
            } else if (this.subsEnabledBtn == btn || this.subsDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.Substitute",
                                this.subsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            } else if (this.amSubEnabledBtn == btn || this.amSubDisabledBtn == btn) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig(
                                "PatternTerminalEx.BeSubstitute",
                                this.amSubEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
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
    protected void initLayout() {
        btnArrLeft = this.guiLeft + 87;
        btnArrTop = this.guiTop + this.ySize - 163;
        btnOffsetX = 10;
        btnOffsetY = 10;
    }

    @Override
    public void initGui() {
        super.initGui();
        layoutButton(invertBtn, 0, 2);
        buttonList.add(invertBtn);

        processingScrollBar.setTop(this.ySize - 164);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        final int offset = ((ContainerPatternTermEx) container).inverted ? 18 * -3 : 0;

        subsEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        subsDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        amSubEnabledBtn.xPosition = this.guiLeft + 97 + offset;
        amSubDisabledBtn.xPosition = this.guiLeft + 97 + offset;
        doubleBtn.xPosition = this.guiLeft + 97 + offset;
        clearBtn.xPosition = this.guiLeft + 87 + offset;
        invertBtn.xPosition = this.guiLeft + 87 + offset;

        processingScrollBar.setCurrentScroll(((ContainerPatternTermEx) container).activePage);
        this.fontRendererObj.drawString(
                GuiText.PatternTerminalEx.getLocal(),
                8,
                this.ySize - 96 + 2 - this.getReservedSpace(),
                GuiColors.PatternTerminalEx.getColor());
        this.processingScrollBar.draw(this);
    }

    @Override
    protected String getBackground() {
        return ((ContainerPatternTermEx) container).inverted ? "guis/pattern4.png" : "guis/pattern3.png";
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
