package appeng.core.sync.packets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
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

public class PacketNetworkVisualiserData extends AppEngPacket {

    public static class VisualisationData {

        public static final int VERSION = 1;
    }

    private ArrayList<VNode> vNodeSet;
    private ArrayList<VLink> vLinkSet;

    // automatic.
    public PacketNetworkVisualiserData(final ByteBuf stream) throws IOException {
        int ver = stream.readInt();
        if (ver != VisualisationData.VERSION) {
            return; // error
        }
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

        data.writeInt(VisualisationData.VERSION);
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

        for (VLink vl : vLinkSet) {
            data.writeInt(vNodeSet.indexOf(vl.node1));
            data.writeInt(vNodeSet.indexOf(vl.node2));
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
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        NetworkVisualiserRender.networkVisualiser(this.vNodeSet, this.vLinkSet);
    }
}
