package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;

import appeng.api.util.AEColor;

public class GuiColorButton extends GuiAeButton {

    private AEColor color;

    public GuiColorButton(int id, int xPosition, int yPosition, int width, int height, AEColor color,
            String tootipString) {
        super(id, xPosition, yPosition, width, height, "", tootipString);
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

            drawVerticalLine(this.xPosition, this.yPosition, this.yPosition + this.height - 1, 0xFF404040);
            drawVerticalLine(
                    this.xPosition + this.width - 1,
                    this.yPosition,
                    this.yPosition + this.height - 1,
                    0xFF404040);
            drawHorizontalLine(this.xPosition, this.xPosition + this.width - 1, this.yPosition, 0xFF404040);
            drawHorizontalLine(
                    this.xPosition,
                    this.xPosition + this.width - 1,
                    this.yPosition + this.height - 1,
                    0xFF404040);

            this.mouseDragged(mc, mouseX, mouseY);
        }
    }

    public AEColor getColor() {
        return color;
    }
}
