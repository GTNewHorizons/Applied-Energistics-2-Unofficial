package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiCoolButton extends GuiImgButton {

    GuiButton[] insideButtons;

    boolean inFocus;

    public GuiCoolButton(int x, int y, Enum idx, Enum val) {
        super(x, y, idx, val);
    }

    public void setInsideButtons(GuiButton[] insideButtons) {
        this.insideButtons = insideButtons;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);

        if (inFocus) drawFocused();
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        final boolean clickedOn = super.mousePressed(mc, mouseX, mouseY);
        if (inFocus) {
            if (!clickedOn) inFocus = false;
        } else if (clickedOn) inFocus = true;
        return clickedOn;
    }

    private void drawFocused() {

    }
}
