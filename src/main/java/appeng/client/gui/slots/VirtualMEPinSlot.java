package appeng.client.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IDisplayRepo;
import appeng.core.AppEng;

public class VirtualMEPinSlot extends VirtualMEMonitorableSlot {

    private static final int PIN_ICON_INDEX = 5 * 16 + 14;
    private static final int UV_Y = (int) Math.floor((double) PIN_ICON_INDEX / 16);
    private static final int UV_X = PIN_ICON_INDEX - UV_Y * 16;
    private static final int ICON_SIZE = 16;
    private static final float PIN_ICON_OPACITY = 0.4f;
    private static final ResourceLocation TEXTURE = new ResourceLocation(AppEng.MOD_ID, "textures/guis/states.png");

    public VirtualMEPinSlot(int x, int y, IDisplayRepo repo, int slotIndex) {
        super(x, y, repo, slotIndex);
    }

    @Override
    public @Nullable IAEStack<?> getAEStack() {
        return this.repo.getAEPin(this.slotIndex);
    }

    public static void drawSlotsBackground(VirtualMEPinSlot[] slots, Minecraft mc, float z) {
        mc.getTextureManager().bindTexture(TEXTURE);
        final Tessellator tessellator = Tessellator.instance;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, PIN_ICON_OPACITY);
        for (VirtualMEPinSlot slot : slots) {
            final float x = slot.getX();
            final float y = slot.getY();
            final float uvX = UV_X * 16;
            final float uvY = UV_Y * 16;

            final float f1 = 0.00390625F;
            final float f = 0.00390625F;
            tessellator.addVertexWithUV(x + 0, y + ICON_SIZE, z, (uvX + 0) * f, (uvY + ICON_SIZE) * f1);
            tessellator.addVertexWithUV(x + ICON_SIZE, y + ICON_SIZE, z, (uvX + ICON_SIZE) * f, (uvY + ICON_SIZE) * f1);
            tessellator.addVertexWithUV(x + ICON_SIZE, y, z, (uvX + ICON_SIZE) * f, (uvY + 0) * f1);
            tessellator.addVertexWithUV(x + 0, y, z, (uvX + 0) * f, (uvY + 0) * f1);
        }
        tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, 1.0f);
        tessellator.draw();
        GL11.glPopAttrib();
    }
}
