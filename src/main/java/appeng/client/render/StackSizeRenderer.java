package appeng.client.render;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import appeng.api.config.TerminalFontSize;
import appeng.util.ReadableNumberConverter;

public class StackSizeRenderer {

    public static void drawStackSize(int offsetX, int offsetY, long stackSize, FontRenderer font,
            TerminalFontSize fontSize) {
        drawStackSize(offsetX, offsetY, getToBeRenderedStackSize(stackSize, fontSize), font, fontSize);
    }

    public static void drawStackSize(int offsetX, int offsetY, String customText, FontRenderer font,
            TerminalFontSize fontSize) {
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

    public static String getToBeRenderedStackSize(final long originalSize, TerminalFontSize fontSize) {
        if (fontSize == TerminalFontSize.LARGE) {
            return ReadableNumberConverter.INSTANCE.toSlimReadableForm(originalSize);
        } else {
            return ReadableNumberConverter.INSTANCE.toWideReadableForm(originalSize);
        }
    }
}
