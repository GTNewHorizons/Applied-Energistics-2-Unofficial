package appeng.integration.modules;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.client.render.FacadeRenderHelper;
import appeng.client.render.RenderBlocksWorkaround;
import appeng.client.texture.FacadeSubIcon;
import team.chisel.ctmlib.ICTMBlock;
import team.chisel.ctmlib.ISubmapManager;
import team.chisel.ctmlib.RenderBlocksCTM;
import team.chisel.ctmlib.TextureSubmap;

public final class ChiselFacadeRenderer {

    public static boolean renderFace(final FacadeRenderHelper helper, final RenderBlocksWorkaround renderer,
            final Block block, final IBlockAccess world, final int x, final int y, final int z, final int metadata,
            final ForgeDirection face) {
        if (!(block instanceof ICTMBlock<?>ctmBlock)) {
            return false;
        }
        final ISubmapManager manager = ctmBlock.getManager(world, x, y, z, metadata);
        if (manager == null) {
            return false;
        }
        final RenderBlocks context = manager.createRenderContext(renderer, block, world);
        // Custom subclasses select additional per-face state in their render methods; their cached submaps may be
        // stale.
        if (!(context instanceof RenderBlocksCTM ctmRenderer) || context.getClass() != RenderBlocksCTM.class) {
            return false;
        }
        manager.preRenderSide(context, world, x, y, z, face);
        try {
            if (ctmRenderer.submap == null || ctmRenderer.submapSmall == null) {
                return false;
            }
            final int[] textures = ctmRenderer.ctm.getSubmapIndices(world, x, y, z, face.ordinal());
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                final int index = textures[quadrant];
                final TextureSubmap map = index < 16 ? ctmRenderer.submap : ctmRenderer.submapSmall;
                final int subIndex = index < 16 ? index : index - 16;
                final IIcon icon = map.getSubIcon(subIndex % map.getWidth(), subIndex / map.getHeight());
                final double minU = quadrant == 1 || quadrant == 2 ? 0.5 : 0;
                final double minV = quadrant < 2 ? 0.5 : 0;
                renderQuadrant(helper, renderer, face, new FacadeSubIcon(icon, face, minU, minV), minU, minV);
            }
            return true;
        } finally {
            manager.postRenderSide(context, world, x, y, z, face);
        }
    }

    private static void renderQuadrant(final FacadeRenderHelper helper, final RenderBlocksWorkaround renderer,
            final ForgeDirection face, final IIcon icon, final double minU, final double minV) {
        final double maxU = minU + 0.5;
        final double maxV = minV + 0.5;
        switch (face) {
            case DOWN -> helper.renderSlice(renderer, face, icon, minU, 0, 1 - maxV, maxU, 1, 1 - minV);
            case UP -> helper.renderSlice(renderer, face, icon, minU, 0, minV, maxU, 1, maxV);
            case NORTH -> helper.renderSlice(renderer, face, icon, 1 - maxU, 1 - maxV, 0, 1 - minU, 1 - minV, 1);
            case SOUTH -> helper.renderSlice(renderer, face, icon, minU, 1 - maxV, 0, maxU, 1 - minV, 1);
            case WEST -> helper.renderSlice(renderer, face, icon, 0, 1 - maxV, minU, 1, 1 - minV, maxU);
            case EAST -> helper.renderSlice(renderer, face, icon, 0, 1 - maxV, 1 - maxU, 1, 1 - minV, 1 - minU);
            default -> {}
        }
    }
}
