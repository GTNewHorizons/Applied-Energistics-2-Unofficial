package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import appeng.client.texture.ExtraBlockTextures;
import appeng.core.localization.ButtonToolTips;

public class TypeToggleButton extends GuiButton implements ITooltip {

    private final ResourceLocation texture;
    private final IIcon icon;
    private boolean enabled = true;

    public TypeToggleButton(int x, int y, ResourceLocation texture, IIcon icon, String buttonText) {
        super(0, x, y, buttonText);
        this.texture = texture;
        this.icon = icon;
        this.width = 16;
        this.height = 16;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                    && mouseX < this.xPosition + this.getWidth()
                    && mouseY < this.yPosition + this.getHeight();
            if (this.enabled) {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GL11.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
            }

            mc.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslatef(this.xPosition, this.yPosition, 0.0F);

            this.drawTexturedModalRect(0, 0, 256 - 16, 256 - 16, 16, 16);

            if (this.texture != null && this.icon != null) {
                mc.renderEngine.bindTexture(this.texture);
                this.drawTexturedModelRectFromIcon(0, 0, this.icon, 16, 16);
            }

            GL11.glTranslatef(-this.xPosition, -this.yPosition, 0.0F);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getMessage() {
        String tooptip = this.displayString + "\n";

        if (this.enabled) {
            tooptip += EnumChatFormatting.GRAY + ButtonToolTips.Enable.getLocal() + EnumChatFormatting.RESET;
        } else {
            tooptip += EnumChatFormatting.GRAY + ButtonToolTips.Disabled.getLocal() + EnumChatFormatting.RESET;
        }

        return tooptip;
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
