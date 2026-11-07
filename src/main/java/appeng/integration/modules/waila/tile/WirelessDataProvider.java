package appeng.integration.modules.waila.tile;

import static appeng.helpers.WireLessToolHelper.getConnectMode;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import appeng.api.config.WirelessToolMode;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.client.render.NetworkVisualiserRender;
import appeng.core.localization.WailaText;
import appeng.helpers.IWirelessLink;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WirelessAnchor;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.items.tools.ToolWirelessKit;
import appeng.util.Platform;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class WirelessDataProvider extends BaseWailaDataProvider {

    private static List<DimensionalCoord> coordsOf(final List<WirelessAnchor> anchors) {
        final List<DimensionalCoord> coords = new ArrayList<>(anchors.size());
        for (final WirelessAnchor anchor : anchors) coords.add(anchor.getCoord());
        return coords;
    }

    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> currentToolTip,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {

        final TileEntity te = accessor.getTileEntity();
        if (!(te instanceof IWirelessLink wl)) {
            return currentToolTip;
        }

        NBTTagCompound tag = accessor.getNBTData();

        if (tag.hasKey("connected")) {
            List<WirelessAnchor> locList = WirelessAnchor.readAsListFromNBT(tag.getCompoundTag("LocList"));
            NetworkVisualiserRender.doWirelessRender(coordsOf(locList));

            switch (locList.size()) {
                case 0:
                    currentToolTip.add(WailaText.wireless_notconnected.getLocal());
                    break;
                case 1: {
                    WirelessAnchor anchor = locList.get(0);
                    currentToolTip.add(WailaText.wireless_connected.getLocal(anchor.getGuiTextShortNoDim()));
                    break;
                }
                default: {
                    if (tag.getBoolean("isSneaking")) {
                        currentToolTip.add(WailaText.wireless_connected_detailsTitle.getLocal());
                        for (WirelessAnchor anchor : locList) {
                            currentToolTip
                                    .add(WailaText.wireless_connected_details.getLocal(anchor.getGuiTextShortNoDim()));
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

        AEColor color = AEColor.VALUES[tag.getInteger("color")];
        if (color != AEColor.Transparent) {
            currentToolTip.add(StatCollector.translateToLocal("gui.appliedenergistics2." + color.name()));
        }

        if (wl.isHub() && tag.getBoolean("holdWand"))
            currentToolTip.add(StatCollector.translateToLocal(tag.getString("wandConnectMode")));

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity te, final NBTTagCompound tag,
            final World world, final int x, final int y, final int z) {
        if (!(te instanceof IWirelessLink wc)) return tag;

        if (wc.isLinked()) {
            tag.setBoolean("connected", true);
            tag.setInteger("channels", wc.getUsedChannels());
            tag.setDouble("power", wc.getPowerUsage());
            if (wc.isLinked()) {
                NBTTagCompound locList = new NBTTagCompound();
                WirelessAnchor.writeListToNBT(locList, wc.getConnectedAnchors());
                tag.setTag("LocList", locList);
            }
            tag.setBoolean("isSneaking", player.isSneaking());
        }

        final ItemStack hand = player.getCurrentEquippedItem();
        if (hand != null && hand.getItem() instanceof ToolWirelessKit) {
            if (WireLessToolHelper.getMode(hand) == WirelessToolMode.Advanced) {
                final String connectionMode = getConnectMode(hand).getUnlocalized();
                tag.setBoolean("holdWand", true);
                tag.setString("wandConnectMode", connectionMode);
            }
        }

        tag.setInteger("color", wc.getColor().ordinal());

        return tag;
    }
}
