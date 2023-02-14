package appeng.helpers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.client.render.BlockPosHighlighter;

// inspired by McJtyLib

public class HighlighterHandler {

    public static void tick(RenderWorldLastEvent event) {
        renderHilightedBlock(event);
    }

    private static void renderHilightedBlock(RenderWorldLastEvent event) {
        DimensionalCoord c = BlockPosHighlighter.getHighlightedBlock();
        if (c == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc.theWorld.provider.dimensionId;
        long time = System.currentTimeMillis();

        if (time > BlockPosHighlighter.getExpireHighlight()
                || dimension != BlockPosHighlighter.getHighlightedBlock().getDimension()) {
            BlockPosHighlighter.highlightBlock(null, -1);
            return;
        }

        if (((time / 500) & 1) == 0) {
            return;
        }

        EntityPlayerSP p = mc.thePlayer;
        double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glLineWidth(3);
        GL11.glTranslated(-doubleX, -doubleY, -doubleZ);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glColor4f(1.0f, 0.0f, 0.0f, 1.0f);
        renderHighLightedBlocksOutline(c.x, c.y, c.z);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    static void renderHighLightedBlocksOutline(double x, double y, double z) {
        GL11.glBegin(GL11.GL_LINE_STRIP);

        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y + 1, z);
        GL11.glVertex3d(x, y + 1, z + 1);
        GL11.glVertex3d(x, y, z + 1);
        GL11.glVertex3d(x, y, z);

        GL11.glVertex3d(x + 1, y, z);
        GL11.glVertex3d(x + 1, y + 1, z);
        GL11.glVertex3d(x + 1, y + 1, z + 1);
        GL11.glVertex3d(x + 1, y, z + 1);
        GL11.glVertex3d(x + 1, y, z);

        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + 1, y, z);
        GL11.glVertex3d(x + 1, y, z + 1);
        GL11.glVertex3d(x, y, z + 1);
        GL11.glVertex3d(x, y + 1, z + 1);
        GL11.glVertex3d(x + 1, y + 1, z + 1);
        GL11.glVertex3d(x + 1, y + 1, z);
        GL11.glVertex3d(x + 1, y, z);
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + 1, y, z);
        GL11.glVertex3d(x + 1, y + 1, z);
        GL11.glVertex3d(x, y + 1, z);
        GL11.glVertex3d(x, y + 1, z + 1);
        GL11.glVertex3d(x + 1, y + 1, z + 1);
        GL11.glVertex3d(x + 1, y, z + 1);
        GL11.glVertex3d(x, y, z + 1);

        GL11.glEnd();
    }
}
