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
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.Settings;
import appeng.api.config.WirelessToolMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.localization.WirelessMessages;
import appeng.server.ServerHelper;
import appeng.util.Platform;

public class WireLessToolHelper {

    public enum BindResult {
        INVALID_SOURCE,
        SUCCESS,
        INVALID_TARGET,
        FAILED,
        ALREADY_BIND,
        TEMPORARY_FAILURE
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
        clearNBT(is, WirelessToolMode.AdvancedLine, null);
        clearNBT(is, WirelessToolMode.Super, null);
    }

    public static void clearNBT(ItemStack is, WirelessToolMode mode, @Nullable EntityPlayer p) {
        final NBTTagCompound tag = is.getTagCompound();
        switch (mode) {
            case Simple -> tag.setTag(NbtSimple, new NBTTagCompound());
            case Advanced -> tag.setTag(NbtAdvanced, new NBTTagCompound());
            case AdvancedLine -> {
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
    public static IWirelessLink resolveLink(WirelessAnchor anchor, World w) {
        final DimensionalCoord coord = anchor.getCoord();
        final TileEntity te = w.getTileEntity(coord.x, coord.y, coord.z);

        if (anchor.isBlockForm()) {
            return te instanceof IWirelessLink link ? link : null;
        }

        if (te instanceof IPartHost host && host.getPart(anchor.getSide()) instanceof IWirelessLink link) {
            return link;
        }

        return null;
    }

    @Nullable
    public static IWirelessLink resolveClicked(final TileEntity te, final float xOff, final float yOff,
            final float zOff) {
        if (te instanceof IWirelessLink link) return link;

        if (te instanceof IPartHost host) {
            final SelectedPart selected = host.selectPart(Vec3.createVectorHelper(xOff, yOff, zOff));
            if (selected != null && selected.part instanceof IWirelessLink link) return link;
        }

        return null;
    }

    @Nullable
    public static IWirelessLink getAndCheckLink(WirelessAnchor anchor, World w, @Nullable EntityPlayer p) {
        if (anchor == null) {
            if (p != null) p.addChatMessage(WirelessMessages.InvalidTarget.toChat());
            return null;
        }

        if (anchor.getDimension() != w.provider.dimensionId) {
            if (p != null) p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return null;
        }

        final IWirelessLink link = resolveLink(anchor, w);
        if (link != null) return link;

        if (p != null) p.addChatMessage(WirelessMessages.InvalidTarget.toChat());

        return null;
    }

    public static boolean securityCheck(IWirelessLink source, BaseActionSource actionSource) {
        if (!Platform.canAccess(source.getLinkProxy(), actionSource)) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.Security.toChat());
            return false;
        }

        return true;
    }

    public static BindResult securityCheck(IWirelessLink target, IWirelessLink source, BaseActionSource actionSource) {
        final boolean securitySource = !Platform.canAccess(source.getLinkProxy(), actionSource);
        final boolean securityTarget = !Platform.canAccess(target.getLinkProxy(), actionSource);

        if (securitySource || securityTarget) {
            if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(WirelessMessages.Security.toChat());
            if (securitySource && securityTarget) return BindResult.FAILED;
            if (securitySource) return BindResult.INVALID_SOURCE;
            return BindResult.INVALID_TARGET;
        }

        return BindResult.SUCCESS;
    }

    public static BindResult performConnection(IWirelessLink target, IWirelessLink source,
            BaseActionSource actionSource) {
        return performConnection(target, source, actionSource, true, true, false);
    }

    private static BindResult performConnection(IWirelessLink target, IWirelessLink source,
            BaseActionSource actionSource, boolean sendMessages, boolean unlinkExisting, boolean restoring) {
        if (target.getLocation().getDimension() != source.getLocation().getDimension()) {
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return BindResult.FAILED;
        }

        if (target.getAnchor().equals(source.getAnchor())) return BindResult.FAILED;

        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return restoring ? BindResult.TEMPORARY_FAILURE : securityCheck;

        if (target.isConnectedTo(source)) return BindResult.ALREADY_BIND;

        if (target.isHub() && !target.canAddLink(source)) {
            if (sendMessages && actionSource instanceof PlayerSource ps)
                ps.player.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return BindResult.INVALID_TARGET;
        }

        if (source.isHub() && !source.canAddLink(target)) {
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

        if (unlinkExisting) {
            if (!target.isHub()) target.unlinkAll();
            if (!source.isHub()) source.unlinkAll();
        }

        final BindResult result = unlinkExisting ? target.getLinkLogic().doLink(source)
                : target.getLinkLogic().restoreLink(source);
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

    public static BindResult restoreConnection(IWirelessLink target, IWirelessLink source,
            BaseActionSource actionSource) {
        final BindResult result = performConnection(target, source, actionSource, false, false, true);
        if (result != BindResult.SUCCESS && result != BindResult.ALREADY_BIND
                && result != BindResult.TEMPORARY_FAILURE) {
            source.getLinkLogic().unlink(target);
            target.getLinkLogic().unlink(source);
        }
        return result;
    }

    public static void breakConnection(IWirelessLink source, BaseActionSource actionSource) {
        if (!securityCheck(source, actionSource)) return;

        final List<IWirelessLink> connectedTiles = new ArrayList<>(source.getConnectedLinks());
        source.unlinkAll();
        if (actionSource instanceof PlayerSource ps) connectedTiles.forEach(
                t -> ps.player.addChatMessage(
                        WirelessMessages.Disconnected.toChat(
                                source.getAnchor().getGuiTextShortNoDim(),
                                t.getAnchor().getGuiTextShortNoDim())));
    }

    public static void breakConnection(IWirelessLink target, IWirelessLink source, BaseActionSource actionSource) {
        final BindResult securityCheck = securityCheck(target, source, actionSource);
        if (securityCheck != BindResult.SUCCESS) return;
        target.getLinkLogic().unlink(source);
        if (actionSource instanceof PlayerSource ps) ps.player.addChatMessage(
                WirelessMessages.Disconnected
                        .toChat(source.getAnchor().getGuiTextShortNoDim(), target.getAnchor().getGuiTextShortNoDim()));
    }

    public static boolean bindSimple(IWirelessLink target, ItemStack tool, World w, EntityPlayer p) {
        NBTTagCompound tag = tool.getTagCompound().getCompoundTag(NbtSimple);
        final PlayerSource playerSource = new PlayerSource(p, null);

        if (!securityCheck(target, playerSource)) return false;

        if (tag.hasNoTags()) {
            if (target.isHub() && !target.canAddLink()) {
                p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
                return false;
            }

            final WirelessAnchor anchor = target.getAnchor();
            anchor.writeToNBT(tag);
            tool.getTagCompound().setTag(NbtSimple, tag);
            p.addChatMessage(WirelessMessages.SimpleBound.toChat(anchor.getGuiTextShortNoDim()));
            return true;
        }

        final WirelessAnchor sourceAnchor = WirelessAnchor.readFromNBT(tag);

        if (sourceAnchor.getDimension() != w.provider.dimensionId) {
            p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return false;
        }

        if (target.getAnchor().equals(sourceAnchor)) {
            tool.getTagCompound().setTag(NbtSimple, new NBTTagCompound());
            return true;
        }

        IWirelessLink tile = getAndCheckLink(sourceAnchor, w, p);
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

    private static boolean addLine(IWirelessLink target, ItemStack tool, EntityPlayer p, String nbtKey) {
        final NBTTagCompound tag = tool.getTagCompound().getCompoundTag(nbtKey);
        if (tag.hasKey(NbtAdvanced1StPoint)) {
            if (tag.hasKey(NbtAdvanced2ndPoint)) {
                p.addChatMessage(WirelessMessages.AdvancedLineReset.toChat());
            } else {
                final WirelessAnchor firstPoint = WirelessAnchor.readFromNBT(tag.getCompoundTag(NbtAdvanced1StPoint));
                final WirelessAnchor secondPoint = target.getAnchor();
                if (isLine(firstPoint.getCoord(), secondPoint.getCoord())) {
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
            final WirelessAnchor firstPoint = target.getAnchor();
            firstPoint.writeToNBT(NbtFirstPoint);
            tag.setTag(NbtAdvanced1StPoint, NbtFirstPoint);
            tool.getTagCompound().setTag(nbtKey, tag);

            p.addChatMessage(WirelessMessages.AdvancedLine1stAdded.toChat(firstPoint.getGuiTextShortNoDim()));
            return true;
        }

        return false;
    }

    private static boolean addToQueueLine(IWirelessLink target, ItemStack tool, EntityPlayer p) {
        return addLine(target, tool, p, NbtAdvancedLineQueue);
    }

    private static boolean addToQueue(IWirelessLink target, ItemStack tool, EntityPlayer p) {
        List<WirelessAnchor> locList = WirelessAnchor
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag(NbtAdvanced));

        WirelessAnchor targetAnchor = target.getAnchor();
        boolean isHub = target.isHub();

        if (!target.canAddLink()) {
            p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return false;
        } else if (!isHub) { // if not a hub, check if not already in the queue
            for (WirelessAnchor loc : locList) {
                if (targetAnchor.equals(loc)) {
                    p.addChatMessage(WirelessMessages.BoundAdvancedFilled.toChat());
                    return false;
                }
            }
        }

        if (ServerHelper.WIRELESS_EXTRA_ACTION.isKeyDown(p) && isHub) {
            int i = 0;
            while (i < target.getFreeSlots()) {
                locList.add(targetAnchor);
                i++;
            }
            p.addChatMessage(WirelessMessages.AdvancedQueueingHub.toChat(i));
        } else {
            locList.add(targetAnchor);
            p.addChatMessage(WirelessMessages.AdvancedQueued.toChat(targetAnchor.getGuiTextShortNoDim()));
        }

        final NBTTagCompound tag = new NBTTagCompound();
        WirelessAnchor.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag(NbtAdvanced, tag);
        return true;
    }

    @Nullable
    private static IWirelessLink resolveAlong(DimensionalCoord at, ForgeDirection a, ForgeDirection b, World w) {
        IWirelessLink link = resolveLink(new WirelessAnchor(at, a), w);
        if (link == null && b != a) link = resolveLink(new WirelessAnchor(at, b), w);
        // both endpoints are fixtures, so the block lookup has not been tried yet
        if (link == null && a != ForgeDirection.UNKNOWN && b != ForgeDirection.UNKNOWN)
            link = resolveLink(new WirelessAnchor(at, ForgeDirection.UNKNOWN), w);
        return link;
    }

    private static List<IWirelessLink> getLine(WirelessAnchor first, WirelessAnchor second, World w) {
        final List<IWirelessLink> tiles = new ArrayList<>();
        final DimensionalCoord firstPoint = first.getCoord();
        final DimensionalCoord secondPoint = second.getCoord();
        if (firstPoint.x != secondPoint.x) {
            final int size = Math.abs(firstPoint.x - secondPoint.x) + 1;
            final boolean direction = firstPoint.x < secondPoint.x;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.x = direction ? firstPoint.x + i : firstPoint.x - i;

                final IWirelessLink link = resolveAlong(next, first.getSide(), second.getSide(), w);
                if (link != null) tiles.add(link);
            }
        } else if (firstPoint.y != secondPoint.y) {
            final int size = Math.abs(firstPoint.y - secondPoint.y) + 1;
            final boolean direction = firstPoint.y < secondPoint.y;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.y = direction ? firstPoint.y + i : firstPoint.y - i;

                final IWirelessLink link = resolveAlong(next, first.getSide(), second.getSide(), w);
                if (link != null) tiles.add(link);
            }
        } else if (firstPoint.z != secondPoint.z) {
            final int size = Math.abs(firstPoint.z - secondPoint.z) + 1;
            final boolean direction = firstPoint.z < secondPoint.z;
            for (int i = 0; i < size; i++) {
                final DimensionalCoord next = new DimensionalCoord(firstPoint);
                next.z = direction ? firstPoint.z + i : firstPoint.z - i;

                final IWirelessLink link = resolveAlong(next, first.getSide(), second.getSide(), w);
                if (link != null) tiles.add(link);
            }
        }

        return tiles;
    }

    private static boolean bindFromQueueLine(IWirelessLink target, ItemStack tool, World w, EntityPlayer p) {
        final NBTTagCompound tagQueue = tool.getTagCompound().getCompoundTag(NbtAdvancedLineQueue);
        final NBTTagCompound tagBind = tool.getTagCompound().getCompoundTag(NbtAdvancedLineBinding);
        if (tagQueue.hasKey(NbtAdvanced1StPoint) && tagQueue.hasKey(NbtAdvanced2ndPoint)) {
            if (tagBind.hasKey(NbtAdvanced1StPoint)) {
                addLine(target, tool, p, NbtAdvancedLineBinding);

                final WirelessAnchor firstPointQueue = WirelessAnchor
                        .readFromNBT(tagQueue.getCompoundTag(NbtAdvanced1StPoint));
                final WirelessAnchor secondPointQueue = WirelessAnchor
                        .readFromNBT(tagQueue.getCompoundTag(NbtAdvanced2ndPoint));

                final WirelessAnchor firstPointBind = WirelessAnchor
                        .readFromNBT(tagBind.getCompoundTag(NbtAdvanced1StPoint));
                final WirelessAnchor secondPointBind = WirelessAnchor
                        .readFromNBT(tagBind.getCompoundTag(NbtAdvanced2ndPoint));

                final List<IWirelessLink> twToBind = getLine(firstPointQueue, secondPointQueue, w);
                final List<IWirelessLink> twTarget = getLine(firstPointBind, secondPointBind, w);

                bindRows(twToBind, twTarget, p);

                tool.getTagCompound().removeTag(NbtAdvancedLineQueue);
                tool.getTagCompound().removeTag(NbtAdvancedLineBinding);
            } else {
                return addLine(target, tool, p, NbtAdvancedLineBinding);
            }
        }

        return false;
    }

    private static boolean bindFromQueue(IWirelessLink target, ItemStack tool, World w, EntityPlayer p) {
        List<WirelessAnchor> locList = WirelessAnchor
                .readAsListFromNBT(tool.getTagCompound().getCompoundTag(NbtAdvanced));
        if (locList.isEmpty()) {
            p.addChatMessage(WirelessMessages.AdvancedNoConnectors.toChat());
            return false;
        }

        final PlayerSource playerSource = new PlayerSource(p, null);
        final boolean bindMultiple = ServerHelper.WIRELESS_EXTRA_ACTION.isKeyDown(p) && target.isHub();
        boolean success = false;
        int boundCount = 0;

        if (bindMultiple && !target.canAddLink()) {
            p.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return false;
        }

        while (!locList.isEmpty()) {
            final WirelessAnchor sourceAnchor = locList.get(0);

            if (sourceAnchor.getDimension() != w.provider.dimensionId) {
                p.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                break;
            }

            if (target.getAnchor().equals(sourceAnchor)) {
                locList.remove(0);
                success = true;
            } else {
                final IWirelessLink tile = getAndCheckLink(sourceAnchor, w, p);
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
        WirelessAnchor.writeListToNBT(tag, locList);
        tool.getTagCompound().setTag(NbtAdvanced, tag);

        if (bindMultiple) p.addChatMessage(WirelessMessages.AdvancedBindingHub.toChat(boundCount));
        return success;
    }

    public static boolean bindAdvanced(IWirelessLink target, ItemStack tool, World w, EntityPlayer p, boolean line) {
        if (!securityCheck(target, new PlayerSource(p, null))) return false;

        AdvancedWirelessToolMode mod = (AdvancedWirelessToolMode) getConfigManager(tool)
                .getSetting(Settings.ADVANCED_WIRELESS_TOOL_MODE);

        return switch (mod) {
            case Queueing -> line ? addToQueueLine(target, tool, p) : addToQueue(target, tool, p);
            case Binding -> line ? bindFromQueueLine(target, tool, w, p) : bindFromQueue(target, tool, w, p);
        };
    }

    public static void bindRows(List<IWirelessLink> twToBind, List<IWirelessLink> twTarget, EntityPlayer p) {
        if (twToBind.isEmpty() || twTarget.isEmpty()) return;

        int i = 0;
        int ii = 0;
        toBind: while (twToBind.size() > i && twTarget.size() > ii) {
            while (twToBind.size() > i && twTarget.size() > ii && twTarget.get(ii).getFreeSlots() > 0) {
                final IWirelessLink source = twToBind.get(i);
                final IWirelessLink target = twTarget.get(ii);
                switch (WireLessToolHelper.performConnection(target, source, new PlayerSource(p, null))) {
                    case SUCCESS -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindSuccess.toChat(
                                        source.getAnchor().getGuiTextShortNoDim(),
                                        target.getAnchor().getGuiTextShortNoDim()));
                        i++;
                        if (!(twToBind.size() > i)) break toBind;
                    }

                    case INVALID_TARGET -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindInvalidTarget
                                        .toChat(target.getAnchor().getGuiTextShortNoDim()));
                        ii++;
                    }
                    case INVALID_SOURCE -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindInvalidSource
                                        .toChat(source.getAnchor().getGuiTextShortNoDim()));
                        i++;
                    }
                    case FAILED -> {
                        p.addChatMessage(
                                WirelessMessages.rowBindFailed.toChat(
                                        source.getAnchor().getGuiTextShortNoDim(),
                                        target.getAnchor().getGuiTextShortNoDim()));
                        i++;
                        ii++;
                    }
                    case ALREADY_BIND -> {
                        i++;
                    }
                }
            }
            ii++;
        }
    }

    @Nullable
    static IGrid gridOf(final IGridHost host, final ForgeDirection side) {
        final IGridNode node = host.getGridNode(side);
        return node == null ? null : node.getGrid();
    }

    public static boolean bindSuper(TileEntity target, ItemStack tool, World w, EntityPlayer p, float xOff, float yOff,
            float zOff) {
        if (!tool.getTagCompound().hasKey(NbtSuper)) clearNBT(tool, WirelessToolMode.Super, null);
        if (!(target instanceof IGridHost gh)) return false;

        final IWirelessLink clicked = resolveClicked(target, xOff, yOff, zOff);
        final ForgeDirection side = clicked == null ? ForgeDirection.UNKNOWN : clicked.getAnchor().getSide();

        // a cable bus holding only an external fixture has no centre cable, so a click anywhere else finds no grid
        final IGrid targetGrid = gridOf(gh, side);
        if (targetGrid == null) {
            p.addChatMessage(WirelessMessages.SuperBoundFailed.toChat());
            return false;
        }

        final NBTTagCompound tag = tool.getTagCompound().getCompoundTag(NbtSuper).getCompoundTag(NbtSuperPos);
        final List<WirelessAnchor> locList = WirelessAnchor.readAsListFromNBT(tag);
        for (final WirelessAnchor anchor : locList) {
            if (anchor.getDimension() != w.provider.dimensionId) continue;

            final DimensionalCoord dc = anchor.getCoord();
            if (w.getTileEntity(dc.x, dc.y, dc.z) instanceof IGridHost tile
                    && targetGrid == gridOf(tile, anchor.getSide())) {
                p.addChatMessage(WirelessMessages.SuperBoundFailed.toChat());
                return false;
            }
        }

        final WirelessAnchor targetAnchor = new WirelessAnchor(new DimensionalCoord(target), side);
        p.addChatMessage(WirelessMessages.SuperBound.toChat(targetAnchor.getGuiTextShortNoDim()));
        locList.add(targetAnchor);
        WirelessAnchor.writeListToNBT(tag, locList);
        return true;
    }
}
