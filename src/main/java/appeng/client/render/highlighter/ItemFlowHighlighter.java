package appeng.client.render.highlighter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.api.util.FlowSearchDTO;
import appeng.api.util.WorldCoord;
import appeng.util.FlowRateFormatter;

public class ItemFlowHighlighter extends BlockPosHighlighter {

    public static final ItemFlowHighlighter INSTANCE = new ItemFlowHighlighter();

    private final List<FlowSearchDTO> highlightedFlows = new ArrayList<>();

    ItemFlowHighlighter() {}

    public static void highlightFlow(EntityPlayer player, List<FlowSearchDTO> flows, ItemStack itemStack,
            String foundMsg, String notFoundMsg, String wrongDimMsg) {
        INSTANCE.clear();

        final IChatComponent itemName = itemStack != null ? itemStack.func_151000_E() : new ChatComponentText("");

        if (flows.isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation(notFoundMsg, itemName));
            return;
        }

        int highlightDuration = INSTANCE.MIN_TIME;
        for (FlowSearchDTO flow : flows) {
            INSTANCE.highlightedFlows.add(flow);
            INSTANCE.highlightedBlocks.add(flow.coord);

            highlightDuration = Math.max(
                    highlightDuration,
                    MathHelper.clamp_int(
                            500 * WorldCoord.getTaxicabDistance(flow.coord, player),
                            INSTANCE.MIN_TIME,
                            INSTANCE.MAX_TIME));

            if (player.worldObj.provider.dimensionId == flow.coord.getDimension()) {
                player.addChatMessage(
                        new ChatComponentTranslation(
                                foundMsg,
                                itemName,
                                FlowRateFormatter.formatTotalForChat(flow.netTotal),
                                flow.coord.x,
                                flow.coord.y,
                                flow.coord.z));
            } else {
                player.addChatMessage(
                        new ChatComponentTranslation(
                                wrongDimMsg,
                                itemName,
                                FlowRateFormatter.formatTotalForChat(flow.netTotal),
                                flow.coord.getDimension()));
            }
        }

        INSTANCE.expireHighlightTime = System.currentTimeMillis() + highlightDuration;
    }

    @Override
    public void clear() {
        super.clear();
        this.highlightedFlows.clear();
    }

    @Override
    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        updateCameraState(event);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

        for (final FlowSearchDTO flow : this.highlightedFlows) {
            final DimensionalCoord c = flow.coord;

            if (dimension != c.getDimension()) {
                continue;
            }

            final boolean entering = flow.netTotal > 0;

            beginOutline();

            if (entering) {
                renderHighLightedBlocksOutline(c.x, c.y, c.z, 0.333f, 1.0f, 0.333f, 1.0f);
            } else {
                renderHighLightedBlocksOutline(c.x, c.y, c.z, 1.0f, 0.333f, 0.333f, 1.0f);
            }

            endOutline();

            renderFloatingText(
                    FlowRateFormatter.formatTotal(flow.netTotal),
                    c.x + 0.5d - doubleX,
                    c.y + 1.3d - doubleY,
                    c.z + 0.5d - doubleZ,
                    entering ? 0x55FF55 : 0xFF5555);
        }

        GL11.glPopAttrib();
    }

    private void renderFloatingText(String text, double x, double y, double z, int color) {
        final RenderManager renderManager = RenderManager.instance;
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        final Tessellator tessellator = Tessellator.instance;

        final float scale = 0.027f;
        GL11.glColor4f(1f, 1f, 1f, 0.5f);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GL11.glRotatef(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final int yOffset = -4;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tessellator.startDrawingQuads();
        final int textWidth = fontRenderer.getStringWidth(text);
        final int stringMiddle = textWidth / 2;
        tessellator.setColorRGBA_F(0.0f, 0.0f, 0.0f, 0.5f);
        tessellator.addVertex(-stringMiddle - 1, -1 + yOffset, 0.0d);
        tessellator.addVertex(-stringMiddle - 1, 8 + yOffset, 0.0d);
        tessellator.addVertex(stringMiddle + 1, 8 + yOffset, 0.0d);
        tessellator.addVertex(stringMiddle + 1, -1 + yOffset, 0.0d);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 0.5f);
        fontRenderer.drawString(text, -textWidth / 2, yOffset, color);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        fontRenderer.drawString(text, -textWidth / 2, yOffset, color);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }
}
