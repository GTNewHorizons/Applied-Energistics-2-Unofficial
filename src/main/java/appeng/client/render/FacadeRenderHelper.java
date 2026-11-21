package appeng.client.render;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.facade.FacadeBlockAccess;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.modules.ChiselFacadeRenderer;

/** Keeps the imitated block's texture context separate from the cable bus used for facade lighting and geometry. */
public final class FacadeRenderHelper {

    private final Block block;
    private final IBlockAccess world;
    private final int x;
    private final int y;
    private final int z;
    private final int metadata;
    private boolean rendering;

    public FacadeRenderHelper(final Block block, final int metadata, final IBlockAccess world, final int x, final int y,
            final int z, final ForgeDirection side) {
        this.block = block;
        this.metadata = metadata;
        this.world = new FacadeBlockAccess(world, side.ordinal(), block, metadata, x, y, z);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getColor() {
        return this.block.colorMultiplier(this.world, this.x, this.y, this.z);
    }

    public boolean renderFace(final RenderBlocksWorkaround renderer, final ForgeDirection face) {
        if (this.rendering || renderer.hasOverrideBlockTexture()) {
            return false;
        }
        this.rendering = true;
        try {
            IIcon icon;
            try {
                icon = renderer.getBlockIcon(this.block, this.world, this.x, this.y, this.z, face.ordinal());
            } catch (final Exception ignored) {
                // Some explicitly whitelisted facades require a tile entity that a facade cannot provide.
                icon = this.block.getIcon(face.ordinal(), this.metadata);
            }
            if (IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.Chisel) && ChiselFacadeRenderer
                    .renderFace(this, renderer, this.block, this.world, this.x, this.y, this.z, this.metadata, face)) {
                return true;
            }
            this.renderFace(renderer, face, icon);
            return true;
        } finally {
            this.rendering = false;
        }
    }

    private void renderFace(final RenderBlocksWorkaround renderer, final ForgeDirection face, final IIcon icon) {
        switch (face) {
            case DOWN -> renderer.renderFaceYNeg(this.block, this.x, this.y, this.z, icon);
            case UP -> renderer.renderFaceYPos(this.block, this.x, this.y, this.z, icon);
            case NORTH -> renderer.renderFaceZNeg(this.block, this.x, this.y, this.z, icon);
            case SOUTH -> renderer.renderFaceZPos(this.block, this.x, this.y, this.z, icon);
            case WEST -> renderer.renderFaceXNeg(this.block, this.x, this.y, this.z, icon);
            case EAST -> renderer.renderFaceXPos(this.block, this.x, this.y, this.z, icon);
            default -> {}
        }
    }

    /** Intersects a texture slice with the existing facade bounds, keeping holes and corner trims in block space. */
    public void renderSlice(final RenderBlocksWorkaround renderer, final ForgeDirection face, final IIcon icon,
            final double minX, final double minY, final double minZ, final double maxX, final double maxY,
            final double maxZ) {
        final double oldMinX = renderer.renderMinX;
        final double oldMinY = renderer.renderMinY;
        final double oldMinZ = renderer.renderMinZ;
        final double oldMaxX = renderer.renderMaxX;
        final double oldMaxY = renderer.renderMaxY;
        final double oldMaxZ = renderer.renderMaxZ;
        try {
            // Only clip along the face. The normal coordinate belongs to the thin facade, not the full block.
            if (face.offsetX == 0) {
                renderer.renderMinX = Math.max(oldMinX, minX);
                renderer.renderMaxX = Math.min(oldMaxX, maxX);
            }
            if (face.offsetY == 0) {
                renderer.renderMinY = Math.max(oldMinY, minY);
                renderer.renderMaxY = Math.min(oldMaxY, maxY);
            }
            if (face.offsetZ == 0) {
                renderer.renderMinZ = Math.max(oldMinZ, minZ);
                renderer.renderMaxZ = Math.min(oldMaxZ, maxZ);
            }
            if (renderer.renderMinX < renderer.renderMaxX && renderer.renderMinY < renderer.renderMaxY
                    && renderer.renderMinZ < renderer.renderMaxZ) {
                this.renderFace(renderer, face, icon);
            }
        } finally {
            renderer.renderMinX = oldMinX;
            renderer.renderMinY = oldMinY;
            renderer.renderMinZ = oldMinZ;
            renderer.renderMaxX = oldMaxX;
            renderer.renderMaxY = oldMaxY;
            renderer.renderMaxZ = oldMaxZ;
        }
    }
}
