package appeng.core.sync.packets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;

import appeng.client.render.NetworkVisualiserRender;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.items.tools.ToolNetworkVisualiser.VLink;
import appeng.items.tools.ToolNetworkVisualiser.VLinkFlags;
import appeng.items.tools.ToolNetworkVisualiser.VNode;
import appeng.items.tools.ToolNetworkVisualiser.VNodeFlags;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class PacketNetworkVisualiserData extends AppEngPacket {

    private final ArrayList<VNode> vNodeSet;
    private final ArrayList<VLink> vLinkSet;

    // automatic.
    public PacketNetworkVisualiserData(final ByteBuf stream) throws IOException {
        int vNodeCount = stream.readInt();
        int vLinkCount = stream.readInt();

        this.vNodeSet = new ArrayList<>();
        for (int i = 0; i < vNodeCount; i++) {
            int x = stream.readInt();
            int y = stream.readInt();
            int z = stream.readInt();
            int flagsCount = stream.readInt();

            EnumSet<VNodeFlags> flags = EnumSet.noneOf(VNodeFlags.class);
            for (int j = 0; j < flagsCount; j++) {
                int flag = stream.readInt();
                flags.add(VNodeFlags.values()[flag]);
            }
            this.vNodeSet.add(new VNode(x, y, z, flags));
        }

        this.vLinkSet = new ArrayList<>();
        for (int i = 0; i < vLinkCount; i++) {
            int n1 = stream.readInt();
            int n2 = stream.readInt();
            int c = stream.readInt();
            int flagsCount = stream.readInt();

            EnumSet<VLinkFlags> flags = EnumSet.noneOf(VLinkFlags.class);
            for (int j = 0; j < flagsCount; j++) {
                int flag = stream.readInt();
                flags.add(VLinkFlags.values()[flag]);
            }
            this.vLinkSet.add(new VLink(this.vNodeSet.get(n1), this.vNodeSet.get(n2), c, flags));
        }
    }

    // api
    public PacketNetworkVisualiserData(ArrayList<VNode> vNodeSet, ArrayList<VLink> vLinkSet) throws IOException {
        this.vNodeSet = vNodeSet;
        this.vLinkSet = vLinkSet;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        data.writeInt(vNodeSet.size());
        data.writeInt(vLinkSet.size());

        for (VNode node : vNodeSet) {
            data.writeInt(node.x);
            data.writeInt(node.y);
            data.writeInt(node.z);
            data.writeInt(node.flags.size());
            for (VNodeFlags f : node.flags) {
                data.writeInt(f.ordinal());
            }
        }

        // Avoid O(nodes * links) ArrayList.indexOf lookups while serializing large visualisations.
        Object2IntOpenHashMap<VNode> nodeIndexes = new Object2IntOpenHashMap<>();
        nodeIndexes.defaultReturnValue(-1);
        for (int i = 0; i < vNodeSet.size(); i++) {
            VNode node = vNodeSet.get(i);
            if (!nodeIndexes.containsKey(node)) {
                nodeIndexes.put(node, i);
            }
        }

        for (VLink vl : vLinkSet) {
            int node1Index = nodeIndexes.getInt(vl.node1);
            int node2Index = nodeIndexes.getInt(vl.node2);
            if (node1Index < 0 || node2Index < 0) {
                throw new IOException("Network visualiser link references a node that is not in the node list");
            }

            data.writeInt(node1Index);
            data.writeInt(node2Index);
            data.writeInt(vl.channels);
            data.writeInt(vl.flags.size());
            for (VLinkFlags f : vl.flags) {
                data.writeInt(f.ordinal());
            }
        }

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        NetworkVisualiserRender.networkVisualiser(this.vNodeSet, this.vLinkSet);
    }
}
