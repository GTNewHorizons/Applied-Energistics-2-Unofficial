package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import appeng.client.texture.ExtraBlockTextures;
import appeng.util.Platform;

public class GuiSimpleImgButton extends GuiButton implements ITooltip {

    private int iconIndex;
    private String tooltip;

    public GuiSimpleImgButton(final int x, final int y, final int iconIndex, final String tooltip) {
        super(0, 0, 16, "");

        this.xPosition = x;
        this.yPosition = y;
        this.width = 16;
        this.height = 16;
        this.iconIndex = iconIndex;
        this.tooltip = tooltip;
    }

    public void setVisibility(final boolean vis) {
        this.visible = vis;
        this.enabled = vis;
    }

    @Override
    public void drawButton(final Minecraft par1Minecraft, final int par2, final int par3) {
        if (this.visible) {
            if (this.enabled) {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GL11.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
            }

            par1Minecraft.renderEngine.bindTexture(ExtraBlockTextures.GuiTexture("guis/states.png"));
            this.field_146123_n = par2 >= this.xPosition && par3 >= this.yPosition
                    && par2 < this.xPosition + this.width
                    && par3 < this.yPosition + this.height;

            final int uv_y = iconIndex / 16;
            final int uv_x = iconIndex - uv_y * 16;

            this.drawTexturedModalRect(this.xPosition, this.yPosition, 256 - 16, 256 - 16, 16, 16);
            this.drawTexturedModalRect(this.xPosition, this.yPosition, uv_x * 16, uv_y * 16, 16, 16);
            this.mouseDragged(par1Minecraft, par2, par3);
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public String getMessage() {
        if (Platform.isServer()) {
            return tooltip;
        }

        if (!tooltip.contains("\n")) {
            return tooltip;
        }

        int i = tooltip.indexOf("\n");
        String name = tooltip.substring(0, i);
        String value = tooltip.substring(i + 1);

        value = Minecraft.getMinecraft().fontRenderer.wrapFormattedStringToWidth(value, 150);

        return name + '\n' + value;
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
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public int getIconIndex() {
        return iconIndex;
    }

    public void setIconIndex(int iconIndex) {
        this.iconIndex = iconIndex;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }
}
