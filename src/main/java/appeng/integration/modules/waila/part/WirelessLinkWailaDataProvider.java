package appeng.integration.modules.waila.part;

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
import appeng.api.parts.IPart;
import appeng.api.util.DimensionalCoord;
import appeng.client.render.NetworkVisualiserRender;
import appeng.core.localization.WailaText;
import appeng.helpers.IWirelessLink;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WirelessAnchor;
import appeng.items.tools.ToolWirelessKit;
import appeng.util.Platform;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public final class WirelessLinkWailaDataProvider extends BasePartWailaDataProvider {

    private static List<DimensionalCoord> coordsOf(final List<WirelessAnchor> anchors) {
        final List<DimensionalCoord> coords = new ArrayList<>(anchors.size());
        for (final WirelessAnchor anchor : anchors) coords.add(anchor.getCoord());
        return coords;
    }

    private static final String TAG_CONNECTED = "wirelessConnected";
    private static final String TAG_LOC_LIST = "wirelessLocList";
    private static final String TAG_CHANNELS = "wirelessChannels";
    private static final String TAG_POWER = "wirelessPower";
    private static final String TAG_SNEAKING = "wirelessSneaking";
    private static final String TAG_HOLD_WAND = "wirelessHoldWand";
    private static final String TAG_WAND_MODE = "wirelessWandMode";

    @Override
    public List<String> getWailaBody(final IPart part, final List<String> currentToolTip,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
        if (!(part instanceof IWirelessLink link)) return currentToolTip;

        final NBTTagCompound tag = accessor.getNBTData();

        if (tag.hasKey(TAG_CONNECTED)) {
            final List<WirelessAnchor> locList = WirelessAnchor.readAsListFromNBT(tag.getCompoundTag(TAG_LOC_LIST));
            NetworkVisualiserRender.doWirelessRender(coordsOf(locList));

            switch (locList.size()) {
                case 0 -> currentToolTip.add(WailaText.wireless_notconnected.getLocal());
                case 1 -> currentToolTip
                        .add(WailaText.wireless_connected.getLocal(locList.get(0).getGuiTextShortNoDim()));
                default -> {
                    if (tag.getBoolean(TAG_SNEAKING)) {
                        currentToolTip.add(WailaText.wireless_connected_detailsTitle.getLocal());
                        for (final WirelessAnchor anchor : locList) {
                            currentToolTip
                                    .add(WailaText.wireless_connected_details.getLocal(anchor.getGuiTextShortNoDim()));
                        }
                        return currentToolTip;
                    }
                    currentToolTip.add(WailaText.wireless_connected_multiple.getLocal(locList.size()));
                }
            }

            currentToolTip.add(WailaText.wireless_channels.getLocal(tag.getInteger(TAG_CHANNELS)));
            currentToolTip.add(
                    WailaText.wireless_power
                            .getLocal(Platform.formatNumberDoubleRestrictedByWidth(tag.getDouble(TAG_POWER), 5)));
        } else {
            currentToolTip.add(WailaText.wireless_notconnected.getLocal());
        }

        if (link.isHub() && tag.getBoolean(TAG_HOLD_WAND)) {
            currentToolTip.add(StatCollector.translateToLocal(tag.getString(TAG_WAND_MODE)));
        }

        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final IPart part, final TileEntity te,
            final NBTTagCompound tag, final World world, final int x, final int y, final int z) {
        if (!(part instanceof IWirelessLink link)) return tag;

        if (link.isLinked()) {
            tag.setBoolean(TAG_CONNECTED, true);
            tag.setInteger(TAG_CHANNELS, link.getUsedChannels());
            tag.setDouble(TAG_POWER, link.getPowerUsage());

            final NBTTagCompound locList = new NBTTagCompound();
            WirelessAnchor.writeListToNBT(locList, link.getConnectedAnchors());
            tag.setTag(TAG_LOC_LIST, locList);

            tag.setBoolean(TAG_SNEAKING, player.isSneaking());
        }

        final ItemStack hand = player.getCurrentEquippedItem();
        if (hand != null && hand.getItem() instanceof ToolWirelessKit
                && WireLessToolHelper.getMode(hand) == WirelessToolMode.Advanced) {
            tag.setBoolean(TAG_HOLD_WAND, true);
            tag.setString(TAG_WAND_MODE, getConnectMode(hand).getUnlocalized());
        }

        return tag;
    }
}
