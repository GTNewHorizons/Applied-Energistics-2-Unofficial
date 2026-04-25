package appeng.client.render.highlighter;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import appeng.api.util.DimensionalCoord;
import appeng.api.util.ItemSearchDTO;
import appeng.api.util.WorldCoord;
import appeng.util.Platform;

public class StoragePosHighlighter extends BlockPosHighlighter {

    static final StoragePosHighlighter INSTANCE = new StoragePosHighlighter();

    private final List<ItemSearchDTO> highlightedCells = new ArrayList<>();

    StoragePosHighlighter() {}

    public static void highlightStorage(EntityPlayer player, List<ItemSearchDTO> interfaces, String foundMsg,
            String wrongDimMsg) {
        INSTANCE.clear();
        int highlightDuration = INSTANCE.MIN_TIME;
        for (ItemSearchDTO storage : interfaces) {

            if (storage.cellSlot >= 0) {
                INSTANCE.highlightedCells.add(storage);
            }
            INSTANCE.highlightedBlocks.add(storage.coord);

            highlightDuration = Math.max(
                    highlightDuration,
                    MathHelper.clamp_int(
                            500 * WorldCoord.getTaxicabDistance(storage.coord, player),
                            INSTANCE.MIN_TIME,
                            INSTANCE.MAX_TIME));

            if (player.worldObj.provider.dimensionId == storage.coord.getDimension()) {
                if (foundMsg == null) {
                    continue;
                }
                final String blockName = storage.getDisplayBlockName();
                final String itemName = storage.getDisplayItemName();
                player.addChatMessage(
                        new ChatComponentTranslation(
                                foundMsg,
                                blockName,
                                storage.itemCount,
                                itemName,
                                storage.coord.x,
                                storage.coord.y,
                                storage.coord.z));
            } else if (wrongDimMsg != null) {
                final String blockName = storage.getDisplayBlockName();
                final String itemName = storage.getDisplayItemName();
                player.addChatMessage(
                        new ChatComponentTranslation(
                                wrongDimMsg,
                                blockName,
                                storage.itemCount,
                                itemName,
                                storage.coord.getDimension()));
            }
        }
        INSTANCE.expireHighlightTime = System.currentTimeMillis() + highlightDuration;
    }

    public void clear() {
        super.clear();
        highlightedCells.clear();
    }

    @Override
    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        super.renderHighlightedBlocks(event);

