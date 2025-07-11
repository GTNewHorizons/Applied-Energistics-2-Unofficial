package appeng.helpers;

import static appeng.items.tools.ToolSuperWirelessKit.getConfigManager;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.WirelessToolType;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.localization.WirelessToolMessages;
import appeng.tile.networking.TileWirelessBase;
import appeng.util.Platform;

public class WireLessToolHelper {

    public static void nextMode(EntityPlayer p, IConfigManager cm) {
        final WirelessToolType newState = (WirelessToolType) Platform.rotateEnum(
                cm.getSetting(Settings.WIRELESS_TOOL_TYPE),
                false,
                Settings.WIRELESS_TOOL_TYPE.getPossibleValues());
        cm.putSetting(Settings.WIRELESS_TOOL_TYPE, newState);

        p.addChatMessage(WirelessToolMessages.set.toChat(EnumChatFormatting.YELLOW + newState.getLocal()));
    }

    public static void nextConnectMode(IConfigManager cm, EntityPlayer p) {
        final AdvancedWirelessToolMode newState = (AdvancedWirelessToolMode) Platform.rotateEnum(
                cm.getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE),
                false,
                Settings.ADVANCED_WIRELESS_TOOL_MODE.getPossibleValues());
        cm.putSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE, newState);

        p.addChatMessage(new ChatComponentTranslation(newState.getLocal()));
    }

    public static void clearNBT(ItemStack is, WirelessToolType mode, @Nullable EntityPlayer p) {
        switch (mode) {
            case Simple -> is.getTagCompound().setTag("simple", new NBTTagCompound());
            case Advanced -> is.getTagCompound().setTag("advanced", new NBTTagCompound());
            case Super -> {
                NBTTagCompound newTag = new NBTTagCompound();
                newTag.setTag("pins", new NBTTagList());
                newTag.setTag("names", new NBTTagList());
                newTag.setTag("pos", new NBTTagCompound());
                is.getTagCompound().setTag("super", newTag);
            }
        }
        if (p != null) p.addChatMessage(WirelessToolMessages.empty.toChat(EnumChatFormatting.YELLOW + mode.getLocal()));
    }

    public static boolean hasBuildPermissions(TileWirelessBase gh, EntityPlayer p) {
        return ((ISecurityGrid) gh.getGridNode(ForgeDirection.UNKNOWN).getGrid().getCache(ISecurityGrid.class))
                .hasPermission(p, SecurityPermissions.BUILD);
    }

    @Nullable
    public static TileWirelessBase getAndCheckTile(DimensionalCoord dc, World w, @Nullable EntityPlayer p) {
        if (dc == null) {
            throw new NullPointerException("dc is null"); // maybe return null instead
        }

        TileEntity te = w.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof TileWirelessBase) {
            return (TileWirelessBase) te;
        }
        if (p != null) p.addChatMessage(
                WirelessToolMessages.invalidTarget.toChat()
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));

        return null;
    }

    public static boolean performConnection(TileWirelessBase target, TileWirelessBase otherTile, EntityPlayer p) {
        if (target.getLocation().getDimension() != otherTile.getLocation().getDimension()) {
            p.addChatMessage(WirelessToolMessages.dimensionMismatch.toChat());
            return false;
        }

        if (target.isHub() && !target.canAddLink()) {
            p.addChatMessage(WirelessToolMessages.targethubfull.toChat());
            return false;
        }

        if (otherTile.isHub() && !otherTile.canAddLink()) {
            DimensionalCoord loc = otherTile.getLocation();
            p.addChatMessage(WirelessToolMessages.otherhubfull.toChat(loc.x, loc.y, loc.z));
            return false;
        }

        if (target.doLink(otherTile)) {
            DimensionalCoord dc = otherTile.getLocation();
            p.addChatMessage(
                    WirelessToolMessages.connected.toChat(dc.x, dc.y, dc.z)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            return true;
        } else {
            p.addChatMessage(
                    WirelessToolMessages.failed.toChat()
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
        }

        return false;
    }

    public static void breakConnection(TileWirelessBase target, TileWirelessBase otherTile, EntityPlayer p) {
        target.doUnlink(otherTile);
        p.addChatMessage(WirelessToolMessages.disconnected.toChat());
    }

    public static boolean bindSimple(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        NBTTagCompound tag = tool.getTagCompound().getCompoundTag("simple");

        if (tag.hasNoTags()) {
            DimensionalCoord dc = target.getLocation();
            dc.writeToNBT(tag);
            tool.getTagCompound().setTag("simple", tag);
            p.addChatMessage(
                    WirelessToolMessages.bound.toChat(dc.x, dc.y, dc.z)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
            return true;
        }

        TileWirelessBase tile = getAndCheckTile(DimensionalCoord.readFromNBT(tag), w, p);
        if (tile == null) {
            return false;
        }

        if (target.isConnectedTo(tile)) {
            breakConnection(target, tile, p);
            tool.getTagCompound().setTag("simple", new NBTTagCompound());
            return true;
        }

        if (performConnection(target, tile, p)) {
            tool.getTagCompound().setTag("simple", new NBTTagCompound());
            return true;
        }
        return false;
    }

    private static boolean addToQueue(TileWirelessBase target, ItemStack tool, EntityPlayer p) {
        List<DimensionalCoord> locList = DimensionalCoord
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag("advanced"));

        DimensionalCoord targetLoc = target.getLocation();
        boolean isHub = target.isHub();

        if (!isHub) {
            for (DimensionalCoord loc : locList) {
                if (targetLoc.isEqual(loc)) {
                    p.addChatMessage(
                            WirelessToolMessages.bound_advanced_filled.toChat()
                                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    return false;
                }
            }
        } else if (!target.canAddLink()) {
            p.addChatMessage(
                    WirelessToolMessages.targethubfull.toChat()
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            return false;
        }

        if (!target.canAddLink()) {
            p.addChatMessage(
                    WirelessToolMessages.targethubfull.toChat()
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
            return false;
        } else if (!isHub) { // if not a hub, check if not already in the queue
            for (DimensionalCoord loc : locList) {
                if (targetLoc.isEqual(loc)) {
                    p.addChatMessage(
                            WirelessToolMessages.bound_advanced_filled.toChat()
                                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    return false;
                }
            }
        }

        if (Platform.keyBindLCtrl.isKeyDown(p) && isHub) {
            int i = 0;
            while (i < target.getFreeSlots()) {
                locList.add(new DimensionalCoord(target));
                i++;
            }
            p.addChatMessage(WirelessToolMessages.mode_advanced_queueing_hub.toChat(i));
        } else {
            locList.add(new DimensionalCoord(target));
            p.addChatMessage(WirelessToolMessages.mode_advanced_queueing.toChat(targetLoc.x, targetLoc.y, targetLoc.z));
        }

        DimensionalCoord.writeListToNBT(tool.getTagCompound().getCompoundTag("advanced"), locList);
        return true;
    }

    private static boolean bindFromQueue(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        List<DimensionalCoord> locList = DimensionalCoord
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag("advanced"));
        if (locList.isEmpty()) {
            p.addChatMessage(WirelessToolMessages.mode_advanced_noconnectors.toChat());
            return false;
        }

        TileWirelessBase tile = getAndCheckTile(locList.get(0), w, p);
        if (tile == null) {
            locList.remove(0);
            return false;
        }

        boolean success = false;
        if (Platform.keyBindLCtrl.isKeyDown(p) && target.isHub()) {
            int i = 0;
            while (tile != null && target.canAddLink()) {
                if (performConnection(target, tile, p)) {
                    locList.remove(0);
                    tile = locList.isEmpty() ? null : getAndCheckTile(locList.get(0), w, p);
                    i++;
                    success = true;
                } else break;
            }
            p.addChatMessage(WirelessToolMessages.mode_advanced_binding_hub.toChat(i));
        } else if (performConnection(target, tile, p)) {
            locList.remove(0);
            success = true;
        }

        NBTTagCompound tag = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag("advanced", tag);
        return success;
    }

    public static boolean bindAdvanced(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        AdvancedWirelessToolMode mod = (AdvancedWirelessToolMode) getConfigManager(tool)
                .getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE);

        return switch (mod) {
            case Queueing, QueueingLine -> addToQueue(target, tool, p);
            case Binding, BindingLine -> bindFromQueue(target, tool, w, p);
        };
    }

    public static boolean bindSuper(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        if (!tool.getTagCompound().hasKey("super")) {
            clearNBT(tool, WirelessToolType.Super, null);
        }

        NBTTagCompound tag = tool.getTagCompound().getCompoundTag("super").getCompoundTag("pos");
        List<DimensionalCoord> locList = DimensionalCoord.readAsListFromNBT(tag);
        for (DimensionalCoord dc : locList) {
            TileEntity TempTe = w.getTileEntity(dc.x, dc.y, dc.z);
            if (TempTe instanceof TileWirelessBase tile) {
                if (target.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        == tile.getGridNode(ForgeDirection.UNKNOWN).getGrid()) {
                    p.addChatMessage(
                            WirelessToolMessages.bound_super_failed.toChat()
                                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    return false;
                }
            }
        }
        DimensionalCoord targetLoc = target.getLocation();
        p.addChatMessage(
                WirelessToolMessages.bound_super.toChat(targetLoc.x, targetLoc.y, targetLoc.z, locList.size())
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
        locList.add(targetLoc);
        DimensionalCoord.writeListToNBT(tag, locList);
        return true;
    }
}
