package appeng.client.gui.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.ScreenColor;
import appeng.client.gui.widgets.GuiAeButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerColorizer;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.items.contents.ColorizerObj;
import cpw.mods.fml.client.config.GuiSlider;

public class GuiColorizer extends AEBaseGui implements GuiSlider.ISlider {

    private static final int RED_SLIDER_ID = 0;
    private static final int GREEN_SLIDER_ID = 1;
    private static final int BLUE_SLIDER_ID = 2;
    private static final int SET_BUTTON_ID = 3;

    private GuiSlider redSlider;
    private GuiSlider greenSlider;
    private GuiSlider blueSlider;
    private GuiButton setButton;
    private MEGuiTextField hexInput;
    private int currentColor;
    private boolean updatingControls;

    public GuiColorizer(final InventoryPlayer inventoryPlayer, final ColorizerObj te) {
        super(new ContainerColorizer(inventoryPlayer, te));
        this.xSize = 176;
        this.ySize = 237;
        this.currentColor = AEConfig.instance.getScreenColor();

        this.hexInput = new MEGuiTextField(100, 20) {

            @Override
            public void onTextChange(final String oldText) {
                GuiColorizer.this.onHexChanged();
            }
        };
        this.hexInput.setMaxStringLength(7);
        this.hexInput.setUnfocusWithEnter(false);
        this.hexInput.setText(this.formatColor(this.currentColor), true);
    }

    @Override
    public void initGui() {
        super.initGui();

        final int red = (this.currentColor >> 16) & 0xFF;
        final int green = (this.currentColor >> 8) & 0xFF;
        final int blue = this.currentColor & 0xFF;

        this.redSlider = this
                .addColorSlider(RED_SLIDER_ID, this.guiLeft + 7, this.guiTop + 20, GuiText.Red.getLocal() + ": ", red);
        this.greenSlider = this.addColorSlider(
                GREEN_SLIDER_ID,
                this.guiLeft + 7,
                this.guiTop + 45,
                GuiText.Green.getLocal() + ": ",
                green);
        this.blueSlider = this.addColorSlider(
                BLUE_SLIDER_ID,
                this.guiLeft + 7,
                this.guiTop + 70,
                GuiText.Blue.getLocal() + ": ",
                blue);

        this.setButton = new GuiAeButton(
                SET_BUTTON_ID,
                this.guiLeft + 112,
                this.guiTop + 95,
                57,
                12,
                GuiText.Set.getLocal(),
                "");
        this.buttonList.add(this.setButton);

        this.hexInput.x = this.guiLeft + 7;
        this.hexInput.y = this.guiTop + 95;
        this.hexInput.setText(this.formatColor(this.currentColor), true);
    }

    private GuiSlider addColorSlider(final int id, final int x, final int y, final String label, final int value) {
        final var slider = new ColorSlider(id, x, y, 162, 20, label, value, this);
        this.buttonList.add(slider);
        return slider;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.Colorizer.getLocal()),
                8,
                6,
                GuiColors.DefaultBlack.getColor());
        this.fontRendererObj
                .drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, GuiColors.DefaultBlack.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/colorizer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.hexInput.drawTextBox();

        final int left = offsetX + 7;
        final int top = offsetY + 110;
        final int right = left + 162;
        final int bottom = top + 32;

        drawRect(left, top, right, bottom, 0xFF000000 | this.currentColor);
        this.drawHorizontalLine(left, right - 1, top, 0xFFFFFFFF);
        this.drawHorizontalLine(left, right - 1, bottom - 1, 0xFFFFFFFF);
        this.drawVerticalLine(left, top, bottom - 1, 0xFFFFFFFF);
        this.drawVerticalLine(right - 1, top, bottom - 1, 0xFFFFFFFF);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) {
        this.hexInput.mouseClicked(xCoord, yCoord, btn);
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) {
        if (this.hexInput.textboxKeyTyped(character, key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.confirmColor();
            }
            return;
        }

        super.keyTyped(character, key);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (btn == this.setButton || btn.id == SET_BUTTON_ID) {
            this.confirmColor();
            return;
        }

        super.actionPerformed(btn);
    }

    @Override
    public void onChangeSliderValue(final GuiSlider slider) {
        if (this.updatingControls) {
            return;
        }

        this.currentColor = (this.redSlider.getValueInt() << 16) | (this.greenSlider.getValueInt() << 8)
                | this.blueSlider.getValueInt();

        if (!this.hexInput.isFocused()) {
            this.hexInput.setText(this.formatColor(this.currentColor), true);
        }
    }

    private void onHexChanged() {
        if (this.updatingControls) {
            return;
        }

        final String hex = this.hexInput.getText();
        if (!this.isValidHex(hex)) {
            return;
        }

        try {
            this.currentColor = Integer.parseInt(hex.substring(1), 16) & 0xFFFFFF;
            this.setSlidersFromColor(this.currentColor);
        } catch (final NumberFormatException ignored) {}
    }

    private void setSlidersFromColor(final int color) {
        this.updatingControls = true;
        this.setSliderValue(this.redSlider, (color >> 16) & 0xFF);
        this.setSliderValue(this.greenSlider, (color >> 8) & 0xFF);
        this.setSliderValue(this.blueSlider, color & 0xFF);
        this.updatingControls = false;
    }

    private void setSliderValue(final GuiSlider slider, final int value) {
        if (slider == null) {
            return;
        }

        slider.setValue(value);
        slider.updateSlider();
    }

    private boolean isValidHex(final String hex) {
        return hex != null && hex.matches("^#[0-9a-fA-F]{6}$");
    }

    private String formatColor(final int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private void confirmColor() {
        AEConfig.instance.setScreenColor(this.currentColor);
        this.mc.thePlayer.closeScreen();
    }

    private static final class ColorSlider extends GuiSlider {

        private ColorSlider(final int id, final int x, final int y, final int width, final int height,
                final String label, final int value, final GuiSlider.ISlider parent) {
            super(id, x, y, width, height, label, "", 0, 255, value, false, true, parent);
        }

        @Override
        public void drawButton(final Minecraft mc, final int mouseX, final int mouseY) {
            ScreenColor.setGuiColor();
            super.drawButton(mc, mouseX, mouseY);
            ScreenColor.resetGuiColor();
        }

        @Override
        protected void mouseDragged(final Minecraft mc, final int mouseX, final int mouseY) {
            if (this.visible) {
                if (this.dragging) {
                    this.sliderValue = (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
                    this.updateSlider();
                }

                ScreenColor.setGuiColor();
                this.drawTexturedModalRect(
                        this.xPosition + (int) (this.sliderValue * (float) (this.width - 8)),
                        this.yPosition,
                        0,
                        66,
                        4,
                        20);
                this.drawTexturedModalRect(
                        this.xPosition + (int) (this.sliderValue * (float) (this.width - 8)) + 4,
                        this.yPosition,
                        196,
                        66,
                        4,
                        20);
                ScreenColor.resetGuiColor();
            }
        }
    }
}
