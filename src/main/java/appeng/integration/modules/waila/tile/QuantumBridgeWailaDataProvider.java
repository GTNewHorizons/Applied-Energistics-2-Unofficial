package appeng.integration.modules.waila.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.util.DimensionalCoord;
import appeng.core.localization.WailaText;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.me.cluster.implementations.QuantumCluster;
import appeng.tile.qnb.TileQuantumBridge;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public final class QuantumBridgeWailaDataProvider extends BaseWailaDataProvider {

    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> currentToolTip,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {

        final TileEntity te = accessor.getTileEntity();
        if (te instanceof TileQuantumBridge) {
            MovingObjectPosition pos = accessor.getPosition();
            NBTTagCompound nbt = accessor.getNBTData();

            int x = pos.blockX;
            int y = pos.blockY;
            int z = pos.blockZ;
            boolean hasConnection = nbt.getBoolean("hasConnection");

            if (hasConnection) {

                int sideAX = nbt.getInteger("sideAX");
                int sideAY = nbt.getInteger("sideAY");
                int sideAZ = nbt.getInteger("sideAZ");
                int sideADim = nbt.getInteger("sideADim");
                String sideAName = nbt.getString("sideAName");

                int sideBX = nbt.getInteger("sideBX");
                int sideBY = nbt.getInteger("sideBY");
                int sideBZ = nbt.getInteger("sideBZ");
                int sideBDim = nbt.getInteger("sideBDim");
                String sideBName = nbt.getString("sideBName");

                if (x == sideAX && y == sideAY && z == sideAZ) {
                    getInfo(sideBX, sideBY, sideBZ, sideBDim, sideAName, currentToolTip);
                } else if (x == sideBX && y == sideBY && z == sideBZ) {
                    getInfo(sideAX, sideAY, sideAZ, sideADim, sideBName, currentToolTip);
                }
            } else {
                currentToolTip.add(EnumChatFormatting.RED + WailaText.Disconnected.getLocal());
            }

        }

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x,
            int y, int z) {
        if (te instanceof TileQuantumBridge quantumBridge) {
            QuantumCluster cluster = (QuantumCluster) quantumBridge.getCluster();
            if (cluster != null) {
                if (cluster.getConnection() != null) {
                    tag.setBoolean("hasConnection", true);
                    final ILocatable myOtherSide = cluster.getOtherSide() == 0 ? null
                            : AEApi.instance().registries().locatable().getLocatableBy(cluster.getOtherSide());
                    if (myOtherSide instanceof QuantumCluster otherCluster) {
                        DimensionalCoord sideA = new DimensionalCoord(cluster.getCenter());
                        DimensionalCoord sideB = new DimensionalCoord(otherCluster.getCenter());
                        TileQuantumBridge sideABridge = cluster.getCenter();
                        TileQuantumBridge sideBBridge = otherCluster.getCenter();
                        String nameSideA = sideABridge.getInternalInventory().getStackInSlot(0).getDisplayName();
                        String nameSideB = sideBBridge.getInternalInventory().getStackInSlot(0).getDisplayName();

                        tag.setInteger("sideAX", sideA.x);
                        tag.setInteger("sideAY", sideA.y);
                        tag.setInteger("sideAZ", sideA.z);
                        tag.setInteger("sideADim", sideA.getDimension());
                        if (!nameSideA.equals("Quantum Entangled Singularity")) {
                            tag.setString("sideAName", nameSideA);
                        }

                        tag.setInteger("sideBX", sideB.x);
                        tag.setInteger("sideBY", sideB.y);
                        tag.setInteger("sideBZ", sideB.z);
                        tag.setInteger("sideBDim", sideB.getDimension());
                        if (!nameSideB.equals("Quantum Entangled Singularity")) {
                            tag.setString("sideBName", nameSideB);
                        }
                        return tag;
                    }
                }
            }
            tag.setBoolean("hasConnection", false);
        }
        return tag;
    }

    public void getInfo(int x, int y, int z, int dimId, String nameItemStack, List<String> currentToolTip) {
        EnumChatFormatting green = EnumChatFormatting.GREEN;
        EnumChatFormatting yellow = EnumChatFormatting.YELLOW;
        currentToolTip.add(EnumChatFormatting.BLUE + WailaText.ConnectedTo.getLocal() + ":");
        if (!nameItemStack.isEmpty()) {
            currentToolTip.add(green + WailaText.Singularity.getLocal() + ": " + yellow + nameItemStack);
        }
        currentToolTip.add(
                green + WailaText.Dimension.getLocal()
                        + ": "
                        + yellow
                        + WorldProvider.getProviderForDimension(dimId).getDimensionName()
                        + " "
                        + "["
                        + EnumChatFormatting.WHITE
                        + WailaText.Id.getLocal()
                        + ": "
                        + dimId
                        + yellow
                        + "]");
        currentToolTip.add(green + WailaText.CoordinateX.getLocal() + ": " + yellow + x);
        currentToolTip.add(green + WailaText.CoordinateY.getLocal() + ": " + yellow + y);
        currentToolTip.add(green + WailaText.CoordinateZ.getLocal() + ": " + yellow + z);
    }
}
