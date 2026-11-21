package appeng.facade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import appeng.tile.networking.TileCableBus;

public class FacadeBlockAccessTest {

    @Test
    public void pinsTheRenderedBlockAndMetadataWithoutRelyingOnTheHostsTile() {
        final Block imitated = new Block(Material.rock) {};
        final Block cable = new Block(Material.glass) {};
        final IBlockAccess world = new StubBlockAccess(cable);
        final IBlockAccess facade = new FacadeBlockAccess(world, ForgeDirection.NORTH.ordinal(), imitated, 2, 4, 5, 6);

        assertSame(imitated, facade.getBlock(4, 5, 6));
        assertEquals(2, facade.getBlockMetadata(4, 5, 6));
        assertSame(cable, facade.getBlock(5, 5, 6));
        assertEquals(9, facade.getBlockMetadata(5, 5, 6));
    }

    @Test
    public void exposesOnlyTheFacadeOnTheRenderedSideWithMappedMetadata() {
        final Block imitated = new Block(Material.rock) {};
        final Block cable = new Block(Material.glass) {};
        final Item item = new ItemBlock(imitated) {

            @Override
            public int getMetadata(final int damage) {
                return damage & 3;
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
                return 6;
            }
        });
        final IBlockAccess world = new StubBlockAccess(cable) {

            @Override
            public TileEntity getTileEntity(final int x, final int y, final int z) {
                return x == 4 ? host : null;
            }

            @Override
            public int getLightBrightnessForSkyBlocks(final int x, final int y, final int z, final int minimum) {
                return 0x00b00070;
            }
        };
        final IBlockAccess north = FacadeBlockAccess.wrap(world, ForgeDirection.NORTH.ordinal());
        final IBlockAccess south = FacadeBlockAccess.wrap(world, ForgeDirection.SOUTH.ordinal());

        assertSame(imitated, north.getBlock(4, 5, 6));
        assertEquals(2, north.getBlockMetadata(4, 5, 6));
        assertSame(cable, south.getBlock(4, 5, 6));
        assertEquals(9, south.getBlockMetadata(4, 5, 6));
        assertSame(cable, north.getBlock(5, 5, 6));
        assertSame(host, north.getTileEntity(4, 5, 6));
        assertEquals(0x00b00070, north.getLightBrightnessForSkyBlocks(4, 5, 6, 0));
    }

    private static class StubBlockAccess implements IBlockAccess {

        private final Block block;

        private StubBlockAccess(final Block block) {
            this.block = block;
        }

        @Override
        public Block getBlock(final int x, final int y, final int z) {
            return this.block;
        }

        @Override
        public TileEntity getTileEntity(final int x, final int y, final int z) {
            return null;
        }

        @Override
        public int getBlockMetadata(final int x, final int y, final int z) {
            return 9;
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
}
