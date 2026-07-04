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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a32;
import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

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
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputSlave;

public class ToolNetworkVisualiser extends AEBaseItem {

    private static final int UPDATE_INTERVAL_TICKS = 100;
    private static final Map<EntityPlayerMP, VisualisationUpdate> LAST_UPDATES = new WeakHashMap<>();

    public ToolNetworkVisualiser() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    public enum VNodeFlags {
        DENSE,
        MISSING,
        PROXY
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
            this.flags = flags.clone();
        }

        public boolean isAtSameLocation(VNode other) {
            return this.x == other.x && this.y == other.y && this.z == other.z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof VNode other)) return false;
            return this.x == other.x && this.y == other.y && this.z == other.z && this.flags.equals(other.flags);
        }

        @Override
        public int hashCode() {
            int hash = Fnv1a32.initialState();
            hash = Fnv1a32.hashStep(hash, this.x);
            hash = Fnv1a32.hashStep(hash, this.y);
            hash = Fnv1a32.hashStep(hash, this.z);
            hash = Fnv1a32.hashStep(hash, this.flags.hashCode());
            return hash;
        }
    }

    public enum VLinkFlags {
        DENSE,
        COMPRESSED,
        PROXY
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
            this.flags = flags.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof VLink other)) return false;
            boolean sameDir = this.node1.equals(other.node1) && this.node2.equals(other.node2);
            boolean oppositeDir = this.node1.equals(other.node2) && this.node2.equals(other.node1);
            return this.channels == other.channels && this.flags.equals(other.flags) && (sameDir || oppositeDir);
        }

        @Override
        public int hashCode() {
            int nodeHash1 = this.node1.hashCode();
            int nodeHash2 = this.node2.hashCode();
            if (nodeHash1 > nodeHash2) {
                int tmp = nodeHash1;
                nodeHash1 = nodeHash2;
                nodeHash2 = tmp;
            }

            int hash = Fnv1a32.initialState();
            hash = Fnv1a32.hashStep(hash, this.channels);
            hash = Fnv1a32.hashStep(hash, this.flags.hashCode());
            hash = Fnv1a32.hashStep(hash, nodeHash1);
            hash = Fnv1a32.hashStep(hash, nodeHash2);
            return hash;
        }
    }

    public enum VisualisationModes {
        FULL,
        NODES,
        CHANNELS,
        NONUM,
        NODES_ONE_CHANNEL,
        ONE_CHANNEL,
        P2P,
        PROXY
    }

    private static class VisualisationUpdate {

        private final int x;
        private final int y;
        private final int z;
        private final int dim;
        private final long tick;

        private VisualisationUpdate(DimensionalCoord pos, long tick) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.dim = pos.getDimension();
            this.tick = tick;
        }

        private boolean matches(DimensionalCoord pos) {
            return this.x == pos.x && this.y == pos.y && this.z == pos.z && this.dim == pos.getDimension();
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack is, World worldIn, EntityPlayer p) {
        if (Platform.isServer()) {
            if (p.isSneaking()) {
                final IConfigManager cm = getConfigManager(is);
                Enum<?> newState = getNextVisualisationMode(cm);
                if (!Platform.isGTLoaded && newState == VisualisationModes.PROXY)
                    newState = getNextVisualisationMode(cm);
                cm.putSetting(Settings.NETWORK_VISUALISER, newState);
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolNetworkVisualiser.set",
                                StatCollector.translateToLocal(
                                        "item.appliedenergistics2.ToolNetworkVisualiser.mode."
                                                + newState.toString().toLowerCase(Locale.US))));
            }
        }
        return is;
    }

    private Enum<?> getNextVisualisationMode(IConfigManager cm) {
        return Platform.rotateEnum(
                cm.getSetting(Settings.NETWORK_VISUALISER),
                false,
                Settings.NETWORK_VISUALISER.getPossibleValues());
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer p, World w, int x, int y, int z, int side, float xOff,
            float yOff, float zOff) {
        if (Platform.isServer()) {
            TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof IGridHost) {
                if (!is.hasTagCompound()) is.setTagCompound(new NBTTagCompound());
                DimensionalCoord dc = new DimensionalCoord(te);
                dc.writeToNBT(is.getTagCompound());
                p.addChatMessage(
                        new ChatComponentTranslation(
                                "item.appliedenergistics2.ToolNetworkVisualiser.bound",
                                dc.getGuiTextShortNoDim()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void onUpdate(ItemStack is, World w, Entity entity, int slot, boolean active) {
        if (!active || Platform.isClient()
                || !(entity instanceof EntityPlayerMP player)
                || is.getTagCompound() == null) {
            return;
        }

        DimensionalCoord dc = DimensionalCoord.readFromNBT(is.getTagCompound());
        if (w.provider.dimensionId != dc.getDimension()) return;
        if (!needToUpdate(player, dc)) return;

        ArrayList<VLink> vLinks = new ArrayList<>();
        Map<IGridNode, VNode> vnList = new HashMap<>();
        ArrayList<VNode> vNodeList = new ArrayList<>();

        TileEntity te = w.getTileEntity(dc.x, dc.y, dc.z);
        if (te instanceof IGridHost gh) {
            IGridNode gn = gh.getGridNode(ForgeDirection.UNKNOWN);
            if (gn != null) {
                IGrid g = gn.getGrid();
                if (g != null) {
                    Set<IGridConnection> gcList = new HashSet<>();
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

                            VNode node = new VNode(loc.x, loc.y, loc.z, flags);
                            vnList.put(igNode, node);

                            if (Platform.isGTLoaded && igb.getMachine() instanceof MTEHatchCraftingInputME crib) {
                                EnumSet<VNodeFlags> flagsNode = EnumSet.of(VNodeFlags.PROXY);
                                EnumSet<VLinkFlags> flagsLink = EnumSet.of(VLinkFlags.PROXY);
                                for (MTEHatchCraftingInputSlave s : crib.getProxyHatches()) {
                                    final IGregTechTileEntity sb = s.getBaseMetaTileEntity();
                                    final VNode sbNode = new VNode(
                                            sb.getXCoord(),
                                            sb.getYCoord(),
                                            sb.getZCoord(),
                                            flagsNode);
                                    vLinks.add(new VLink(node, sbNode, 0, flagsLink));
                                    vNodeList.add(sbNode);
                                }
                            }
                        }
                    }

                    for (IGridConnection c : gcList) {
                        VNode n1 = vnList.get(c.a());
                        VNode n2 = vnList.get(c.b());
                        if (n1 != null && n2 != null && !n1.isAtSameLocation(n2)) {
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
                }
            }
        }

        try {
            vNodeList.addAll(vnList.values());
            NetworkHandler.instance.sendTo(new PacketNetworkVisualiserData(vNodeList, vLinks), player);
        } catch (IOException ignored) {}
    }

    private static boolean needToUpdate(EntityPlayerMP player, DimensionalCoord pos) {
        long now = player.worldObj.getTotalWorldTime();
        VisualisationUpdate last = LAST_UPDATES.get(player);
        if (last != null && last.matches(pos) && last.tick >= now - UPDATE_INTERVAL_TICKS) {
            return false;
        }

        LAST_UPDATES.put(player, new VisualisationUpdate(pos, now));
        return true;
    }

    public static IConfigManager getConfigManager(final ItemStack target) {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = ItemStackNBT.get(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.NETWORK_VISUALISER, VisualisationModes.FULL);

        out.readFromNBT((NBTTagCompound) ItemStackNBT.get(target).copy());
        return out;
    }

    @Override
    protected void addCheckedInformation(ItemStack is, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        lines.add(
                StatCollector.translateToLocal("item.appliedenergistics2.ToolNetworkVisualiser.mode") + " "
                        + StatCollector.translateToLocal(
                                "item.appliedenergistics2.ToolNetworkVisualiser.mode." + getConfigManager(is)
                                        .getSetting(Settings.NETWORK_VISUALISER).toString().toLowerCase(Locale.US)));
    }
}
