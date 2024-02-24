package appeng.client.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;

import org.lwjgl.opengl.GL11;

import appeng.api.config.TerminalFontSize;
import appeng.util.ISlimReadableNumberConverter;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;

public abstract class AERenderItem extends RenderItem {

    private static final ISlimReadableNumberConverter SLIM_CONVERTER = ReadableNumberConverter.INSTANCE;
    private static final IWideReadableNumberConverter WIDE_CONVERTER = ReadableNumberConverter.INSTANCE;

    public void drawStackSize(int offsetX, int offsetY, long stackSize, FontRenderer font, Enum fontSize) {
        drawStackSize(offsetX, offsetY, getToBeRenderedStackSize(stackSize, fontSize), font, fontSize);
    }

    public void drawStackSize(int offsetX, int offsetY, String customText, FontRenderer font, Enum fontSize) {
        float scale = 1.0f;
        float shiftX = 0f;
        float shiftY = 0f;

        if (fontSize == TerminalFontSize.LARGE && customText.length() > 3) {
            fontSize = TerminalFontSize.DYNAMIC;
        }

        if (fontSize == TerminalFontSize.SMALL) {
            scale = 0.5f;
            shiftX = 2;
            shiftY = 1;
        } else if (fontSize == TerminalFontSize.LARGE) {
            scale = 0.85f;
        } else if (fontSize == TerminalFontSize.DYNAMIC) {
            if (customText.length() == 3) {
                scale = 0.786f;
                shiftX = 0.5f;
            } else if (customText.length() == 4) {
                scale = 0.644f;
                shiftX = 1f;
                shiftY = 0.5f;
            } else if (customText.length() > 4) {
                scale = 0.5f;
                shiftX = 2;
                shiftY = 1;
            } else {
                scale = 0.85f;
            }
        }

        if (scale == 1.0f) {
            font.drawStringWithShadow(
                    customText,
                    offsetX + 16 + 1 - font.getStringWidth(customText),
                    offsetY + 16 - 7,
                    16777215);
        } else {
            final float inverseScaleFactor = 1.0f / scale;
            GL11.glScaled(scale, scale, scale);

            final int X = (int) (((float) offsetX - shiftX + 16.0f + 1.0f - font.getStringWidth(customText) * scale)
                    * inverseScaleFactor);
            final int Y = (int) (((float) offsetY - shiftY + 16.0f - 7.0f * scale) * inverseScaleFactor);

            font.drawStringWithShadow(customText, X, Y, 16777215);

            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
        }
    }

    protected String getToBeRenderedStackSize(final long originalSize, Enum fontSize) {
        if (fontSize == TerminalFontSize.LARGE) {
            return SLIM_CONVERTER.toSlimReadableForm(originalSize);
        } else {
            return WIDE_CONVERTER.toWideReadableForm(originalSize);
        }
    }
}
