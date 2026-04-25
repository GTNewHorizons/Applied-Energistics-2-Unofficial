package appeng.client.render.previewBlocks;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.parts.IPartCable;
import appeng.api.networking.IGridHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.networking.PartCable;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.grid.AENetworkPowerTile;

public class RendererCable extends AbstractRendererPreview implements IRenderPreview {

    private boolean isShortest = false;

    @Override
    public void renderPreview() {
        EntityPlayer player = ViewHelper.getPlayer();
        if (player == null) return;

        setupGlState(player);

        if (isDense()) {
            renderCableCore(3.0);
            renderDenseCableConnections();
        } else {
            renderCableCore(0.0);
            renderCableConnections();
        }

        clearGlState();
    }

    @Override
    public List<Class<?>> validItemClass() {
        return ViewHelper.getValidClasses(PartCable.class);
    }

    @Override
    protected boolean canPlace(World world, ForgeDirection side, int x, int y, int z) {
        AECableType cableType = ViewHelper.getCableType(ViewHelper.getCachedItemStack());
        boolean isDense = cableType != null && isDenseCable(cableType);
        TileEntity te = world.getTileEntity(x, y, z);
        TileEntity neighborTe = world.getTileEntity(x + side.offsetX, y + side.offsetY, z + side.offsetZ);

        if (te == null) {
            return handleNullTileEntity(isDense, neighborTe, world, side, x, y, z);
        }

        if (te instanceof IPartHost partHost) {
            return handlePartHost(partHost, isDense, neighborTe, x, y, z, world, side);
        }

        if (neighborTe instanceof IPartHost partHost) {
            IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);
            if (centerPart != null) {
                setPreviewOffset(x, y, z, side);
                return false;
            }
            return true;
        }

        if (te instanceof IGridHost gridHost) {
            setPreviewOffset(x, y, z, side);
            return canConnectToGridHost(gridHost, side.getOpposite()) || canPlaceBlockAt(
                    world,
                    ViewHelper.getPreviewX(),
                    ViewHelper.getPreviewY(),
                    ViewHelper.getPreviewZ());
        }

