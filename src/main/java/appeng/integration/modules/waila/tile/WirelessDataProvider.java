package appeng.integration.modules.waila.tile;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import appeng.api.config.PowerMultiplier;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.client.render.NetworkVisualiserRender;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.tile.networking.TileWirelessConnector;
import appeng.tile.networking.TileWirelessHub;
import appeng.util.Platform;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class WirelessDataProvider extends BaseWailaDataProvider {

    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> currentToolTip,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {

        final TileEntity te = accessor.getTileEntity();
        if (te instanceof TileWirelessConnector wc) {
            NBTTagCompound tag = accessor.getNBTData();

            if (tag.hasKey("connected")) {
                if (wc.isHub()) {
                    NetworkVisualiserRender
                            .doWirelessHubRender(DimensionalCoord.readAsListFromNBT(tag.getCompoundTag("hubList")));
                } else {
                    final DimensionalCoord dc = DimensionalCoord.readFromNBT(tag);
                    currentToolTip.add(
                            StatCollector.translateToLocalFormatted(
                                    "waila.appliedenergistics2.wireless.connected",
                                    dc.x,
                                    dc.y,
                                    dc.z));
                    NetworkVisualiserRender.doWirelessRender(dc);
                }
                currentToolTip.add(
                        StatCollector.translateToLocalFormatted(
                                "waila.appliedenergistics2.wireless.channels",
                                tag.getInteger("channels")));
                currentToolTip.add(
                        StatCollector.translateToLocalFormatted(
                                "waila.appliedenergistics2.wireless.power",
                                Platform.formatNumberDoubleRestrictedByWidth(tag.getDouble("power"), 5)));
            } else {
                currentToolTip.add(StatCollector.translateToLocal("waila.appliedenergistics2.wireless.notconnected"));
            }
            if (tag.hasKey("name")) {
                currentToolTip.add(
                        StatCollector.translateToLocalFormatted(
                                "waila.appliedenergistics2.wireless.name",
                                tag.getString("name")));
            }
            AEColor color = AEColor.values()[tag.getInteger("color")];
            if (color != AEColor.Transparent) {
                currentToolTip.add(StatCollector.translateToLocal("gui.appliedenergistics2." + color.name()));
            }
        }
        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity te, final NBTTagCompound tag,
            final World world, final int x, final int y, final int z) {
        if (te instanceof TileWirelessConnector wc) {
            if (wc.hasConnection()) {
                tag.setBoolean("connected", true);
                tag.setInteger("channels", wc.getChannelUsage());
                tag.setDouble("power", PowerMultiplier.CONFIG.multiply(wc.getPowerUsage()));
                if (wc instanceof TileWirelessHub wh && wh.hasConnection()) {
                    NBTTagCompound cordList = new NBTTagCompound();
                    DimensionalCoord.writeListToNBT(cordList, wh.getConnectionList());
                    tag.setTag("hubList", cordList);
                } else {
                    wc.getTarget().writeToNBT(tag);
                }
            }
            if (wc.hasCustomName()) {
                tag.setString("name", wc.getCustomName());
            }
            tag.setInteger("color", wc.getColor().ordinal());
        }
        return tag;
    }
}
