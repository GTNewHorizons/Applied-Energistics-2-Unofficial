package appeng.client.gui.widgets;

import java.util.Objects;

import net.minecraft.client.Minecraft;

import appeng.api.util.AEColor;
import appeng.core.localization.GuiColors;

public class GuiColorButton extends GuiAeButton {

    private final AEColor color;
    public boolean drawDisplayString = true;

    public GuiColorButton(int id, int xPosition, int yPosition, int width, int height, AEColor color,
            String tootipString) {
        this(id, xPosition, yPosition, width, height, color, "", tootipString);
    }

    public GuiColorButton(int id, int xPosition, int yPosition, int width, int height, AEColor color,
            String displayString, String tootipString) {
        super(id, xPosition, yPosition, width, height, displayString, tootipString);
        this.color = color;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.width
                    && mouseY < this.yPosition + this.height;
            int color;
            if (this.field_146123_n) {
                color = this.color.blackVariant - 16777216;
            } else {
                color = this.color.mediumVariant - 16777216;
            }
            drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, color);

            drawVerticalLine(
                    this.xPosition,
                    this.yPosition,
                    this.yPosition + this.height - 1,
                    GuiColors.ColorButtonOutline.getColor());
            drawVerticalLine(
                    this.xPosition + this.width - 1,
                    this.yPosition,
                    this.yPosition + this.height - 1,
                    GuiColors.ColorButtonOutline.getColor());
            drawHorizontalLine(
                    this.xPosition,
                    this.xPosition + this.width - 1,
                    this.yPosition,
                    GuiColors.ColorButtonOutline.getColor());
            drawHorizontalLine(
                    this.xPosition,
                    this.xPosition + this.width - 1,
                    this.yPosition + this.height - 1,
                    GuiColors.ColorButtonOutline.getColor());

            if (this.drawDisplayString && !Objects.equals(this.displayString, "")) {
                mc.fontRenderer.drawString(
                        this.displayString,
                        this.xPosition + this.width / 2 - mc.fontRenderer.getStringWidth(this.displayString) / 2,
                        this.yPosition + (this.height - 8) / 2,
                        this.getTextColor());
            }

            this.mouseDragged(mc, mouseX, mouseY);
        }
    }

    private int getTextColor() {
        final int color = this.color.mediumVariant;
        return ((0.2126 * ((color >> 16) & 0xFF) + 0.7152 * ((color >> 8) & 0xFF) + 0.0722 * (color & 0xFF)) > 128)
                ? 0xFF000000
                : 0xFFFFFFFF;
    }

    public AEColor getColor() {
        return color;
    }
}