        return false;
    }

    private boolean handlePartHost(IPartHost partHost, boolean isDense, TileEntity neighborTe, int x, int y, int z,
            World world, ForgeDirection side) {
        IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);

        if (centerPart != null) {
            setPreviewOffset(x, y, z, side);
        } else if (isDense) {
            setPreviewOffset(x, y, z, side);
        } else {
            return true;
        }

        if (neighborTe instanceof IPartHost partHostNeighbor) {
            IPart centerPartNeighbor = partHostNeighbor.getPart(ForgeDirection.UNKNOWN);
            if (isDense) return false;
            return centerPartNeighbor == null;
        } else {
            return canPlaceBlockAt(world, ViewHelper.getPreviewX(), ViewHelper.getPreviewY(), ViewHelper.getPreviewZ());
        }
    }

    private boolean handleNullTileEntity(boolean isDense, TileEntity neighborTe, World world, ForgeDirection side,
            int x, int y, int z) {
        setPreviewOffset(x, y, z, side);
        if (neighborTe instanceof IPartHost partHost) {
            IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);
            if (isDense) return false;
            return centerPart == null;
        }
        return canPlaceBlockAt(world, ViewHelper.getPreviewX(), ViewHelper.getPreviewY(), ViewHelper.getPreviewZ());
    }

    private boolean isDense() {
        AECableType type = ViewHelper.getCableType(ViewHelper.getCachedItemStack());
        return type == AECableType.DENSE || type == AECableType.DENSE_COVERED;
    }

    public boolean isDenseCable(AECableType type) {
        return type == AECableType.DENSE || type == AECableType.DENSE_COVERED;
    }

    private void renderCableCore(double size) {
        double min = (6.0 - size) / 16.0;
        double max = (10.0 + size) / 16.0;
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();

        double minX = previewX + min;
        double minY = previewY + min;
        double minZ = previewZ + min;
        double maxX = previewX + max;
        double maxY = previewY + max;
        double maxZ = previewZ + max;

        renderWireframeCube(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void renderDenseCableConnections() {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (shouldRenderConnection(direction)) {
                AECableType neighborType = getNeighborCableType(direction);
                if (isDenseCable(neighborType)) {
                    renderDenseConnection(direction);
                } else {
                    renderNormalConnectionForDense(direction);
                }
            }
        }
    }

    private boolean shouldRenderConnection(ForgeDirection direction) {
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();
        World world = ViewHelper.getWorld();
        TileEntity te = world.getTileEntity(previewX, previewY, previewZ);
        TileEntity neighborTe = world.getTileEntity(
                previewX + direction.offsetX,
                previewY + direction.offsetY,
                previewZ + direction.offsetZ);

        if (te instanceof IPartHost partHost) {
            IPart part = partHost.getPart(direction);
            if (part != null) {
                isShortest = true;
                return true;
            }
        }

        if (neighborTe == null) {
            return false;
        }

        if (neighborTe instanceof IPartHost partHost) {
            isShortest = false;
            return hasConnectablePart(partHost, direction.getOpposite());
        }

        if (neighborTe instanceof IGridHost gridHost) {
            isShortest = true;
            return canConnectToGridHost(gridHost, direction.getOpposite());
        }

        return false;
    }

    private boolean hasConnectablePart(IPartHost partHost, ForgeDirection side) {
        IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);
        IPart partSide = partHost.getPart(side);

        if (canConnectToPart(centerPart, side) && partSide == null) {
            return true;
        }

        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            IPart part = partHost.getPart(direction);
            if (canConnectToPart(part, side) && partSide == null) {
                return true;
            }
        }

        return false;
    }

    private boolean canConnectToPart(IPart part, ForgeDirection side) {
        if (part instanceof IPartCable cable) {
            AEColor color = cable.getCableColor();
            return isColorCompatible(color, ViewHelper.getCableColor(ViewHelper.getCachedItemStack()));
        }
        return false;
    }

    private boolean isColorCompatible(AEColor color1, AEColor color2) {
        if (color1 == AEColor.Transparent || color2 == AEColor.Transparent) return true;
        return color1.matches(color2);
    }

    private boolean canConnectToGridHost(IGridHost gridHost, ForgeDirection side) {
        AECableType connectionType = gridHost.getCableConnectionType(side);

        if (gridHost instanceof AENetworkInvTile) {
            return hasConnectableSide(((AENetworkInvTile) gridHost)::getProxy, side);
        }

        if (gridHost instanceof AENetworkPowerTile) {
            return hasConnectableSide(((AENetworkPowerTile) gridHost)::getProxy, side);
        }

        return connectionType != null && connectionType != AECableType.NONE;
    }

    private boolean hasConnectableSide(Supplier<AENetworkProxy> proxySupplier, ForgeDirection side) {
        EnumSet<ForgeDirection> connectableSides = proxySupplier.get().getConnectableSides();
        if (connectableSides.isEmpty()) {
            proxySupplier.get().onReady();
        }
        return !connectableSides.isEmpty() && connectableSides.contains(side);
    }

    private AECableType getNeighborCableType(ForgeDirection direction) {
        int neighborX = ViewHelper.getPreviewX() + direction.offsetX;
        int neighborY = ViewHelper.getPreviewY() + direction.offsetY;
        int neighborZ = ViewHelper.getPreviewZ() + direction.offsetZ;

        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(neighborX, neighborY, neighborZ);
        if (te == null) {
            return AECableType.NONE;
        }

        if (te instanceof IPartHost partHost) {
            return getCableTypeFromPartHost(partHost, direction);
        }

        if (te instanceof IGridHost gridHost) {
            AECableType connectionType = gridHost.getCableConnectionType(direction.getOpposite());
            return (connectionType != null && connectionType != AECableType.NONE) ? connectionType : AECableType.NONE;
        }

        return AECableType.NONE;
    }

    private AECableType getCableTypeFromPartHost(IPartHost partHost, ForgeDirection direction) {
        IPart centerPart = partHost.getPart(ForgeDirection.UNKNOWN);
        if (centerPart instanceof IGridHost gridHost) {
            AECableType connectionType = gridHost.getCableConnectionType(direction.getOpposite());
            if (connectionType != null && gridHost instanceof PartCable cable) {
                AEColor color = cable.getCableColor();
                if (isColorCompatible(color, ViewHelper.getCableColor(ViewHelper.getCachedItemStack()))) {
                    return connectionType;
                }
            }
        }

        for (ForgeDirection partSide : ForgeDirection.VALID_DIRECTIONS) {
            IPart part = partHost.getPart(partSide);
            if (part instanceof IGridHost gridHost) {
                AECableType connectionType = gridHost.getCableConnectionType(direction.getOpposite());
                if (connectionType != null && connectionType != AECableType.NONE) {
                    return connectionType;
                }
            }
        }

        return AECableType.NONE;
    }

    private void renderDenseConnection(ForgeDirection direction) {
        double minX, minY, minZ, maxX, maxY, maxZ;
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();
        switch (direction) {
            case DOWN:
                minX = previewX + 4.0 / 16.0;
                minY = previewY - 1.0 + 12.0 / 16.0;
                minZ = previewZ + 4.0 / 16.0;
                maxX = previewX + 12.0 / 16.0;
                maxY = previewY + 3.0 / 16.0;
                maxZ = previewZ + 12.0 / 16.0;
                break;
            case UP:
                minX = previewX + 4.0 / 16.0;
                minY = previewY + 13.0 / 16.0;
                minZ = previewZ + 4.0 / 16.0;
                maxX = previewX + 12.0 / 16.0;
                maxY = previewY + 1.0 + 3.0 / 16.0;
                maxZ = previewZ + 12.0 / 16.0;
                break;
            case NORTH:
                minX = previewX + 4.0 / 16.0;
                minY = previewY + 4.0 / 16.0;
                minZ = previewZ - 1.0 + 12.0 / 16.0;
                maxX = previewX + 12.0 / 16.0;
                maxY = previewY + 12.0 / 16.0;
                maxZ = previewZ + 3.0 / 16.0;
                break;
            case SOUTH:
                minX = previewX + 4.0 / 16.0;
                minY = previewY + 4.0 / 16.0;
                minZ = previewZ + 13.0 / 16.0;
                maxX = previewX + 12.0 / 16.0;
                maxY = previewY + 12.0 / 16.0;
                maxZ = previewZ + 1.0 + 3.0 / 16.0;
                break;
            case WEST:
                minX = previewX - 1.0 + 12.0 / 16.0;
                minY = previewY + 4.0 / 16.0;
                minZ = previewZ + 4.0 / 16.0;
                maxX = previewX + 3.0 / 16.0;
                maxY = previewY + 12.0 / 16.0;
                maxZ = previewZ + 12.0 / 16.0;
                break;
            case EAST:
                minX = previewX + 13.0 / 16.0;
                minY = previewY + 4.0 / 16.0;
                minZ = previewZ + 4.0 / 16.0;
                maxX = previewX + 1.0 + 3.0 / 16.0;
                maxY = previewY + 12.0 / 16.0;
                maxZ = previewZ + 12.0 / 16.0;
                break;
            default:
                return;
        }

        renderWireframeCube(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void renderNormalConnectionForDense(ForgeDirection direction) {
        double minX, minY, minZ, maxX, maxY, maxZ;
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();
        switch (direction) {
            case DOWN:
                minX = previewX + 6.0 / 16.0;
                minY = previewY - 1.0 + 10.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 3.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case UP:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + 13.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 1.0 + 6.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case NORTH:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ - 1.0 + 10.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 3.0 / 16.0;
                break;
            case SOUTH:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + 13.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 1.0 + 6.0 / 16.0;
                break;
            case WEST:
                minX = previewX - 1.0 + 10.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 3.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case EAST:
                minX = previewX + 13.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 1.0 + 6.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            default:
                return;
        }

        renderWireframeCube(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void renderCableConnections() {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (shouldRenderConnection(direction)) {
                renderNormalConnection(direction, isShortest);
            }
        }
    }

    private void renderNormalConnection(ForgeDirection direction, boolean shortest) {
        double minX, minY, minZ, maxX, maxY, maxZ;
        int previewX = ViewHelper.getPreviewX();
        int previewY = ViewHelper.getPreviewY();
        int previewZ = ViewHelper.getPreviewZ();
        switch (direction) {
            case DOWN:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + (shortest ? 0.0 : -1.0 + 10.0 / 16.0);
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + (shortest ? 10.0 / 16.0 : 6.0 / 16.0);
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case UP:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + (shortest ? 6.0 / 16.0 : 10.0 / 16.0);
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + (shortest ? 1.0 : 1.0 + 6.0 / 16.0);
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case NORTH:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + (shortest ? 0.0 : -1.0 + 10.0 / 16.0);
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + (shortest ? 10.0 / 16.0 : 6.0 / 16.0);
                break;
            case SOUTH:
                minX = previewX + 6.0 / 16.0;
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + (shortest ? 6.0 / 16.0 : 10.0 / 16.0);
                maxX = previewX + 10.0 / 16.0;
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + (shortest ? 1.0 : 1.0 + 6.0 / 16.0);
                break;
            case WEST:
                minX = previewX + (shortest ? 0.0 : -1.0 + 10.0 / 16.0);
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + (shortest ? 10.0 / 16.0 : 6.0 / 16.0);
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            case EAST:
                minX = previewX + (shortest ? 6.0 / 16.0 : 10.0 / 16.0);
                minY = previewY + 6.0 / 16.0;
                minZ = previewZ + 6.0 / 16.0;
                maxX = previewX + (shortest ? 1.0 : 1.0 + 6.0 / 16.0);
                maxY = previewY + 10.0 / 16.0;
                maxZ = previewZ + 10.0 / 16.0;
                break;
            default:
                return;
        }

        renderWireframeCube(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
