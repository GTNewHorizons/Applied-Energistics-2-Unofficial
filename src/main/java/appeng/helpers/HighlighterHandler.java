package appeng.helpers;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.client.render.BlockPosHighlighter;

// inspired by McJtyLib

public class HighlighterHandler {

    public static void tick(RenderWorldLastEvent event) {
        renderHighlightedBlocks(event);
    }

    private static void renderHighlightedBlocks(RenderWorldLastEvent event) {
        List<DimensionalCoord> list = BlockPosHighlighter.getHighlightedBlocks();
        if (list.isEmpty()) {
            return;
        }
        long time = System.currentTimeMillis();
        if (time > BlockPosHighlighter.getExpireHighlightTime()) {
            BlockPosHighlighter.clear();
            return;
        }
        if (((time / 500) & 1) == 0) {
            // this does the blinking effect
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc.theWorld.provider.dimensionId;

        EntityPlayerSP p = mc.thePlayer;
        double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;

        for (DimensionalCoord c : list) {
            if (dimension != c.getDimension()) {
                continue;
            }
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glLineWidth(3);
            GL11.glTranslated(-doubleX, -doubleY, -doubleZ);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            renderHighLightedBlocksOutline(c.x, c.y, c.z);

            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void renderHighLightedBlocksOutline(double x, double y, double z) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINE_STRIP);

        tess.setColorRGBA_F(1.0f, 0.0f, 0.0f, 1.0f);

        tess.addVertex(x, y, z);
        tess.addVertex(x, y + 1, z);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x, y, z + 1);
        tess.addVertex(x, y, z);

        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x + 1, y, z);

        tess.addVertex(x, y, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x, y, z + 1);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x, y, z);
        tess.addVertex(x + 1, y, z);
        tess.addVertex(x + 1, y + 1, z);
        tess.addVertex(x, y + 1, z);
        tess.addVertex(x, y + 1, z + 1);
        tess.addVertex(x + 1, y + 1, z + 1);
        tess.addVertex(x + 1, y, z + 1);
        tess.addVertex(x, y, z + 1);

        tess.draw();
    }
}
