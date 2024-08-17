package appeng.client.gui.widgets;

import java.util.regex.Pattern;

import net.minecraft.client.gui.GuiButton;

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