        for (ItemSearchDTO storage : highlightedCells) {
            DimensionalCoord c = storage.coord;
            if (dimension != c.getDimension()) {
                continue;
            }
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glLineWidth(3);
            GL11.glTranslated(-doubleX, -doubleY, -doubleZ);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            renderHighlightedCellSlotOverlay(c.x, c.y, c.z, storage.cellSlot, storage.forward, storage.up);

            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private void renderHighlightedCellSlotOverlay(double x, double y, double z, int index, ForgeDirection forward,
            ForgeDirection up) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();

        // Transparent yellow
        tess.setColorRGBA_F(1.0f, 1.0f, 0.0f, 0.4f);

        int spin = 0;

        switch (forward.offsetX + forward.offsetY * 2 + forward.offsetZ * 3) {
            case 1 -> {
                switch (up) {
                    case UP -> spin = 3;
                    case DOWN -> spin = 1;
                    case NORTH -> spin = 0;
                    case SOUTH -> spin = 2;
                    default -> {}
                }
            }
            case -1 -> {
                switch (up) {
                    case UP -> spin = 1;
                    case DOWN -> spin = 3;
                    case NORTH -> spin = 0;
                    case SOUTH -> spin = 2;
                    default -> {}
                }
            }
            case -2 -> {
                switch (up) {
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    case NORTH -> spin = 2;
                    case SOUTH -> spin = 0;
                    default -> {}
                }
            }
            case 2 -> {
                switch (up) {
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    case NORTH -> spin = 0;
                    case SOUTH -> spin = 0;
                    default -> {}
                }
            }
            case 3 -> {
                switch (up) {
                    case UP -> spin = 2;
                    case DOWN -> spin = 0;
                    case EAST -> spin = 3;
                    case WEST -> spin = 1;
                    default -> {}
                }
            }
            case -3 -> {
                switch (up) {
                    case UP -> spin = 2;
                    case DOWN -> spin = 0;
                    case EAST -> spin = 1;
                    case WEST -> spin = 3;
                    default -> {}
                }
            }
        }

        // Calculate 2x5 grid position
        int origCol = 1 - (index % 2);
        int origRow = index / 2; // 0 or 1

        final ForgeDirection west = Platform.crossProduct(forward, up);
        RenderBlocks renderer = new RenderBlocks();
        selectFace(renderer, west, up, forward, 2 + origCol * 7, 7 + origCol * 7, 1 + origRow * 3, 3 + origRow * 3);

        double u1 = getInterpolatedU((spin % 4 < 2) ? 1 : 6);
        double u2 = getInterpolatedU(((spin + 1) % 4 < 2) ? 1 : 6);
        double u3 = getInterpolatedU(((spin + 2) % 4 < 2) ? 1 : 6);
        double u4 = getInterpolatedU(((spin + 3) % 4 < 2) ? 1 : 6);

        double v1 = getInterpolatedU(((spin + 1) % 4 < 2) ? 1 : 3);
        double v2 = getInterpolatedU(((spin + 2) % 4 < 2) ? 1 : 3);
        double v3 = getInterpolatedU(((spin + 3) % 4 < 2) ? 1 : 3);
        double v4 = getInterpolatedU(((spin) % 4 < 2) ? 1 : 3);

        switch (forward.offsetX + forward.offsetY * 2 + forward.offsetZ * 3) {
            case 1 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMinZ, u4, v4);
            }
            case -1 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case -2 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case 2 -> {
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMinZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMinZ, u4, v4);
            }
            case 3 -> {
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMinY, z + renderer.renderMaxZ, u4, v4);
            }
            case -3 -> {
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMinY, z + renderer.renderMaxZ, u1, v1);
                tess.addVertexWithUV(x + renderer.renderMinX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u2, v2);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMaxY, z + renderer.renderMaxZ, u3, v3);
                tess.addVertexWithUV(x + renderer.renderMaxX, y + renderer.renderMinY, z + renderer.renderMaxZ, u4, v4);
            }
        }
        tess.draw();
    }

    private void selectFace(final RenderBlocks renderer, final ForgeDirection west, final ForgeDirection up,
            final ForgeDirection forward, final int u1, final int u2, int v1, int v2) {
        v1 = 16 - v1;
        v2 = 16 - v2;

        final double minX = (forward.offsetX > 0 ? 1 : 0) + mapFaceUV(west.offsetX, u1) + mapFaceUV(up.offsetX, v1);
        final double minY = (forward.offsetY > 0 ? 1 : 0) + mapFaceUV(west.offsetY, u1) + mapFaceUV(up.offsetY, v1);
        final double minZ = (forward.offsetZ > 0 ? 1 : 0) + mapFaceUV(west.offsetZ, u1) + mapFaceUV(up.offsetZ, v1);

        final double maxX = (forward.offsetX > 0 ? 1 : 0) + mapFaceUV(west.offsetX, u2) + mapFaceUV(up.offsetX, v2);
        final double maxY = (forward.offsetY > 0 ? 1 : 0) + mapFaceUV(west.offsetY, u2) + mapFaceUV(up.offsetY, v2);
        final double maxZ = (forward.offsetZ > 0 ? 1 : 0) + mapFaceUV(west.offsetZ, u2) + mapFaceUV(up.offsetZ, v2);

        renderer.renderMinX = Math.max(0.0, Math.min(minX, maxX) - (forward.offsetX != 0 ? 0 : 0.001));
        renderer.renderMaxX = Math.min(1.0, Math.max(minX, maxX) + (forward.offsetX != 0 ? 0 : 0.001));

        renderer.renderMinY = Math.max(0.0, Math.min(minY, maxY) - (forward.offsetY != 0 ? 0 : 0.001));
        renderer.renderMaxY = Math.min(1.0, Math.max(minY, maxY) + (forward.offsetY != 0 ? 0 : 0.001));

        renderer.renderMinZ = Math.max(0.0, Math.min(minZ, maxZ) - (forward.offsetZ != 0 ? 0 : 0.001));
        renderer.renderMaxZ = Math.min(1.0, Math.max(minZ, maxZ) + (forward.offsetZ != 0 ? 0 : 0.001));
    }

    private double mapFaceUV(final int offset, final int uv) {
        if (offset == 0) {
            return 0;
        }

        if (offset > 0) {
            return uv / 16.0;
        }

        return (16.0 - uv) / 16.0;
    }

    private float getInterpolatedU(int texCoord) {
        return texCoord / 16f;
    }
}
