package appeng.client.render.previewBlocks;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import appeng.api.implementations.parts.IPartCable;
import appeng.api.parts.BusSupport;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.core.AEConfig;
import appeng.parts.networking.PartCable;

public abstract class AbstractRendererPreview {

    private final int lineWidth = AEConfig.instance.previewLineWidth;

    protected void renderWireframeCube(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        Tessellator tessellator = Tessellator.instance;

        GL11.glLineWidth(lineWidth);
        tessellator.startDrawing(GL11.GL_LINES);

        tessellator.addVertex(minX, minY, minZ);
        tessellator.addVertex(maxX, minY, minZ);

        tessellator.addVertex(maxX, minY, minZ);
        tessellator.addVertex(maxX, minY, maxZ);

        tessellator.addVertex(maxX, minY, maxZ);
        tessellator.addVertex(minX, minY, maxZ);

        tessellator.addVertex(minX, minY, maxZ);
        tessellator.addVertex(minX, minY, minZ);

        tessellator.addVertex(minX, maxY, minZ);
        tessellator.addVertex(maxX, maxY, minZ);

        tessellator.addVertex(maxX, maxY, minZ);
        tessellator.addVertex(maxX, maxY, maxZ);

        tessellator.addVertex(maxX, maxY, maxZ);
        tessellator.addVertex(minX, maxY, maxZ);

        tessellator.addVertex(minX, maxY, maxZ);
        tessellator.addVertex(minX, maxY, minZ);

        tessellator.addVertex(minX, minY, minZ);
        tessellator.addVertex(minX, maxY, minZ);

        tessellator.addVertex(maxX, minY, minZ);
        tessellator.addVertex(maxX, maxY, minZ);

        tessellator.addVertex(maxX, minY, maxZ);
        tessellator.addVertex(maxX, maxY, maxZ);

        tessellator.addVertex(minX, minY, maxZ);
        tessellator.addVertex(minX, maxY, maxZ);

        tessellator.draw();
    }

    protected void getValidColorGL11() {
        if (ViewHelper.getValidPosition()) {
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.6f);
        } else {
            GL11.glColor4f(1.0f, 0.0f, 0.0f, 0.6f);
        }
    }

    protected void setPreviewOffset(int x, int y, int z, ForgeDirection side) {
        ViewHelper.setPreviewX(x + side.offsetX);
        ViewHelper.setPreviewY(y + side.offsetY);
        ViewHelper.setPreviewZ(z + side.offsetZ);
    }

    protected boolean canPlaceBlockAt(World world, int x, int y, int z) {
        if (world.isAirBlock(x, y, z)) {
            return true;
        }
        Block block = world.getBlock(x, y, z);
        return block != null && block.isReplaceable(world, x, y, z);
    }

    protected boolean hasParts(IPartHost partHost) {
        if (partHost.getPart(ForgeDirection.UNKNOWN) != null) {
            return true;
        }

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if (partHost.getPart(dir) != null) {
                return true;
            }
        }

        return false;
    }

    protected void applySideRotation(double x, double y, double z, ForgeDirection side) {
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        switch (side) {
            case DOWN:
                GL11.glRotatef(90, 1, 0, 0);
                break;
            case UP:
                GL11.glRotatef(-90, 1, 0, 0);
                break;
            case NORTH:
                GL11.glRotatef(180, 0, 1, 0);
                break;
            case SOUTH:
                break;
            case WEST:
                GL11.glRotatef(-90, 0, 1, 0);
                break;
            case EAST:
                GL11.glRotatef(90, 0, 1, 0);
                break;
        }

        GL11.glTranslated(-0.5, -0.5, -0.5);
    }

    protected boolean shouldPlaceOnNeighborBlock() {
        TileEntity te = ViewHelper.getWorld()
                .getTileEntity(ViewHelper.getPreviewX(), ViewHelper.getPreviewY(), ViewHelper.getPreviewZ());

        if (!(te instanceof IPartHost partHost)) {
            return true;
        }

        IPart existingPart = partHost.getPart(ViewHelper.getPlacementSide());
        if (existingPart != null) {
            return true;
        }

        IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);

        if (centerPart instanceof PartCable cablePart) {
            BusSupport busSupport = cablePart.supportsBuses();
            return busSupport != BusSupport.CABLE && busSupport != BusSupport.DENSE_CABLE;
        }

        return !hasParts(partHost);
    }

    protected boolean canPlace(World world, ForgeDirection side, int x, int y, int z) {
        int neighborX = x + side.offsetX;
        int neighborY = y + side.offsetY;
        int neighborZ = z + side.offsetZ;

        TileEntity te = world.getTileEntity(x, y, z);
        TileEntity neighborTe = world.getTileEntity(neighborX, neighborY, neighborZ);
        boolean canPlaceOnNeighbor = canPlaceBlockAt(world, neighborX, neighborY, neighborZ);

        if (!shouldPlaceOnNeighborBlock() && checkTe(te, side, canPlaceOnNeighbor)) {
            return true;
        }

        return checkTe(neighborTe, side, canPlaceOnNeighbor);
    }

    protected boolean checkTe(TileEntity te, ForgeDirection side, boolean canPlaceOnNeighbor) {
        if (!(te instanceof IPartHost partHost)) {
            return canPlaceOnNeighbor;
        }

        if (partHost.getPart(side) != null && !shouldPlaceOnNeighborBlock()) {
            return false;
        }

        if (partHost.getPart(side.getOpposite()) != null) {
            return false;
        }

        IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);
        if (centerPart instanceof IPartCable cable) {
            return cable.supportsBuses() == BusSupport.CABLE;
        }

        return hasParts(partHost);
    }

    protected void renderBase(double minXBase, double minYBase, double minZBase, double maxXBase, double maxYBase,
            double maxZBase) {
        double minX = minXBase / 16.0;
        double minY = minYBase / 16.0;
        double minZ = minZBase / 16.0;
        double maxX = maxXBase / 16.0;
        double maxY = maxYBase / 16.0;
        double maxZ = maxZBase / 16.0;

        renderWireframeCube(minX, minY, minZ, maxX, maxY, maxZ);
    }

    protected void renderCommonPreview(Runnable renderSpecificContent) {
        EntityPlayer player = ViewHelper.getPlayer();
        if (player == null) return;

        setupGlState(player);

        boolean shouldPlaceOnNeighborBlock = shouldPlaceOnNeighborBlock();
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();
        ForgeDirection placementSide = ViewHelper.getPlacementSide();

        if (shouldPlaceOnNeighborBlock) {
            int partX = previewX + placementSide.offsetX;
            int partY = previewY + placementSide.offsetY;
            int partZ = previewZ + placementSide.offsetZ;
            applySideRotation(partX, partY, partZ, placementSide.getOpposite());
        } else {
            applySideRotation(previewX, previewY, previewZ, placementSide);
        }

        renderSpecificContent.run();

        clearGlState();
    }

    protected void setupGlState(EntityPlayer player) {
        double playerX = player.lastTickPosX
                + (player.posX - player.lastTickPosX) * ViewHelper.getCurrentPartialTicks();
        double playerY = player.lastTickPosY
                + (player.posY - player.lastTickPosY) * ViewHelper.getCurrentPartialTicks();
        double playerZ = player.lastTickPosZ
                + (player.posZ - player.lastTickPosZ) * ViewHelper.getCurrentPartialTicks();

        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);

        getValidColorGL11();
    }

    protected void clearGlState() {
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
