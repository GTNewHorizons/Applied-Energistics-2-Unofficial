/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;

/**
 * A modified version of the Minecraft text field. You can initialize it over the full element span. The mouse click
 * area is increased to the full element subtracted with the defined padding.
 * <p>
 * The rendering does pay attention to the size of the '_' caret.
 */
public class MEGuiTextField implements ITooltip {

    protected GuiTextField field;

    private static final int PADDING = 2;
    private static boolean previousKeyboardRepeatEnabled;
    private static MEGuiTextField previousKeyboardRepeatEnabledField;
    private String tooltip;
    private int fontPad;

    public int x;
    public int y;
    public int w;
    public int h;

    /**
     * Uses the values to instantiate a padded version of a text field. Pays attention to the '_' caret.
     *
     * @param width   absolute width
     * @param height  absolute height
     * @param tooltip tooltip message
     */
    public MEGuiTextField(final int width, final int height, final String tooltip) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        field = new GuiTextField(fontRenderer, 0, 0, 0, 0);

        w = width;
        h = height;

        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(AEConfig.instance.quartzKnifeInputLength);
        field.setTextColor(GuiColors.SearchboxText.getColor());
        field.setCursorPositionZero();

        setMessage(tooltip);

        this.fontPad = fontRenderer.getCharWidth('_');

        setDimensionsAndColor();
    }

    public MEGuiTextField(final int width, final int height) {
        this(width, height, "");
    }

    public MEGuiTextField() {
        this(0, 0);
    }

    protected void setDimensionsAndColor() {
        field.xPosition = this.x + PADDING;
        field.yPosition = this.y + PADDING;
        field.width = this.w - PADDING * 2 - this.fontPad;
        field.height = this.h - PADDING * 2;
    }

    public void onTextChange(final String oldText) {}

    public void mouseClicked(final int xPos, final int yPos, final int button) {

        if (!this.isMouseIn(xPos, yPos)) {
            setFocused(false);
            return;
        }

        field.setCanLoseFocus(false);
        setFocused(true);

        if (button == 1) {
            setText("");
        } else {
            field.mouseClicked(xPos, yPos, button);
        }

        field.setCanLoseFocus(true);
    }

    /**
     * Checks if the mouse is within the element
     *
     * @param xCoord current x coord of the mouse
     * @param yCoord current y coord of the mouse
     * @return true if mouse position is within the getText field area
     */
    public boolean isMouseIn(final int xCoord, final int yCoord) {
        final boolean withinXRange = this.x <= xCoord && xCoord < this.x + this.w;
        final boolean withinYRange = this.y <= yCoord && yCoord < this.y + this.h;

        return withinXRange && withinYRange;
    }

    public boolean textboxKeyTyped(final char keyChar, final int keyID) {
        if (!isFocused()) {
            return false;
        }

        final String oldText = getText();
        boolean handled = field.textboxKeyTyped(keyChar, keyID);

        if (!handled && (keyID == Keyboard.KEY_RETURN || keyID == Keyboard.KEY_NUMPADENTER
                || keyID == Keyboard.KEY_ESCAPE)) {
            setFocused(false);
        }

        if (handled) {
            onTextChange(oldText);
        }

        return handled;
    }

    public void drawTextBox() {
        if (field.getVisible()) {
            setDimensionsAndColor();
            GuiTextField.drawRect(
                    this.x + 1,
                    this.y + 1,
                    this.x + this.w - 1,
                    this.y + this.h - 1,
                    isFocused() ? GuiColors.SearchboxFocused.getColor() : GuiColors.SearchboxUnfocused.getColor());
            field.drawTextBox();
        }
    }

    public void setText(String text, boolean ignoreTrigger) {
        final String oldText = getText();

        int currentCursorPos = field.getCursorPosition();
        field.setText(text);
        field.setCursorPosition(currentCursorPos);

        if (!ignoreTrigger) {
            onTextChange(oldText);
        }
    }

    public void setText(String text) {
        setText(text, false);
    }

    public void setCursorPositionEnd() {
        field.setCursorPositionEnd();
    }

    public void setFocused(boolean focus) {
        if (field.isFocused() == focus) {
            return;
        }

        field.setFocused(focus);

        if (focus) {

            if (previousKeyboardRepeatEnabledField == null) {
                previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
            }

            previousKeyboardRepeatEnabledField = this;
            Keyboard.enableRepeatEvents(true);
        } else {

            if (previousKeyboardRepeatEnabledField == this) {
                previousKeyboardRepeatEnabledField = null;
                Keyboard.enableRepeatEvents(previousKeyboardRepeatEnabled);
            }
        }
    }

    public void setMaxStringLength(final int size) {
        field.setMaxStringLength(size);
    }

    public boolean isFocused() {
        return field.isFocused();
    }

    public String getText() {
        return field.getText();
    }

    public void setMessage(String t) {
        tooltip = t;
    }

    @Override
    public String getMessage() {
        return tooltip;
    }

    @Override
    public boolean isVisible() {
        return field.getVisible();
    }

    @Override
    public int xPos() {
        return x;
    }

    @Override
    public int yPos() {
        return y;
    }

    @Override
    public int getWidth() {
        return w;
    }

    @Override
    public int getHeight() {
        return h;
    }
}
