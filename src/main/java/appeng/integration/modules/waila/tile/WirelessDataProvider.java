package appeng.integration.modules.waila.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.client.render.NetworkVisualiserRender;
import appeng.core.localization.WailaText;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.tile.networking.TileWirelessBase;
import appeng.util.Platform;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class WirelessDataProvider extends BaseWailaDataProvider {

    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> currentToolTip,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {

        final TileEntity te = accessor.getTileEntity();
        if (!(te instanceof TileWirelessBase)) {
            return currentToolTip;
        }

        NBTTagCompound tag = accessor.getNBTData();

        if (tag.hasKey("connected")) {
            List<DimensionalCoord> locList = DimensionalCoord.readAsListFromNBT(tag.getCompoundTag("LocList"));
            NetworkVisualiserRender.doWirelessRender(locList);

            switch (locList.size()) {
                case 0:
                    currentToolTip.add(WailaText.wireless_notconnected.getLocal());
                    break;
                case 1: {
                    DimensionalCoord dc = locList.get(0);
                    currentToolTip.add(WailaText.wireless_connected.getLocal(dc.x, dc.y, dc.z));
                    break;
                }
                default: {
                    if (tag.getBoolean("isSneaking")) {
                        currentToolTip.add(WailaText.wireless_connected_detailsTitle.getLocal());
                        for (DimensionalCoord dc : locList) {
                            currentToolTip.add(WailaText.wireless_connected_details.getLocal(dc.x, dc.y, dc.z));
                        }
                        return currentToolTip; // just list connected wireless devices
                    }
                    currentToolTip.add(WailaText.wireless_connected_multiple.getLocal(locList.size()));
                }
            }

            currentToolTip.add(WailaText.wireless_channels.getLocal(tag.getInteger("channels")));
            currentToolTip.add(
                    WailaText.wireless_power
                            .getLocal(Platform.formatNumberDoubleRestrictedByWidth(tag.getDouble("power"), 5)));
        } else {
            currentToolTip.add(WailaText.wireless_notconnected.getLocal());
        }

        AEColor color = AEColor.values()[tag.getInteger("color")];
        if (color != AEColor.Transparent) {
            currentToolTip.add(StatCollector.translateToLocal("gui.appliedenergistics2." + color.name()));
        }

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity te, final NBTTagCompound tag,
            final World world, final int x, final int y, final int z) {
        if (!(te instanceof TileWirelessBase wc)) return tag;

        if (wc.isLinked()) {
            tag.setBoolean("connected", true);
            tag.setInteger("channels", wc.getUsedChannels());
            tag.setDouble("power", wc.getPowerUsage());
            if (wc.isLinked()) {
                NBTTagCompound locList = new NBTTagCompound();
                DimensionalCoord.writeListToNBT(locList, wc.getConnectedCoords());
                tag.setTag("LocList", locList);
            }
            tag.setBoolean("isSneaking", player.isSneaking());
        }

        tag.setInteger("color", wc.getColor().ordinal());

        return tag;
    }
}
