/*
 * Copyright (c) bdew, 2014 - 2015 https://github.com/bdew/ae2stuff This mod is distributed under the terms of the
 * Minecraft Mod Public License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package appeng.items.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Settings;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.features.AEFeature;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketNetworkVisualiserData;
import appeng.items.AEBaseItem;
import appeng.util.ConfigManager;
import appeng.util.Platform;

public class ToolNetworkVisualiser extends AEBaseItem {

    private boolean needUpdate = true;

    public ToolNetworkVisualiser() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    public enum VNodeFlags {
        DENSE,
        MISSING
    }

    public static class VNode {

        public final int x;
        public final int y;
        public final int z;
        public final EnumSet<VNodeFlags> flags;

        public VNode(int x, int y, int z, EnumSet<VNodeFlags> flags) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.flags = flags;
        }
    }

    public enum VLinkFlags {
        DENSE,
        COMPRESSED
    }

    public static class VLink {

        public final VNode node1;
        public final VNode node2;
        public final int channels;
        public final EnumSet<VLinkFlags> flags;

        public VLink(VNode node1, VNode node2, int channels, EnumSet<VLinkFlags> flags) {
            this.node1 = node1;
            this.node2 = node2;
            this.channels = channels;
            this.flags = flags;
        }
    }

    public enum VisualisationModes {
        FULL,
        NODES,
        CHANNELS,
        NONUM,
        P2P;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World worldIn, EntityPlayer p) {
        if (Platform.isServer()) {
            if (p.isSneaking()) {
                IConfigManager cm = getConfigManager(is);
                final Enum<?> newState = Platform.rotateEnum(
                        cm.getSetting(Settings.NETWORK_VISUALISER),
                        false,
                        Settings.NETWORK_VISUALISER.getPossibleValues());
                cm.putSetting(Settings.NETWORK_VISUALISER, newState);
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolNetworkVisualiser.set",
                                EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolNetworkVisualiser.mode."
                                                + newState.toString().toLowerCase(Locale.US))));
            }
        }
        return is;
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer p, World w, int x, int y, int z, int side, float xOff,
            float yOff, float zOff) {
        if (Platform.isServer()) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof IGridHost) {
                DimensionalCoord dc = new DimensionalCoord(te);
                dc.writeToNBT(is.getTagCompound());
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolNetworkVisualiser.bound",
                                String.valueOf(dc.x),
                                String.valueOf(dc.y),
                                String.valueOf(dc.z))
                                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
                needUpdate = true;
                return true;
            }
        }
        return false;
    }

    ArrayList<IGridConnection> gcListCache = new ArrayList<>();
    ItemStack currItem;

    @Override
    public void onUpdate(ItemStack is, World w, Entity entity, int slot, boolean active) {
        if (Platform.isClient() || !(entity instanceof EntityPlayerMP player) || is.getTagCompound() == null) {
            return;
        }
        ItemStack currentItem = player.inventory.getCurrentItem();
        if (currentItem != currItem) {
            currItem = currentItem;
            needUpdate = true;
        }
        if (currentItem == null || !(currentItem.getItem() instanceof ToolNetworkVisualiser) || !needUpdate) {
            return;
        }
        currItem = currentItem;
        DimensionalCoord dc = DimensionalCoord.readFromNBT(is.getTagCompound());
        if (w.provider.dimensionId != dc.getDimension()) return;
        TileEntity te = w.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof IGridHost gh) {
            IGridNode gn = gh.getGridNode(ForgeDirection.UNKNOWN);
            if (gn != null) {
                IGrid g = gn.getGrid();
                if (g != null) {
                    Map<IGridNode, VNode> vnList = new HashMap<>();

                    ArrayList<IGridConnection> gcList = new ArrayList<>();
                    for (IGridNode igNode : g.getNodes()) {
                        IGridBlock igb = igNode.getGridBlock();
                        if (igb.isWorldAccessible() && igb.getLocation().isInWorld(w)) {
                            DimensionalCoord loc = igb.getLocation();
                            for (IGridConnection igc : igNode.getConnections()) {
                                gcList.add(igc);
                            }
                            EnumSet<VNodeFlags> flags = EnumSet.noneOf(VNodeFlags.class);
                            if (!igNode.meetsChannelRequirements()) flags.add(VNodeFlags.MISSING);
                            if (igNode.hasFlag(GridFlags.DENSE_CAPACITY)) {
                                flags.add(VNodeFlags.DENSE);
                            }
                            vnList.put(igNode, new VNode(loc.x, loc.y, loc.z, flags));
                        }
                    }

                    if (gcList.equals(gcListCache)) {
                        needUpdate = false;
                        return;
                    }

                    this.gcListCache = gcList;

                    ArrayList<VLink> vLinks = new ArrayList<>();
                    for (IGridConnection c : gcList) {
                        VNode n1 = vnList.get(c.a());
                        VNode n2 = vnList.get(c.b());
                        if (n1 != null && n2 != null && n1 != n2) {
                            EnumSet<VLinkFlags> flags = EnumSet.noneOf(VLinkFlags.class);
                            if (c.a().hasFlag(GridFlags.DENSE_CAPACITY) && c.b().hasFlag(GridFlags.DENSE_CAPACITY)) {
                                flags.add(VLinkFlags.DENSE);
                            }
                            if (c.a().hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)
                                    && c.b().hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED)) {
                                flags.add(VLinkFlags.COMPRESSED);
                            }
                            vLinks.add(new VLink(n1, n2, c.getUsedChannels(), flags));
                        }
                    }

                    try {
                        ArrayList<VNode> vNodeList = new ArrayList<>(vnList.values());
                        NetworkHandler.instance
                                .sendTo(new PacketNetworkVisualiserData(vNodeList, vLinks), (EntityPlayerMP) entity);
                        needUpdate = false;
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public static IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.NETWORK_VISUALISER, VisualisationModes.FULL);

        out.readFromNBT((NBTTagCompound) Platform.openNbtData(target).copy());
        return out;
    }

    @Override
    protected void addCheckedInformation(ItemStack is, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        lines.add(
                StatCollector.translateToLocal("item.appliedenergistics2.ToolNetworkVisualiser.mode") + " "
                        + EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal(
                                "item.appliedenergistics2.ToolNetworkVisualiser.mode." + getConfigManager(is)
                                        .getSetting(Settings.NETWORK_VISUALISER).toString().toLowerCase(Locale.US)));
    }
}
