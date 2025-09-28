package appeng.client.render.highlighter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;

// taken from McJty's McJtyLib
public class BlockPosHighlighter implements IHighlighter {

    static final BlockPosHighlighter INSTANCE = new BlockPosHighlighter();

    protected final List<DimensionalCoord> highlightedBlocks = new ArrayList<>();
    protected long expireHighlightTime;
    protected final int MIN_TIME = 3000;
    protected final int MAX_TIME = MIN_TIME * 10;

    protected int dimension;
    protected double doubleX;
    protected double doubleY;
    protected double doubleZ;

    BlockPosHighlighter() {}

    public static void highlightBlocks(EntityPlayer player, List<DimensionalCoord> interfaces, String deviceName,
            String foundMsg, String wrongDimMsg) {
        INSTANCE.clear();
        int highlightDuration = INSTANCE.MIN_TIME;
        for (DimensionalCoord coord : interfaces) {

            INSTANCE.highlightedBlocks.add(coord);
            highlightDuration = Math.max(
                    highlightDuration,
                    MathHelper.clamp_int(
                            500 * WorldCoord.getTaxicabDistance(coord, player),
                            INSTANCE.MIN_TIME,
                            INSTANCE.MAX_TIME));

            if (player.worldObj.provider.dimensionId == coord.getDimension()) {
                if (foundMsg == null) continue;

                if (deviceName.isEmpty()) {
                    player.addChatMessage(new ChatComponentTranslation(foundMsg, coord.x, coord.y, coord.z));
                } else {
                    player.addChatMessage(
                            new ChatComponentTranslation(foundMsg, deviceName, coord.x, coord.y, coord.z));
                }
            } else if (wrongDimMsg != null) {
                if (deviceName.isEmpty()) {
                    player.addChatMessage(new ChatComponentTranslation(wrongDimMsg, coord.getDimension()));
                } else {
                    player.addChatMessage(new ChatComponentTranslation(wrongDimMsg, deviceName, coord.getDimension()));
                }
            }
        }
        INSTANCE.expireHighlightTime = System.currentTimeMillis() + highlightDuration;
    }

    public static void highlightBlocks(EntityPlayer player, List<DimensionalCoord> interfaces, String foundMsg,
            String wrongDimMsg) {
        highlightBlocks(player, interfaces, "", foundMsg, wrongDimMsg);
    }

    public void clear() {
        highlightedBlocks.clear();
        expireHighlightTime = -1;
    }

    @Override
    public boolean noWork() {
        return highlightedBlocks.isEmpty();
    }

    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        dimension = mc.theWorld.provider.dimensionId;

        EntityPlayerSP p = mc.thePlayer;
        doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;

        for (DimensionalCoord c : highlightedBlocks) {
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

    void renderHighLightedBlocksOutline(double x, double y, double z) {
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

    @Override
    public long getExpireTime() {
        return expireHighlightTime;
    }
}
