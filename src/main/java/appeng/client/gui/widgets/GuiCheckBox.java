package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

public class GuiCheckBox extends GuiToggleButton {

    final double scale;

    public GuiCheckBox(double scale, int x, int y, String DisplayName, String DisplayHint) {
        this(scale, x, y, 16 * 6 + 13, 16 * 6 + 12, DisplayName, DisplayHint);
    }

    public GuiCheckBox(double scale, int x, int y, int iconOn, int iconOff, String DisplayName, String DisplayHint) {
        super(x, y, iconOn, iconOff, DisplayName, DisplayHint);
        this.scale = scale;
        this.xPosition = (int) Math.ceil(this.xPosition / scale);
        this.yPosition = (int) Math.ceil(this.yPosition / scale);
    }

    @Override
    public void drawButton(Minecraft par1Minecraft, int par2, int par3) {
        GL11.glPushMatrix();
        GL11.glScaled(scale, scale, scale);
        super.drawButton(par1Minecraft, par2, par3);
        GL11.glPopMatrix();

    }

    @Override
    public int xPos() {
        return (int) Math.ceil(this.xPosition * scale);
    }

    @Override
    public int yPos() {
        return (int) Math.ceil(this.yPosition * scale);
    }

    @Override
    public int getWidth() {
        return (int) Math.ceil(this.width * scale);
    }

    @Override
    public int getHeight() {
        return (int) Math.ceil(this.height * scale);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return this.enabled && this.visible
                && mouseX >= xPos()
                && mouseY >= yPos()
                && mouseX < xPos() + getWidth()
                && mouseY < yPos() + getHeight();
    }
}
