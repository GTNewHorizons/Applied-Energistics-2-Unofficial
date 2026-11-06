package appeng.test.client.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadViewMutable;

import appeng.api.AEApi;
import appeng.client.render.FacadeRenderHelper;
import appeng.client.render.RenderBlocksWorkaround;
import appeng.facade.FacadeBlockAccess;
import appeng.facade.FacadePart;
import appeng.integration.modules.ChiselFacadeRenderer;
import appeng.tile.networking.TileCableBus;
import cpw.mods.fml.common.FMLCommonHandler;
import team.chisel.ctmlib.CTM;
import team.chisel.ctmlib.ICTMBlock;
import team.chisel.ctmlib.ISubmapManager;
import team.chisel.ctmlib.RenderBlocksCTM;
import team.chisel.ctmlib.SubmapManagerCTM;

public class FacadeRenderHelperFunctionalTest {

    @Test
    public void chiselConnectsRealBlocksAndFacadesInBothDirections() {
        final Block imitated = Blocks.stone;
        final Block cable = AEApi.instance().definitions().blocks().multiPart().maybeBlock().get();
        final Item item = new ItemBlock(imitated) {

            @Override
            public int getMetadata(final int damage) {
                return damage;
            }
        };
        final TileCableBus host = new TileCableBus();
        host.getFacadeContainer().addFacade(new FacadePart(new ItemStack(new Item()), ForgeDirection.NORTH) {

            @Override
            public Item getItem() {
                return item;
            }

            @Override
            public int getItemDamage() {
                return 2;
            }
        });
        final IBlockAccess actual = pairWorld(imitated, cable, host);
        final IBlockAccess expected = pairWorld(imitated, imitated, null);
        final CTM ctm = CTM.getInstance();
        final int face = ForgeDirection.NORTH.ordinal();

        assertArrayEquals(ctm.getSubmapIndices(expected, 0, 0, 0, face), ctm.getSubmapIndices(actual, 0, 0, 0, face));
        assertArrayEquals(
                ctm.getSubmapIndices(expected, 1, 0, 0, face),
                ctm.getSubmapIndices(FacadeBlockAccess.wrap(actual, face), 1, 0, 0, face));
        assertFalse(
                Arrays.equals(
                        ctm.getSubmapIndices(pairWorld(imitated, Blocks.air, null), 0, 0, 0, face),
                        ctm.getSubmapIndices(actual, 0, 0, 0, face)));
    }

    @Test
    public void emittedFacadeUvsMatchChiselsOwnRendererOnAllSixFaces() {
        final CtmBlock block = new CtmBlock();
        final SubmapManagerCTM manager = (SubmapManagerCTM) block.manager;
        manager.registerIcons("test", block, FacadeRenderHelperFunctionalTest::icon);
        manager.getSubmap().texturesStitched();
        manager.getSubmapSmall().texturesStitched();
        final IBlockAccess world = world(block);
        for (final ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            final ReferenceRenderer reference = new ReferenceRenderer(manager, world);
            final FacadeRenderHelper helper = new FacadeRenderHelper(block, 2, world, 0, 0, 0, face);
            final RenderBlocksWorkaround facade = new RenderBlocksWorkaround();
            facade.setFacade(true);
            facade.setRenderBounds(
                    face.offsetX > 0 ? 0.875 : 0,
                    face.offsetY > 0 ? 0.875 : 0,
                    face.offsetZ > 0 ? 0.875 : 0,
                    face.offsetX < 0 ? 0.125 : 1,
                    face.offsetY < 0 ? 0.125 : 1,
                    face.offsetZ < 0 ? 0.125 : 1);
            final List<String> original = capture(() -> renderReferenceFace(reference, block, face));
            final List<String> imitated = capture(
                    () -> assertTrue(ChiselFacadeRenderer.renderFace(helper, facade, block, world, 0, 0, 0, 2, face)));
            assertEquals(original, imitated, face.name());
        }
    }

    private static IBlockAccess pairWorld(final Block block, final Block neighbor, final TileCableBus host) {
        return new StubBlockAccess() {

            @Override
            public Block getBlock(final int x, final int y, final int z) {
                if (x == 0 && y == 0 && z == 0) {
                    return block;
                }
                return x == 1 && y == 0 && z == 0 ? neighbor : Blocks.air;
            }

            @Override
            public int getBlockMetadata(final int x, final int y, final int z) {
                final boolean origin = x == 0 && y == 0 && z == 0;
                final boolean adjacent = x == 1 && y == 0 && z == 0;
                return origin || adjacent && host == null ? 2 : 0;
            }

            @Override
            public TileEntity getTileEntity(final int x, final int y, final int z) {
                return x == 1 && y == 0 && z == 0 ? host : null;
            }
        };
    }

