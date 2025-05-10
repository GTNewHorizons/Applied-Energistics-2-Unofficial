/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

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
import appeng.items.tools.ToolNetworkVisualiser;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import appeng.items.tools.ToolNetworkVisualiser.VNode;
import appeng.items.tools.ToolNetworkVisualiser.VNodeFlags;
import appeng.items.tools.ToolNetworkVisualiser.VisualisationModes;
import appeng.tile.networking.TileWirelessConnector;
import appeng.tile.networking.TileWirelessHub;
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
    private static DimensionalCoord otherDc;
    private static final List<DimensionalCoord> hubConnections = new ArrayList<>();
    private static DimensionalCoord prevPos;

    ArrayList<VisualisationModes> renderNodesModes = new ArrayList<>(
            Arrays.asList(VisualisationModes.NODES, VisualisationModes.FULL, VisualisationModes.NONUM));

    ArrayList<VisualisationModes> renderLinksModes = new ArrayList<>(
            Arrays.asList(
                    VisualisationModes.CHANNELS,
                    VisualisationModes.FULL,
                    VisualisationModes.NONUM,
                    VisualisationModes.P2P));

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

    public static void doWirelessRender(DimensionalCoord dc) {
        otherDc = dc;
        renderWireless = true;
        expTime = System.currentTimeMillis() + 100;
    }

    public static void doWirelessHubRender(List<DimensionalCoord> dcl) {
        hubConnections.clear();
        hubConnections.addAll(dcl);
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

        GL11.glPushMatrix();
        GL11.glTranslated(-viewX, -viewY, -viewZ);
        doRenderWirelessPath();
        GL11.glPopMatrix();

        ItemStack is = mc.thePlayer.inventory.getCurrentItem();
        if (!(is != null && is.getItem() instanceof ToolNetworkVisualiser && is.hasTagCompound())
                || !is.getTagCompound().hasKey("dim"))
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

    public void doRenderWirelessPath() {
        if (renderWireless) {
            if (expTime < System.currentTimeMillis()) {
                otherDc = null;
                hubConnections.clear();
                renderWireless = false;
            }

            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                DimensionalCoord pos = new DimensionalCoord(mop.blockX, mop.blockY, mop.blockZ, 0);
                TileEntity te = mc.theWorld.getTileEntity(pos.x, pos.y, pos.z);
                if (prevPos == null || !pos.isEqual(prevPos)) {
                    prevPos = pos;
                    expTime = 0;
                    return;
                }
                if (te instanceof TileWirelessHub && !hubConnections.isEmpty()) {
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
                    for (DimensionalCoord dc : hubConnections) {
                        tess.addVertex(pos.x + 0.5d, pos.y + 0.5d, pos.z + 0.5d);
                        tess.addVertex(dc.x + 0.5d, dc.y + 0.5d, dc.z + 0.5d);
                    }
                    tess.draw();

                    GL11.glPopAttrib();
                } else if (te instanceof TileWirelessConnector && otherDc != null) {
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
                    tess.addVertex(pos.x + 0.5d, pos.y + 0.5d, pos.z + 0.5d);
                    tess.addVertex(otherDc.x + 0.5d, otherDc.y + 0.5d, otherDc.z + 0.5d);
                    tess.draw();

                    GL11.glPopAttrib();
                }
            }
        }
    }

    public void doRender(Float partialTicks, double viewX, double viewY, double viewZ) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        if (needListRefresh) {
            needListRefresh = false;
            GL11.glNewList(staticList, GL11.GL_COMPILE);

            if (renderNodesModes.contains(mode)) renderNodes();

            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

            if (renderLinksModes.contains(mode)) {
                renderLinks(dense, 16f, mode == VisualisationModes.P2P);
                renderLinks(normal, 4f, mode == VisualisationModes.P2P);
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
                        renderFloatingText(String.valueOf(link.channels), linkX, linkY, linkZ, 0xffffff);
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
            int[] color = node.flags.contains(VNodeFlags.MISSING) ? new int[] { 255, 0, 0 }
                    : node.flags.contains(VNodeFlags.DENSE) ? new int[] { 255, 255, 0 } : new int[] { 0, 0, 255 };

            tess.setColorRGBA(color[0], color[1], color[2], 255); // +Y
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(color[0] / 2, color[1] / 2, color[2] / 2, 255); // -Y
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(color[0] * 8 / 10, color[1] * 8 / 10, color[2] * 8 / 10, 255); // +/- Z
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d + SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d + SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d + SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);
            tess.addVertex(node.x + 0.5d - SIZE, node.y + 0.5d - SIZE, node.z + 0.5d - SIZE);

            tess.setColorRGBA(color[0] * 6 / 10, color[1] * 6 / 10, color[2] * 6 / 10, 255); // +/- X
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

    private void renderLinks(Set<VLink> links, float width, boolean onlyP2P) {
        GL11.glLineWidth(width);
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);

        for (VLink link : links) {
            if (!onlyP2P || link.flags.contains(VLinkFlags.COMPRESSED)) {
                if (link.flags.contains(VLinkFlags.COMPRESSED)) {
                    tess.setColorRGBA(255, 0, 255, 255);
                } else if (link.flags.contains(VLinkFlags.DENSE)) {
                    tess.setColorRGBA(255, 255, 0, 255);
                } else {
                    tess.setColorRGBA(0, 0, 255, 255);
                }

                tess.addVertex(link.node1.x + 0.5d, link.node1.y + 0.5d, link.node1.z + 0.5d);
                tess.addVertex(link.node2.x + 0.5d, link.node2.y + 0.5d, link.node2.z + 0.5d);
            }
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
