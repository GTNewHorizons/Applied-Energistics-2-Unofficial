package appeng.client.gui.widgets;

import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

public class GuiAeButton extends GuiButton implements ITooltip {

    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", Pattern.LITERAL);

    private String tootipString;

    public GuiAeButton(final int id, final int xPosition, final int yPosition, final int width, final int height,
            final String displayString, final String tootipString) {
        super(id, xPosition, yPosition, width, height, displayString);
        this.tootipString = tootipString;
    }

    public void setTootipString(final String tootipString) {
        this.tootipString = tootipString;
    }

    @Override
    public String getMessage() {
        if (this.tootipString != null) {
            return PATTERN_NEW_LINE.matcher(this.tootipString).replaceAll("\n");
        } else {
            return "";
        }
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);

        if (!this.visible || this.height >= 20) return;
        mc.getTextureManager().bindTexture(buttonTextures);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        int texV = 46 + this.getHoverState(this.field_146123_n) * 20;
        int half = this.width / 2;
        this.drawTexturedModalRect(this.xPosition, this.yPosition + this.height - 2, 0, texV + 18, half, 2);
        this.drawTexturedModalRect(
                this.xPosition + half,
                this.yPosition + this.height - 2,
                200 - half,
                texV + 18,
                half,
                2);
    }

    @Override
    public int xPos() {
        return this.xPosition;
    }

    @Override
    public int yPos() {
        return this.yPosition;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

}
