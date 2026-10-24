package appeng.client.render.highlighter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.api.util.NamedDimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.util.Platform;

// taken from McJty's McJtyLib
public class BlockPosHighlighter implements IHighlighter {

    static final BlockPosHighlighter INSTANCE = new BlockPosHighlighter();

    private static final double LOOK_DISTANCE_SQUARED_EPSILON = 1.0e-6;

    protected final List<DimensionalCoord> highlightedBlocks = new ArrayList<>();
    protected long expireHighlightTime;
    protected final int MIN_TIME = 3000;
    protected final int MAX_TIME = MIN_TIME * 10;

    protected int dimension;
    protected double doubleX;
    protected double doubleY;
    protected double doubleZ;

    BlockPosHighlighter() {}

    public static void highlightNamedBlocks(EntityPlayer player, Map<NamedDimensionalCoord, String[]> ndcm,
            String deviceName) {
        INSTANCE.clear();
        int highlightDuration = INSTANCE.MIN_TIME;
        for (NamedDimensionalCoord coord : ndcm.keySet()) {
            final String[] msgs = ndcm.get(coord);
            final String foundMsg = msgs[0];
            final String wrongDimMsg = msgs[1];
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
                } else if (coord.getCustomName().isEmpty()) {
                    player.addChatMessage(
                            new ChatComponentTranslation(foundMsg, deviceName, coord.x, coord.y, coord.z));
                } else {
                    player.addChatMessage(
                            new ChatComponentTranslation(
                                    foundMsg,
                                    deviceName,
                                    coord.getCustomName(),
                                    coord.x,
                                    coord.y,
                                    coord.z));
                }
            } else if (wrongDimMsg != null) {
                if (deviceName.isEmpty()) {
                    player.addChatMessage(new ChatComponentTranslation(wrongDimMsg, coord.getDimension()));
                } else if (coord.getCustomName().isEmpty()) {
                    player.addChatMessage(new ChatComponentTranslation(wrongDimMsg, deviceName, coord.getDimension()));
                } else {
                    player.addChatMessage(
                            new ChatComponentTranslation(
                                    wrongDimMsg,
                                    deviceName,
                                    coord.getCustomName(),
                                    coord.getDimension()));
                }
            }
        }
        INSTANCE.expireHighlightTime = System.currentTimeMillis() + highlightDuration;
    }

    public static void highlightBlocks(EntityPlayer player, List<DimensionalCoord> interfaces, String deviceName,
            String foundMsg, String wrongDimMsg) {
        List<NamedDimensionalCoord> noNamedCoords = new ArrayList<>();
        for (DimensionalCoord coord : interfaces) {
            noNamedCoords.add(new NamedDimensionalCoord(coord, ""));
        }
        Map<NamedDimensionalCoord, String[]> ndcm = new HashMap<>();
        for (NamedDimensionalCoord noNamedCoord : noNamedCoords) {
            ndcm.put(noNamedCoord, new String[] { foundMsg, wrongDimMsg });
        }
        highlightNamedBlocks(player, ndcm, deviceName);
    }

    public static void highlightBlocks(EntityPlayer player, List<DimensionalCoord> interfaces, String foundMsg,
            String wrongDimMsg) {
        highlightBlocks(player, interfaces, "", foundMsg, wrongDimMsg);
    }

    /** Aims at the current highlight group when its blocks share a useful direction in the player's dimension. */
    public static void lookAtHighlightedBlocks(final EntityPlayer player) {
        final int dimension = player.worldObj.provider.dimensionId;
        final double eyeY = Platform.getEyeOffset(player);
        double centerX = 0;
        double centerY = 0;
        double centerZ = 0;
        int count = 0;

        for (final DimensionalCoord block : INSTANCE.highlightedBlocks) {
            if (block.getDimension() != dimension) {
                continue;
            }

            centerX += block.x + 0.5;
            centerY += block.y + 0.5;
            centerZ += block.z + 0.5;
            count++;
        }

        if (count == 0) {
            return;
        }

        final double dx = centerX / count - player.posX;
        final double dy = centerY / count - eyeY;
        final double dz = centerZ / count - player.posZ;
        final double horizontalDistanceSquared = dx * dx + dz * dz;

        // Keep the camera direction when opposing blocks have no useful common direction.
        for (final DimensionalCoord block : INSTANCE.highlightedBlocks) {
            if (block.getDimension() != dimension) {
                continue;
            }
            final double blockDx = block.x + 0.5 - player.posX;
            final double blockDz = block.z + 0.5 - player.posZ;
            if ((horizontalDistanceSquared < LOOK_DISTANCE_SQUARED_EPSILON
                    && blockDx * blockDx + blockDz * blockDz >= LOOK_DISTANCE_SQUARED_EPSILON)
                    || blockDx * dx + blockDz * dz < 0) {
                return;
            }
        }

        if (horizontalDistanceSquared + dy * dy < LOOK_DISTANCE_SQUARED_EPSILON) {
            return;
        }

        // Keep the existing yaw when the target is directly above or below the player.
        if (horizontalDistanceSquared >= LOOK_DISTANCE_SQUARED_EPSILON) {
            player.rotationYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            player.prevRotationYaw = player.rotationYaw;
            player.rotationYawHead = player.rotationYaw;
            player.prevRotationYawHead = player.rotationYaw;
        }
        player.rotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(horizontalDistanceSquared)));
        player.prevRotationPitch = player.rotationPitch;
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
        updateCameraState(event);

        for (DimensionalCoord c : highlightedBlocks) {
            if (dimension != c.getDimension()) {
                continue;
            }

            beginOutline();
            renderHighLightedBlocksOutline(c.x, c.y, c.z);
            endOutline();
        }
    }

    protected void updateCameraState(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        dimension = mc.theWorld.provider.dimensionId;

        EntityLivingBase p = mc.renderViewEntity;
        doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;
    }

    protected void beginOutline() {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glLineWidth(3);
        GL11.glTranslated(-doubleX, -doubleY, -doubleZ);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    protected void endOutline() {
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    protected void renderHighLightedBlocksOutline(double x, double y, double z) {
        renderHighLightedBlocksOutline(x, y, z, 1.0f, 0.0f, 0.0f, 1.0f);
    }

    protected void renderHighLightedBlocksOutline(double x, double y, double z, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINE_STRIP);

        tess.setColorRGBA_F(r, g, b, a);

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
