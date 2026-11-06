package appeng.facade;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IFMP;
import appeng.parts.CableBusContainer;

/** A render-only view of the blocks imitated by facades on one side of a cable bus. */
public final class FacadeBlockAccess implements IBlockAccess {

    private final IBlockAccess world;
    private final int side;
    private final Block block;
    private final int metadata;
    private final int x;
    private final int y;
    private final int z;

    public FacadeBlockAccess(final IBlockAccess world, final int side) {
        this(world, side, null, 0, 0, 0, 0);
    }

    public FacadeBlockAccess(final IBlockAccess world, final int side, final Block block, final int metadata,
            final int x, final int y, final int z) {
        this.world = world;
        this.side = side;
        this.block = block;
        this.metadata = metadata;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static IBlockAccess wrap(final IBlockAccess world, final int side) {
        if (world == null || world instanceof FacadeBlockAccess || side < 0 || side >= 6) {
            return world;
        }
        return new FacadeBlockAccess(world, side);
    }

    @Nullable
    public static IFacadePart getFacade(final IBlockAccess world, final int x, final int y, final int z,
            final int side) {
        if (side < 0 || side >= 6) {
            return null;
        }
        final TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof IPartHost host) {
            return host.getFacadeContainer().getFacade(ForgeDirection.VALID_DIRECTIONS[side]);
        }
        if (tile != null && IntegrationRegistry.INSTANCE.isEnabled(IntegrationType.FMP)) {
            final CableBusContainer container = ((IFMP) IntegrationRegistry.INSTANCE.getInstance(IntegrationType.FMP))
                    .getCableContainer(tile);
            if (container != null) {
                return container.getFacadeContainer().getFacade(ForgeDirection.VALID_DIRECTIONS[side]);
            }
        }
        return null;
    }

    @Nullable
    public static Block getFacadeBlock(final IFacadePart facade) {
        if (facade == null || !(facade.getItem() instanceof ItemBlock item)) {
            return null;
        }
        return item.field_150939_a;
    }

    public static int getFacadeMetadata(final IFacadePart facade) {
        final Item item = facade.getItem();
        return item.getMetadata(facade.getItemDamage());
    }

    @Override
    public Block getBlock(final int x, final int y, final int z) {
        if (this.isFacadePosition(x, y, z)) {
            return this.block;
        }
        final Block facade = getFacadeBlock(getFacade(this.world, x, y, z, this.side));
        return facade == null ? this.world.getBlock(x, y, z) : facade;
    }

    @Override
    public int getBlockMetadata(final int x, final int y, final int z) {
        if (this.isFacadePosition(x, y, z)) {
            return this.metadata;
        }
        final IFacadePart facade = getFacade(this.world, x, y, z, this.side);
        return getFacadeBlock(facade) == null ? this.world.getBlockMetadata(x, y, z) : getFacadeMetadata(facade);
    }

    @Override
    public TileEntity getTileEntity(final int x, final int y, final int z) {
        return this.world.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(final int x, final int y, final int z, final int minimum) {
        return this.world.getLightBrightnessForSkyBlocks(x, y, z, minimum);
    }

    @Override
    public boolean isAirBlock(final int x, final int y, final int z) {
        return this.getBlock(x, y, z).isAir(this, x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(final int x, final int z) {
        return this.world.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return this.world.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return this.world.extendedLevelsInChunkCache();
    }

    @Override
    public int isBlockProvidingPowerTo(final int x, final int y, final int z, final int side) {
        return this.world.isBlockProvidingPowerTo(x, y, z, side);
    }

    @Override
    public boolean isSideSolid(final int x, final int y, final int z, final ForgeDirection side,
            final boolean fallback) {
        final Block facade = this.isFacadePosition(x, y, z) ? this.block
                : getFacadeBlock(getFacade(this.world, x, y, z, this.side));
        if (facade != null) {
            return facade.isSideSolid(this, x, y, z, side);
        }
        return this.world.isSideSolid(x, y, z, side, fallback);
    }

    private boolean isFacadePosition(final int x, final int y, final int z) {
        return this.block != null && this.x == x && this.y == y && this.z == z;
    }
}
