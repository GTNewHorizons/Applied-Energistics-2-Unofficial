package appeng.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import appeng.api.config.Settings;
import appeng.api.util.DimensionalCoord;
import appeng.core.AEConfig;
import appeng.core.localization.GuiColors;
import appeng.items.tools.ToolNetworkVisualiser;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import appeng.items.tools.ToolNetworkVisualiser.VNode;
import appeng.items.tools.ToolNetworkVisualiser.VNodeFlags;
import appeng.items.tools.ToolNetworkVisualiser.VisualisationModes;
import appeng.tile.networking.TileWirelessBase;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NetworkVisualiserRender {

    private static long expTime;
    private final double SIZE = 0.2d;
    private final int staticList = GL11.glGenLists(1);
    private static boolean needListRefresh = true;
    private static ArrayList<VNode> vNodeSet = new ArrayList<>();
    private static ArrayList<VLink> vLinkSet = new ArrayList<>();
    private static final Set<VLink> dense = new HashSet<>();
    private static final Set<VLink> normal = new HashSet<>();
    private static VisualisationModes mode = VisualisationModes.FULL;
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean renderWireless = false;
    private static final List<DimensionalCoord> wirelessConnections = new ArrayList<>();
    private static DimensionalCoord prevPos;

    final List<VisualisationModes> renderNodesModes = Arrays.asList(
            VisualisationModes.NODES,
            VisualisationModes.FULL,
            VisualisationModes.NONUM,
            VisualisationModes.PROXY);

    final List<VisualisationModes> renderLinksModes = Arrays.asList(
            VisualisationModes.CHANNELS,
            VisualisationModes.FULL,
            VisualisationModes.NONUM,
            VisualisationModes.P2P,
            VisualisationModes.PROXY);

    public static void networkVisualiser(ArrayList<VNode> vNodeSetNew, ArrayList<VLink> vLinkSetNew) {
        vNodeSet = vNodeSetNew;
        vLinkSet = vLinkSetNew;
        normal.clear();
        dense.clear();

        for (VLink link : vLinkSet) {
            if (link.flags.contains(VLinkFlags.DENSE)) {
                dense.add(link);
            } else {
                normal.add(link);
            }
        }
        needListRefresh = true;
    }

    public static void doWirelessRender(List<DimensionalCoord> dcl) {
        wirelessConnections.clear();
        wirelessConnections.addAll(dcl);
        renderWireless = true;
        expTime = System.currentTimeMillis() + 100;
    }

    @SubscribeEvent
    public void renderNetwork(RenderWorldLastEvent ev) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        double viewX = p.lastTickPosX + (p.posX - p.lastTickPosX) * ev.partialTicks;
        double viewY = p.lastTickPosY + (p.posY - p.lastTickPosY) * ev.partialTicks;
        double viewZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * ev.partialTicks;

        doRenderWirelessPath(viewX, viewY, viewZ);

        ItemStack is = mc.thePlayer.inventory.getCurrentItem();
        if (is == null || !(is.getItem() instanceof ToolNetworkVisualiser && is.hasTagCompound()
                && is.getTagCompound().hasKey("dim")))
            return;

        // Do not render if in a different dimension from the bound network
        VisualisationModes newMode = (VisualisationModes) ToolNetworkVisualiser.getConfigManager(is)
                .getSetting(Settings.NETWORK_VISUALISER);
        if (newMode != mode) {
            mode = newMode;
            needListRefresh = true;
        }
        int networkDim = is.getTagCompound().getInteger("dim");
        if (networkDim != mc.theWorld.provider.dimensionId) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslated(-viewX, -viewY, -viewZ);
        doRender(ev.partialTicks, viewX, viewY, viewZ);
        GL11.glPopMatrix();
    }

    public void doRenderWirelessPath(double viewX, double viewY, double viewZ) {
        if (!renderWireless) return;

        if (expTime < System.currentTimeMillis()) {
            wirelessConnections.clear();
            renderWireless = false;
            return;
        }

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        DimensionalCoord pos = new DimensionalCoord(mop.blockX, mop.blockY, mop.blockZ, 0);
        if (prevPos == null || !pos.isEqual(prevPos)) {
            prevPos = pos;
            expTime = 0;
            return;
        }

        TileEntity te = mc.theWorld.getTileEntity(pos.x, pos.y, pos.z);

        if (!(te instanceof TileWirelessBase) || wirelessConnections.isEmpty()) return;

        GL11.glPushMatrix();
        GL11.glTranslated(-viewX, -viewY, -viewZ);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(4.0f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA_F(0, 0, 1, 1);
        for (DimensionalCoord dc : wirelessConnections) {
            tess.addVertex(pos.x + 0.5d, pos.y + 0.5d, pos.z + 0.5d);
            tess.addVertex(dc.x + 0.5d, dc.y + 0.5d, dc.z + 0.5d);
        }
        tess.draw();

        GL11.glPopAttrib();
        GL11.glPopMatrix();

    }

    public void doRender(Float partialTicks, double viewX, double viewY, double viewZ) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (needListRefresh) {
            needListRefresh = false;
            GL11.glNewList(staticList, GL11.GL_COMPILE);

            if (renderNodesModes.contains(mode)) renderNodes();

            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

            if (renderLinksModes.contains(mode)) {
                renderLinks(dense, AEConfig.instance.visualiserWidthDense);
                renderLinks(normal, AEConfig.instance.visualiserWidthNormal);
            }

            GL11.glEndList();
        }

        GL11.glCallList(staticList);

        // Labels are rendered every frame because they need to face the camera

        if (mode == VisualisationModes.FULL) {
            for (VLink link : vLinkSet) {
                if (link.channels > 0) {
                    double linkX = (link.node1.x + link.node2.x) / 2d + 0.5d;
                    double linkY = (link.node1.y + link.node2.y) / 2d + 0.5d;
                    double linkZ = (link.node1.z + link.node2.z) / 2d + 0.5d;
                    double distSq = (viewX - linkX) * (viewX - linkX) + (viewY - linkY) * (viewY - linkY)
                            + (viewZ - linkZ) * (viewZ - linkZ);
                    if (distSq < 256d) { // 16 blocks
                        renderFloatingText(
                                String.valueOf(link.channels),
                                linkX,
                                linkY,
                                linkZ,
                                GuiColors.NetworkVisualiserFloatingText.getColor());
                    }
                }
            }
        }

        GL11.glPopAttrib();
    }

    private void renderNodes() {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUADS);

        for (VNode node : vNodeSet) {
            switch (mode) {
                case NODES, NONUM -> {
                    if (node.flags.contains(VNodeFlags.PROXY)) continue;
                }

                case CHANNELS, P2P -> {
                    continue;
                }

                case PROXY -> {
                    if (!node.flags.contains(VNodeFlags.PROXY)) continue;
                }

                default -> {}
            }

            final int color;
            if (node.flags.contains(VNodeFlags.MISSING)) color = GuiColors.NetworkVisualiserNodeMissing.getColor();
            else if (node.flags.contains(VNodeFlags.DENSE)) color = GuiColors.NetworkVisualiserNodeDense.getColor();
            else if (node.flags.contains(VNodeFlags.PROXY)) color = GuiColors.NetworkVisualiserNodeProxy.getColor();
            else color = GuiColors.NetworkVisualiserNodeDefault.getColor();

            final int alpha = (color >> 24) & 0xFF;
            final int red = (color >> 16) & 0xFF;
            final int green = (color >> 8) & 0xFF;
            final int blue = (color) & 0xFF;

            tess.setColorRGBA(red, green, blue, alpha); // +Y
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(red / 2, green / 2, blue / 2, alpha); // -Y
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(red * 8 / 10, green * 8 / 10, blue * 8 / 10, alpha); // +/- Z
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(red * 6 / 10, green * 6 / 10, blue * 6 / 10, alpha); // +/- X
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
        }

        tess.draw();
    }

    private void renderLinks(Set<VLink> links, float width) {
        GL11.glLineWidth(width);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);

        for (VLink link : links) {
            switch (mode) {
                case NODES -> {
                    continue;
                }

                case CHANNELS, NONUM -> {
                    if (link.flags.contains(VLinkFlags.PROXY)) continue;
                }

                case P2P -> {
                    if (!link.flags.contains(VLinkFlags.COMPRESSED)) continue;
                }

                case PROXY -> {
                    if (!link.flags.contains(VLinkFlags.PROXY)) continue;
                }

                default -> {}
            }

            final int color;
            if (link.flags.contains(VLinkFlags.COMPRESSED))
                color = GuiColors.NetworkVisualiserLinkCompressed.getColor();
            else if (link.flags.contains(VLinkFlags.DENSE)) color = GuiColors.NetworkVisualiserLinkDense.getColor();
            else if (link.flags.contains(VLinkFlags.PROXY)) color = GuiColors.NetworkVisualiserLinkProxy.getColor();
            else color = GuiColors.NetworkVisualiserLinkDefault.getColor();

            final int alpha = (color >> 24) & 0xFF;
            final int red = (color >> 16) & 0xFF;
            final int green = (color >> 8) & 0xFF;
            final int blue = (color) & 0xFF;

            tess.setColorRGBA(red, green, blue, alpha);

            tess.addVertex(link.node1.x + 0.5d, link.node1.y + 0.5d, link.node1.z + 0.5d);
            tess.addVertex(link.node2.x + 0.5d, link.node2.y + 0.5d, link.node2.z + 0.5d);

        }
        tess.draw();
    }

    public void renderFloatingText(String text, Double x, Double y, Double z, int color) {
        RenderManager renderManager = RenderManager.instance;
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        Tessellator tessellator = Tessellator.instance;

        float scale = 0.027f;
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

        int yOffset = -4;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tessellator.startDrawingQuads();
        int textWidth = fontRenderer.getStringWidth(text);
        int stringMiddle = textWidth / 2;
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
