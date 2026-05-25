package appeng.client.gui;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import appeng.core.AEConfig;

public final class ScreenColor {

    private ScreenColor() {}

    public static int getColor() {
        return AEConfig.instance.getScreenColor();
    }

    public static float getRed() {
        return ((getColor() >> 16) & 0xFF) / 255.0F;
    }

    public static float getGreen() {
        return ((getColor() >> 8) & 0xFF) / 255.0F;
    }

    public static float getBlue() {
        return (getColor() & 0xFF) / 255.0F;
    }

    public static void setGuiColor() {
        setGuiColor(1.0F);
    }

    public static void setGuiColor(final float alpha) {
        GL11.glColor4f(getRed(), getGreen(), getBlue(), alpha);
    }

    public static void setDimmedGuiColor() {
        GL11.glColor4f(getRed() * 0.5F, getGreen() * 0.5F, getBlue() * 0.5F, 1.0F);
    }

    public static void resetGuiColor() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void applyButtonColorHook(final float red, final float green, final float blue, final float alpha) {
        final var minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen instanceof AEBaseGui) {
            setGuiColor(alpha);
            return;
        }

        GL11.glColor4f(red, green, blue, alpha);
    }
}
