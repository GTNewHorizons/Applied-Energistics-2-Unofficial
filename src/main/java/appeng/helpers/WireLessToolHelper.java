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
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.Settings;
import appeng.api.config.WirelessToolType;
import appeng.api.networking.IGridHost;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.localization.WirelessMessages;
import appeng.tile.networking.TileWirelessBase;
import appeng.util.Platform;

public class WireLessToolHelper {

    public enum BindResult {
        INVALID_SOURCE,
        SUCCESS,
        INVALID_TARGET,
        FAILED,
        ALREADY_BIND
    }

    public static void nextToolMode(EntityPlayer p, IConfigManager cm) {
        final WirelessToolType newState = (WirelessToolType) Platform.rotateEnum(
                cm.getSetting(Settings.WIRELESS_TOOL_TYPE),
                false,
                Settings.WIRELESS_TOOL_TYPE.getPossibleValues());
        cm.putSetting(Settings.WIRELESS_TOOL_TYPE, newState);

        p.addChatMessage(WirelessMessages.set.toChat(newState.getLocal()));
    }

    public static void nextConnectMode(IConfigManager cm, EntityPlayer p) {
        final AdvancedWirelessToolMode newState = (AdvancedWirelessToolMode) Platform.rotateEnum(
                cm.getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE),
                false,
                Settings.ADVANCED_WIRELESS_TOOL_MODE.getPossibleValues());
        cm.putSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE, newState);

        p.addChatMessage(new ChatComponentTranslation(newState.getLocal()));
    }

    public static String getConnectMode(ItemStack stack) {
        IConfigManager cm = getConfigManager(stack);
        return ((AdvancedWirelessToolMode) cm.getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE)).getMode();
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
        if (p != null) p.addChatMessage(WirelessMessages.empty.toChat(mode.getLocal()));
    }

    @Nullable
    public static TileWirelessBase getAndCheckTile(DimensionalCoord dc, World w, @Nullable EntityPlayer p) {
        if (dc == null) throw new NullPointerException("dc is null"); // maybe return null instead

        final TileEntity te = w.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof TileWirelessBase twb) return twb;

        if (p != null) p.addChatMessage(WirelessMessages.invalidTarget.toChat());

        return null;
    }

    public static boolean securityCheck(TileWirelessBase source, BaseActionSource actionSource) {
        if (!Platform.canAccess(source.getProxy(), actionSource)) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(
                    new ChatComponentTranslation("item.appliedenergistics2.ToolSuperWirelessKit.security.player"));
            return false;
        }

        return true;
    }

    public static BindResult securityCheck(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        final boolean securitySource = !Platform.canAccess(source.getProxy(), actionSource);
        final boolean securityTarget = !Platform.canAccess(target.getProxy(), actionSource);

        if (securitySource || securityTarget) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(
                    new ChatComponentTranslation("item.appliedenergistics2.ToolSuperWirelessKit.security.player"));
            if (securitySource && securityTarget) return BindResult.FAILED;
            if (securitySource) return BindResult.INVALID_SOURCE;
            return BindResult.INVALID_TARGET;
        }

        return BindResult.SUCCESS;
    }

    public static BindResult performConnection(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        if (target.getLocation().getDimension() != source.getLocation().getDimension()) {
            if (actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.dimensionMismatch.toChat());
            return BindResult.FAILED;
        }

        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return securityCheck;

        if (target.isHub() && !target.canAddLink()) {
            if (actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.targethubfull.toChat());
            return BindResult.INVALID_TARGET;
        }

        if (source.isHub() && !source.canAddLink()) {
            final DimensionalCoord loc = source.getLocation();
            if (actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.otherhubfull.toChat(loc.getGuiTextShortNoDim()));
            return BindResult.INVALID_SOURCE;
        }

        if (source.isHub() && target.isHub()) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.hubtohub.toChat());
            return BindResult.INVALID_SOURCE;
        }

        final BindResult result = target.doLink(source);
        switch (result) {
            case SUCCESS -> {
                final DimensionalCoord dc = source.getLocation();
                if (actionSource instanceof PlayerSource ps)
                    ps.player.addChatMessage(WirelessMessages.connected.toChat(dc.getGuiTextShortNoDim()));
            }

            case FAILED, INVALID_TARGET, INVALID_SOURCE -> {
                if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.failed.toChat());
            }
        }

        return result;
    }

    public static void breakConnection(TileWirelessBase source, BaseActionSource actionSource) {
        source.getConnectedTiles().forEach(t -> {
            final BindResult securityCheck = securityCheck(t, source, actionSource);
            if (securityCheck == BindResult.SUCCESS) WireLessToolHelper.breakConnection(t, source, actionSource);
            if (actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.disconnected.toChat());
        });
    }

    public static void breakConnection(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return;
        target.doUnlink(source);
        if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.disconnected.toChat());
    }

    public static boolean bindSimple(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        NBTTagCompound tag = tool.getTagCompound().getCompoundTag("simple");

        if (tag.hasNoTags()) {
            DimensionalCoord dc = target.getLocation();
            dc.writeToNBT(tag);
            tool.getTagCompound().setTag("simple", tag);
            p.addChatMessage(WirelessMessages.bound.toChat(dc.x, dc.y, dc.z));
            return true;
        }

        TileWirelessBase tile = getAndCheckTile(DimensionalCoord.readFromNBT(tag), w, p);
        if (tile == null) return false;

        if (target.isConnectedTo(tile)) {
            breakConnection(target, tile, new PlayerSource(p, null));
            tool.getTagCompound().setTag("simple", new NBTTagCompound());
            return true;
        }

        if (performConnection(target, tile, new PlayerSource(p, null)) == BindResult.SUCCESS) {
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

        if (!target.canAddLink()) {
            p.addChatMessage(WirelessMessages.targethubfull.toChat());
            return false;
        } else if (!isHub) { // if not a hub, check if not already in the queue
            for (DimensionalCoord loc : locList) {
                if (targetLoc.isEqual(loc)) {
                    p.addChatMessage(WirelessMessages.bound_advanced_filled.toChat());
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
            p.addChatMessage(WirelessMessages.mode_advanced_queueing_hub.toChat(i));
        } else {
            locList.add(new DimensionalCoord(target));
            p.addChatMessage(WirelessMessages.mode_advanced_queued.toChat(targetLoc.getGuiTextShortNoDim()));
        }

        final NBTTagCompound tag = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag("advanced", tag);
        return true;
    }

    private static boolean bindFromQueue(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        List<DimensionalCoord> locList = DimensionalCoord
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag("advanced"));
        if (locList.isEmpty()) {
            p.addChatMessage(WirelessMessages.mode_advanced_noconnectors.toChat());
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
                if (performConnection(target, tile, new PlayerSource(p, null)) == BindResult.SUCCESS) {
                    locList.remove(0);
                    tile = locList.isEmpty() ? null : getAndCheckTile(locList.get(0), w, p);
                    i++;
                    success = true;
                } else break;
            }
            p.addChatMessage(WirelessMessages.mode_advanced_binding_hub.toChat(i));
        } else if (performConnection(target, tile, new PlayerSource(p, null)) == BindResult.SUCCESS) {
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

    public static boolean bindSuper(TileEntity target, ItemStack tool, World w, EntityPlayer p) {
        if (!tool.getTagCompound().hasKey("super")) {
            clearNBT(tool, WirelessToolType.Super, null);
        }

        NBTTagCompound tag = tool.getTagCompound().getCompoundTag("super").getCompoundTag("pos");
        List<DimensionalCoord> locList = DimensionalCoord.readAsListFromNBT(tag);
        for (DimensionalCoord dc : locList) {
            TileEntity TempTe = w.getTileEntity(dc.x, dc.y, dc.z);
            if (TempTe instanceof IGridHost tile && target instanceof IGridHost gh) {
                if (gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        == tile.getGridNode(ForgeDirection.UNKNOWN).getGrid()) {
                    p.addChatMessage(WirelessMessages.bound_super_failed.toChat());
                    return false;
                }
            }
        }
        DimensionalCoord targetLoc = new DimensionalCoord(target);
        p.addChatMessage(WirelessMessages.bound_super.toChat(targetLoc.getGuiTextShortNoDim()));
        locList.add(targetLoc);
        DimensionalCoord.writeListToNBT(tag, locList);
        return true;
    }
}