    private static List<String> capture(final Runnable render) {
        final CapturingTessellator tessellator = TessellatorManager.startCapturingAndGet();
        tessellator.startDrawingQuads();
        final List<String> vertices = new ArrayList<>();
        try {
            render.run();
        } finally {
            final List<ModelQuadViewMutable> quads = TessellatorManager.stopCapturingToPooledQuads();
            for (final ModelQuadViewMutable quad : quads) {
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertices.add(
                            Math.round(quad.getX(vertex) * 1_000_000) + ","
                                    + Math.round(quad.getY(vertex) * 1_000_000)
                                    + ","
                                    + Math.round(quad.getZ(vertex) * 1_000_000)
                                    + ","
                                    + Math.round(quad.getTexU(vertex) * 1_000_000)
                                    + ","
                                    + Math.round(quad.getTexV(vertex) * 1_000_000));
                }
            }
            tessellator.clearQuads();
        }
        Collections.sort(vertices);
        return vertices;
    }

    private static void renderReferenceFace(final RenderBlocksCTM renderer, final Block block,
            final ForgeDirection face) {
        final IIcon icon = block.getIcon(face.ordinal(), 2);
        switch (face) {
            case DOWN -> renderer.renderFaceYNeg(block, 0, 0, 0, icon);
            case UP -> renderer.renderFaceYPos(block, 0, 0, 0, icon);
            case NORTH -> renderer.renderFaceZNeg(block, 0, 0, 0, icon);
            case SOUTH -> renderer.renderFaceZPos(block, 0, 0, 0, icon);
            case WEST -> renderer.renderFaceXNeg(block, 0, 0, 0, icon);
            case EAST -> renderer.renderFaceXPos(block, 0, 0, 0, icon);
            default -> {}
        }
    }

    private static final class ReferenceRenderer extends RenderBlocksCTM {

        private ReferenceRenderer(final SubmapManagerCTM manager, final IBlockAccess world) {
            this.manager = manager;
            this.submap = manager.getSubmap();
            this.submapSmall = manager.getSubmapSmall();
            this.blockAccess = world;
            this.inWorld = true;
            this.meta = 2;
            this.setRenderBounds(0, 0, 0, 1, 1, 1);
        }
    }

    @BeforeAll
    public static void prepareLaunchBlackboard() {
        Assumptions.assumeTrue(FMLCommonHandler.instance().getSide().isClient());
        if (Launch.blackboard == null) {
            Launch.blackboard = new HashMap<>();
        }
    }

    @Test
    public void usesTheOriginalBlocksWorldTextureAndBiomeColor() {
        final IIcon worldIcon = icon("world");
        final IIcon itemIcon = icon("inventory");
        final Block block = new Block(Material.rock) {

            @Override
            public IIcon getIcon(final IBlockAccess world, final int x, final int y, final int z, final int side) {
                assertTrue(world instanceof FacadeBlockAccess);
                assertEquals(2, world.getBlockMetadata(x, y, z));
                return worldIcon;
            }

            @Override
            public IIcon getIcon(final int side, final int metadata) {
                return itemIcon;
            }

            @Override
            public int colorMultiplier(final IBlockAccess world, final int x, final int y, final int z) {
                return 0x123456;
            }
        };
        final IBlockAccess world = world(block);
        final FacadeRenderHelper helper = new FacadeRenderHelper(block, 2, world, 0, 0, 0, ForgeDirection.SOUTH);
        final RecordingRenderer renderer = new RecordingRenderer();
        renderer.setRenderBounds(0, 0, 0.875, 1, 1, 1);

        assertEquals(0x123456, helper.getColor());
        assertTrue(helper.renderFace(renderer, ForgeDirection.SOUTH));
        assertEquals(1, renderer.slices.size());
        assertSame(worldIcon, renderer.slices.get(0).icon);
    }

    @Test
    public void chiselQuadrantsKeepTheirBlockCoordinatesAcrossTrimmedFacadesOnEverySide() {
        final CtmBlock block = new CtmBlock();
        block.manager.registerIcons("test", block, FacadeRenderHelperFunctionalTest::icon);
        ((SubmapManagerCTM) block.manager).getSubmap().texturesStitched();
        ((SubmapManagerCTM) block.manager).getSubmapSmall().texturesStitched();
        final IBlockAccess world = world(block);
        for (final ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            final FacadeRenderHelper helper = new FacadeRenderHelper(block, 2, world, 0, 0, 0, face);
            final RecordingRenderer renderer = new RecordingRenderer();
            final double minX = face.offsetX != 0 ? 0.875 : 0.25;
            final double minY = face.offsetY != 0 ? 0.875 : 0.25;
            final double minZ = face.offsetZ != 0 ? 0.875 : 0.25;
            renderer.setRenderBounds(minX, minY, minZ, 1, 1, 1);

            assertTrue(ChiselFacadeRenderer.renderFace(helper, renderer, block, world, 0, 0, 0, 2, face));
            assertEquals(4, renderer.slices.size());
            double area = 0;
            for (final Slice slice : renderer.slices) {
                final double dx = slice.maxX - slice.minX;
                final double dy = slice.maxY - slice.minY;
                final double dz = slice.maxZ - slice.minZ;
                area += face.offsetX != 0 ? dy * dz : face.offsetY != 0 ? dx * dz : dx * dy;
                assertTrue(slice.minX >= minX && slice.minY >= minY && slice.minZ >= minZ);
                assertEquals(1, Math.max(slice.maxX, Math.max(slice.maxY, slice.maxZ)), 0);
                assertTrue(slice.icon.getInterpolatedU(8) >= 0);
            }
            assertEquals(0.75 * 0.75, area, 0.00001);
            assertEquals(minX, renderer.renderMinX, 0);
            assertEquals(minY, renderer.renderMinY, 0);
            assertEquals(minZ, renderer.renderMinZ, 0);
            assertEquals(1, renderer.renderMaxX, 0);
            assertEquals(1, renderer.renderMaxY, 0);
            assertEquals(1, renderer.renderMaxZ, 0);
        }
    }

    private static IBlockAccess world(final Block block) {
        return new StubBlockAccess() {

            @Override
            public Block getBlock(final int x, final int y, final int z) {
                return block;
            }
        };
    }

    private abstract static class StubBlockAccess implements IBlockAccess {

        @Override
        public int getBlockMetadata(final int x, final int y, final int z) {
            return 2;
        }

        @Override
        public TileEntity getTileEntity(final int x, final int y, final int z) {
            return null;
        }

        @Override
        public int getLightBrightnessForSkyBlocks(final int x, final int y, final int z, final int minimum) {
            throw new AssertionError("Unexpected getLightBrightnessForSkyBlocks call");
        }

        @Override
        public boolean isAirBlock(final int x, final int y, final int z) {
            throw new AssertionError("Unexpected isAirBlock call");
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(final int x, final int z) {
            throw new AssertionError("Unexpected getBiomeGenForCoords call");
        }

        @Override
        public int getHeight() {
            throw new AssertionError("Unexpected getHeight call");
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            throw new AssertionError("Unexpected extendedLevelsInChunkCache call");
        }

        @Override
        public int isBlockProvidingPowerTo(final int x, final int y, final int z, final int side) {
            throw new AssertionError("Unexpected isBlockProvidingPowerTo call");
        }

        @Override
        public boolean isSideSolid(final int x, final int y, final int z, final ForgeDirection side,
                final boolean fallback) {
            throw new AssertionError("Unexpected isSideSolid call");
        }
    }

    private static IIcon icon(final String name) {
        final TextureAtlasSprite sprite = new TextureAtlasSprite(name) {};
        sprite.setIconWidth(16);
        sprite.setIconHeight(16);
        sprite.initSprite(16, 16, 0, 0, false);
        return sprite;
    }

    private static final class CtmBlock extends Block implements ICTMBlock<ISubmapManager> {

        private final ISubmapManager manager = new SubmapManagerCTM("test");

        private CtmBlock() {
            super(Material.rock);
        }

        @Override
        public ISubmapManager getManager(final IBlockAccess world, final int x, final int y, final int z,
                final int meta) {
            return this.manager;
        }

        @Override
        public ISubmapManager getManager(final int meta) {
            return this.manager;
        }
    }

    private static final class Slice {

        private final IIcon icon;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private Slice(final RecordingRenderer renderer, final IIcon icon) {
            this.icon = icon;
            this.minX = renderer.renderMinX;
            this.minY = renderer.renderMinY;
            this.minZ = renderer.renderMinZ;
            this.maxX = renderer.renderMaxX;
            this.maxY = renderer.renderMaxY;
            this.maxZ = renderer.renderMaxZ;
        }
    }

    private static final class RecordingRenderer extends RenderBlocksWorkaround {

        private final List<Slice> slices = new ArrayList<>();

        @Override
        public void renderFaceYNeg(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }

        @Override
        public void renderFaceYPos(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }

        @Override
        public void renderFaceZNeg(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }

        @Override
        public void renderFaceZPos(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }

        @Override
        public void renderFaceXNeg(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }

        @Override
        public void renderFaceXPos(final Block block, final double x, final double y, final double z,
                final IIcon icon) {
            this.slices.add(new Slice(this, icon));
        }
    }
}
