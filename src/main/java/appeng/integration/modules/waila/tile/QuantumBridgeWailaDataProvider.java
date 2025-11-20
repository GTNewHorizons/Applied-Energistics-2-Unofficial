package appeng.integration.modules.waila.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
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
            int x = accessor.getPosition().blockX;
            int y = accessor.getPosition().blockY;
            int z = accessor.getPosition().blockZ;
            boolean hasConnection = accessor.getNBTData().getBoolean("hasConnection");

            if (hasConnection) {

                int sideAX = accessor.getNBTData().getInteger("sideAX");
                int sideAY = accessor.getNBTData().getInteger("sideAY");
                int sideAZ = accessor.getNBTData().getInteger("sideAZ");
                int sideADim = accessor.getNBTData().getInteger("sideADim");
                String sideAName = accessor.getNBTData().getString("sideAName");

                int sideBX = accessor.getNBTData().getInteger("sideBX");
                int sideBY = accessor.getNBTData().getInteger("sideBY");
                int sideBZ = accessor.getNBTData().getInteger("sideBZ");
                int sideBDim = accessor.getNBTData().getInteger("sideBDim");
                String sideBName = accessor.getNBTData().getString("sideBName");

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
