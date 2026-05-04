package appeng.helpers;

import static appeng.items.tools.ToolWirelessKit.getConfigManager;

import java.util.ArrayList;
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
import appeng.api.config.WirelessToolMode;
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

    public final static String NbtSimple = "Simple";

    public final static String NbtAdvanced = "Advanced";
    public final static String NbtAdvancedLineQueue = "advancedLineQueue";
    public final static String NbtAdvancedLineBinding = "advancedLineBinding";
    public final static String NbtAdvanced1StPoint = "1stPoint";
    public final static String NbtAdvanced2ndPoint = "2ndPoint";

    public final static String NbtSuper = "Super";
    public final static String NbtSuperPins = "pins";
    public final static String NbtSuperNames = "names";
    public final static String NbtSuperPos = "pos";

    public static void nextToolMode(EntityPlayer p, IConfigManager cm) {
        final WirelessToolMode newState = (WirelessToolMode) Platform.rotateEnum(
                cm.getSetting(Settings.WIRELESS_TOOL_MODE),
                false,
                Settings.WIRELESS_TOOL_MODE.getPossibleValues());
        cm.putSetting(Settings.WIRELESS_TOOL_MODE, newState);

        p.addChatMessage(WirelessMessages.SetMode.toChat(newState.getLocal()));
    }

    public static void nextConnectMode(IConfigManager cm, EntityPlayer p) {
        final AdvancedWirelessToolMode newState = (AdvancedWirelessToolMode) Platform.rotateEnum(
                cm.getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE),
                false,
                Settings.ADVANCED_WIRELESS_TOOL_MODE.getPossibleValues());
        cm.putSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE, newState);

        p.addChatMessage(new ChatComponentTranslation(newState.getLocal()));
    }

    public static WirelessToolMode getMode(ItemStack is) {
        final IConfigManager cm = getConfigManager(is);
        return (WirelessToolMode) cm.getSetting(Settings.WIRELESS_TOOL_MODE);
    }

    public static AdvancedWirelessToolMode getConnectMode(ItemStack stack) {
        IConfigManager cm = getConfigManager(stack);
        return ((AdvancedWirelessToolMode) cm.getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE));
    }

    public static void newNBT(ItemStack is) {
        is.setTagCompound(new NBTTagCompound());
        clearNBT(is, WirelessToolMode.Simple, null);
        clearNBT(is, WirelessToolMode.Advanced, null);
        clearNBT(is, WirelessToolMode.Super, null);
    }

    public static void clearNBT(ItemStack is, WirelessToolMode mode, @Nullable EntityPlayer p) {
        final NBTTagCompound tag = is.getTagCompound();
        switch (mode) {
            case Simple -> tag.setTag(NbtSimple, new NBTTagCompound());
            case Advanced -> {
                tag.setTag(NbtAdvanced, new NBTTagCompound());
                tag.setTag(NbtAdvancedLineQueue, new NBTTagCompound());
                tag.setTag(NbtAdvancedLineBinding, new NBTTagCompound());
            }
            case Super -> {
                NBTTagCompound newTag = new NBTTagCompound();
                newTag.setTag(NbtSuperPins, new NBTTagList());
                newTag.setTag(NbtSuperNames, new NBTTagList());
                newTag.setTag(NbtSuperPos, new NBTTagCompound());
                tag.setTag(NbtSuper, newTag);
            }
        }
        if (p != null) p.addChatMessage(WirelessMessages.Cleared.toChat(mode.getLocal()));
    }

    @Nullable
    public static TileWirelessBase getAndCheckTile(DimensionalCoord dc, World w, @Nullable EntityPlayer p) {
        if (dc == null) {
            if (p != null) p.addChatMessage(WirelessMessages.InvalidTarget.toChat());
            return null;
        }

        if (dc.getDimension() != w.provider.dimensionId) {
            if (p != null) p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return null;
        }

        final TileEntity te = w.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof TileWirelessBase twb) return twb;

        if (p != null) p.addChatMessage(WirelessMessages.InvalidTarget.toChat());

        return null;
    }

    public static boolean securityCheck(TileWirelessBase source, BaseActionSource actionSource) {
        if (!Platform.canAccess(source.getProxy(), actionSource)) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.Security.toChat());
            return false;
        }

        return true;
    }

    public static BindResult securityCheck(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        final boolean securitySource = !Platform.canAccess(source.getProxy(), actionSource);
        final boolean securityTarget = !Platform.canAccess(target.getProxy(), actionSource);

        if (securitySource || securityTarget) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.Security.toChat());
            if (securitySource && securityTarget) return BindResult.FAILED;
            if (securitySource) return BindResult.INVALID_SOURCE;
            return BindResult.INVALID_TARGET;
        }

        return BindResult.SUCCESS;
    }

    public static BindResult performConnection(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        return performConnection(target, source, actionSource, true);
    }

    public static BindResult performConnection(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource, boolean sendMessages) {
        if (target.getLocation().getDimension() != source.getLocation().getDimension()) {
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return BindResult.FAILED;
        }

        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return securityCheck;

        if (target.isHub() && !target.canAddLink()) {
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return BindResult.INVALID_TARGET;
        }

        if (source.isHub() && !source.canAddLink()) {
            final DimensionalCoord loc = source.getLocation();
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.SourceHubFull.toChat(loc.getGuiTextShortNoDim()));
            return BindResult.INVALID_SOURCE;
        }

        if (source.isHub() && target.isHub()) {
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.HubToHub.toChat());
            return BindResult.INVALID_SOURCE;
        }

        if (!target.isHub()) target.doUnlink();
        if (!source.isHub()) source.doUnlink();

        final BindResult result = target.doLink(source);
        switch (result) {
            case SUCCESS -> {
                final DimensionalCoord dc = source.getLocation();
                if (sendMessages && actionSource instanceof PlayerSource ps)
                    ps.player.addChatMessage(WirelessMessages.Connected.toChat(dc.getGuiTextShortNoDim()));
            }

            case FAILED, INVALID_TARGET, INVALID_SOURCE -> {
                if (sendMessages && actionSource instanceof PlayerSource ps)
                    ps.player.addChatMessage(WirelessMessages.Failed.toChat());
            }
        }

        return result;
    }

    public static void breakConnection(TileWirelessBase source, BaseActionSource actionSource) {
        source.getConnectedTiles().forEach(t -> {
            final BindResult securityCheck = securityCheck(t, source, actionSource);
            if (securityCheck == BindResult.SUCCESS) WireLessToolHelper.breakConnection(t, source, actionSource);
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(
                    WirelessMessages.Disconnected.toChat(
                            source.getLocation().getGuiTextShortNoDim(),
                            t.getLocation().getGuiTextShortNoDim()));
        });
    }

    public static void breakConnection(TileWirelessBase target, TileWirelessBase source,
            BaseActionSource actionSource) {
        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return;
        target.doUnlink(source);
        if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(
                WirelessMessages.Disconnected.toChat(
                        source.getLocation().getGuiTextShortNoDim(),
                        target.getLocation().getGuiTextShortNoDim()));
    }

    public static boolean bindSimple(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        NBTTagCompound tag = tool.getTagCompound().getCompoundTag(NbtSimple);
        final PlayerSource playerSource = new PlayerSource(p, null);

        if (!securityCheck(target, playerSource)) return false;

        if (tag.hasNoTags()) {
            if (target.isHub() && !target.canAddLink()) {
                p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
                return false;
            }

            DimensionalCoord dc = target.getLocation();
            dc.writeToNBT(tag);
            tool.getTagCompound().setTag(NbtSimple, tag);
            p.addChatMessage(WirelessMessages.SimpleBound.toChat(dc.getGuiTextShortNoDim()));
            return true;
        }

        final DimensionalCoord sourceLocation = DimensionalCoord.readFromNBT(tag);

        if (sourceLocation.getDimension() != w.provider.dimensionId) {
            p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return false;
        }

        if (target.getLocation().isEqual(sourceLocation)) {
            tool.getTagCompound().setTag(NbtSimple, new NBTTagCompound());
            return true;
        }

        TileWirelessBase tile = getAndCheckTile(sourceLocation, w, p);
        if (tile == null) {
            tool.getTagCompound().setTag(NbtSimple, new NBTTagCompound());
            return false;
        }

        if (!securityCheck(tile, playerSource)) {
            tool.getTagCompound().setTag(NbtSimple, new NBTTagCompound());
            return false;
        }

        final BindResult result = performConnection(target, tile, playerSource);
        tool.getTagCompound().setTag(NbtSimple, new NBTTagCompound());
        return result == BindResult.SUCCESS;
    }

    private static boolean isLine(DimensionalCoord firstPoint, DimensionalCoord secondPoint) {
        int line = 0;
        if (firstPoint.equals(secondPoint)) return false;
        if (firstPoint.x != secondPoint.x) line++;
        if (firstPoint.y != secondPoint.y) line++;
        if (firstPoint.z != secondPoint.z) line++;

        return line == 1;
    }

    private static boolean addLine(TileWirelessBase target, ItemStack tool, EntityPlayer p, String nbtKey) {
        final NBTTagCompound tag = tool.getTagCompound().getCompoundTag(nbtKey);
        if (tag.hasKey(NbtAdvanced1StPoint)) {
            if (tag.hasKey(NbtAdvanced2ndPoint)) {
                p.addChatMessage(WirelessMessages.AdvancedLineReset.toChat());
            } else {
                final DimensionalCoord firstPoint = DimensionalCoord
                        .readFromNBT(tag.getCompoundTag(NbtAdvanced1StPoint));
                final DimensionalCoord secondPoint = target.getLocation();
                if (isLine(firstPoint, secondPoint)) {
                    final NBTTagCompound secondPointTag = new NBTTagCompound();
                    secondPoint.writeToNBT(secondPointTag);
                    tag.setTag(NbtAdvanced2ndPoint, secondPointTag);
                    tool.getTagCompound().setTag(nbtKey, tag);

                    p.addChatMessage(WirelessMessages.AdvancedLine2ndAdded.toChat(secondPoint.getGuiTextShortNoDim()));
                    return true;
                } else {
                    p.addChatMessage(WirelessMessages.AdvancedLineNotLine.toChat());
                    tool.getTagCompound().removeTag(nbtKey);
                }
            }
        } else {
            final NBTTagCompound NbtFirstPoint = new NBTTagCompound();
            final DimensionalCoord firstPoint = target.getLocation();
            firstPoint.writeToNBT(NbtFirstPoint);
            tag.setTag(NbtAdvanced1StPoint, NbtFirstPoint);
            tool.getTagCompound().setTag(nbtKey, tag);

            p.addChatMessage(WirelessMessages.AdvancedLine1stAdded.toChat(firstPoint.getGuiTextShortNoDim()));
            return true;
        }

        return false;
    }

    private static boolean addToQueueLine(TileWirelessBase target, ItemStack tool, EntityPlayer p) {
        return addLine(target, tool, p, NbtAdvancedLineQueue);
    }

    private static boolean addToQueue(TileWirelessBase target, ItemStack tool, EntityPlayer p) {
        List<DimensionalCoord> locList = DimensionalCoord
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag(NbtAdvanced));

        DimensionalCoord targetLoc = target.getLocation();
        boolean isHub = target.isHub();

        if (!target.canAddLink()) {
            p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return false;
        } else if (!isHub) { // if not a hub, check if not already in the queue
            for (DimensionalCoord loc : locList) {
                if (targetLoc.isEqual(loc)) {
                    p.addChatMessage(WirelessMessages.BoundAdvancedFilled.toChat());
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
            p.addChatMessage(WirelessMessages.AdvancedQueueingHub.toChat(i));
        } else {
            locList.add(new DimensionalCoord(target));
            p.addChatMessage(WirelessMessages.AdvancedQueued.toChat(targetLoc.getGuiTextShortNoDim()));
        }

        final NBTTagCompound tag = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag(NbtAdvanced, tag);
        return true;
    }

    private static List<TileWirelessBase> getLine(DimensionalCoord firstPoint, DimensionalCoord secondPoint, World w) {
        final List<TileWirelessBase> tiles = new ArrayList<>();
        if (firstPoint.x != secondPoint.x) {
            final int size = Math.abs(firstPoint.x - secondPoint.x) + 1;
            final boolean direction = firstPoint.x < secondPoint.x;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.x = direction ? firstPoint.x + i : firstPoint.x - i;

                if (w.getTileEntity(next.x, next.y, next.z) instanceof TileWirelessBase twb) tiles.add(twb);
            }
        } else if (firstPoint.y != secondPoint.y) {
            final int size = Math.abs(firstPoint.y - secondPoint.y);
            final boolean direction = firstPoint.y < secondPoint.y;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.y = direction ? firstPoint.y + i : firstPoint.y - i;

                if (w.getTileEntity(next.x, next.y, next.z) instanceof TileWirelessBase twb) tiles.add(twb);
            }
        } else if (firstPoint.z != secondPoint.z) {
            final int size = Math.abs(firstPoint.z - secondPoint.z);
            final boolean direction = firstPoint.z < secondPoint.z;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.z = direction ? firstPoint.z + i : firstPoint.z - i;

                if (w.getTileEntity(next.x, next.y, next.z) instanceof TileWirelessBase twb) tiles.add(twb);
            }
        }

        return tiles;
    }

    private static boolean bindFromQueueLine(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        final NBTTagCompound tagQueue = tool.getTagCompound().getCompoundTag(NbtAdvancedLineQueue);
        final NBTTagCompound tagBind = tool.getTagCompound().getCompoundTag(NbtAdvancedLineBinding);
        if (tagQueue.hasKey(NbtAdvanced1StPoint) && tagQueue.hasKey(NbtAdvanced2ndPoint)) {
            if (tagBind.hasKey(NbtAdvanced1StPoint)) {
                addLine(target, tool, p, NbtAdvancedLineBinding);

                final DimensionalCoord firstPointQueue = DimensionalCoord
                        .readFromNBT(tagQueue.getCompoundTag(NbtAdvanced1StPoint));
                final DimensionalCoord secondPointQueue = DimensionalCoord
                        .readFromNBT(tagQueue.getCompoundTag(NbtAdvanced2ndPoint));

                final DimensionalCoord firstPointBind = DimensionalCoord
                        .readFromNBT(tagBind.getCompoundTag(NbtAdvanced1StPoint));
                final DimensionalCoord secondPointBind = DimensionalCoord
                        .readFromNBT(tagBind.getCompoundTag(NbtAdvanced2ndPoint));

                final List<TileWirelessBase> twToBind = getLine(firstPointQueue, secondPointQueue, w);
                final List<TileWirelessBase> twTarget = getLine(firstPointBind, secondPointBind, w);

                bindRows(twToBind, twTarget, p);

                tool.getTagCompound().removeTag(NbtAdvancedLineQueue);
                tool.getTagCompound().removeTag(NbtAdvancedLineBinding);
            } else {
                return addLine(target, tool, p, NbtAdvancedLineBinding);
            }
        }

        return false;
    }

    private static boolean bindFromQueue(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        List<DimensionalCoord> locList = DimensionalCoord
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag(NbtAdvanced));
        if (locList.isEmpty()) {
            p.addChatMessage(WirelessMessages.AdvancedNoConnectors.toChat());
            return false;
        }

        final PlayerSource playerSource = new PlayerSource(p, null);
        final boolean bindMultiple = Platform.keyBindLCtrl.isKeyDown(p) && target.isHub();
        boolean success = false;
        int boundCount = 0;

        if (bindMultiple && !target.canAddLink()) {
            p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return false;
        }

        while (!locList.isEmpty()) {
            final DimensionalCoord sourceLocation = locList.get(0);

            if (sourceLocation.getDimension() != w.provider.dimensionId) {
                p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                break;
            }

            if (target.getLocation().isEqual(sourceLocation)) {
                locList.remove(0);
                success = true;
            } else {
                final TileWirelessBase tile = getAndCheckTile(sourceLocation, w, p);
                if (tile == null) {
                    locList.remove(0);
                } else if (!securityCheck(tile, playerSource)) {
                    locList.remove(0);
                } else {
                    final BindResult result = performConnection(target, tile, playerSource);
                    locList.remove(0);
                    if (result == BindResult.SUCCESS) {
                        boundCount++;
                        success = true;
                    }
                }
            }

            if (!bindMultiple || !target.canAddLink()) break;
        }

        NBTTagCompound tag = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag(NbtAdvanced, tag);

        if (bindMultiple) p.addChatMessage(WirelessMessages.AdvancedBindingHub.toChat(boundCount));
        return success;
    }

    public static boolean bindAdvanced(TileWirelessBase target, ItemStack tool, World w, EntityPlayer p) {
        if (!securityCheck(target, new PlayerSource(p, null))) return false;

        AdvancedWirelessToolMode mod = (AdvancedWirelessToolMode) getConfigManager(tool)
                .getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE);

        return switch (mod) {
            case Queueing -> addToQueue(target, tool, p);
            case Binding -> bindFromQueue(target, tool, w, p);
            case QueueingLine -> addToQueueLine(target, tool, p);
            case BindingLine -> bindFromQueueLine(target, tool, w, p);
        };
    }

    public static void bindRows(List<TileWirelessBase> twToBind, List<TileWirelessBase> twTarget, EntityPlayer p) {
        if (twToBind.isEmpty() || twTarget.isEmpty()) return;

        int i = 0;
        int ii = 0;
        toBind: while (twToBind.size() > i && twTarget.size() > ii) {
            while (twTarget.get(ii).getFreeSlots() > 0) {
                final TileWirelessBase source = twToBind.get(ii);
                final TileWirelessBase target = twTarget.get(i);
                switch (WireLessToolHelper.performConnection(source, target, new PlayerSource(p, null))) {
                    case SUCCESS -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindSuccess.toChat(
                                        source.getLocation().getGuiTextShortNoDim(),
                                        target.getLocation().getGuiTextShortNoDim()));
                        i++;
                        if (!(twToBind.size() > i)) break toBind;
                    }

                    case INVALID_TARGET -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindInvalidTarget
                                        .toChat(target.getLocation().getGuiTextShortNoDim()));
                        ii++;
                    }
                    case INVALID_SOURCE -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindInvalidSource
                                        .toChat(target.getLocation().getGuiTextShortNoDim()));
                        i++;
                    }
                    case FAILED -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindFailed.toChat(target.getLocation().getGuiTextShortNoDim()));
                        i++;
                        ii++;
                    }
                }
            }
            ii++;
        }
    }

    public static boolean bindSuper(TileEntity target, ItemStack tool, World w, EntityPlayer p) {
        if (!tool.getTagCompound().hasKey(NbtSuper)) clearNBT(tool, WirelessToolMode.Super, null);

        final NBTTagCompound tag = tool.getTagCompound().getCompoundTag(NbtSuper).getCompoundTag(NbtSuperPos);
        final List<DimensionalCoord> locList = DimensionalCoord.readAsListFromNBT(tag);
        for (DimensionalCoord dc : locList) {
            TileEntity TempTe = w.getTileEntity(dc.x, dc.y, dc.z);
            if (TempTe instanceof IGridHost tile && target instanceof IGridHost gh) {
                if (gh.getGridNode(ForgeDirection.UNKNOWN).getGrid()
                        == tile.getGridNode(ForgeDirection.UNKNOWN).getGrid()) {
                    p.addChatMessage(WirelessMessages.SuperBoundFailed.toChat());
                    return false;
                }
            }
        }
        DimensionalCoord targetLoc = new DimensionalCoord(target);
        p.addChatMessage(WirelessMessages.SuperBound.toChat(targetLoc.getGuiTextShortNoDim()));
        locList.add(targetLoc);
        DimensionalCoord.writeListToNBT(tag, locList);
        return true;
    }
}
