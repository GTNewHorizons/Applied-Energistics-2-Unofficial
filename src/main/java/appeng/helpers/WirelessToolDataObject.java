package appeng.helpers;

import java.util.ArrayList;
import java.util.List;

import appeng.api.util.AEColor;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class WirelessToolDataObject {

    public final WirelessAnchor network;
    public final String customName;
    public final WirelessAnchor cord;
    public boolean isConnected;
    public final List<WirelessAnchor> targets;
    public final AEColor color;
    public final int channels;
    public final boolean isHub;
    public final boolean external;
    public final int slots;

    public WirelessToolDataObject(WirelessAnchor network, String name, WirelessAnchor cord, boolean isConnected,
            List<WirelessAnchor> targets, AEColor color, int channels, boolean isHub, boolean external, int slots) {
        this.network = network;
        this.customName = name;
        this.cord = cord;
        this.isConnected = isConnected;
        this.targets = targets;
        this.color = color;
        this.channels = channels;
        this.isHub = isHub;
        this.external = external;
        this.slots = slots;
    }

    public void write(ByteBuf buf) {
        this.network.writeToPacket(buf);
        ByteBufUtils.writeUTF8String(buf, this.customName);
        this.cord.writeToPacket(buf);
        buf.writeBoolean(this.isConnected);
        buf.writeBoolean(this.isHub);
        buf.writeBoolean(this.external);

        buf.writeInt(this.targets.size());
        this.targets.forEach((anchor) -> anchor.writeToPacket(buf));

        buf.writeInt(this.color.ordinal());
        buf.writeInt(this.channels);
        buf.writeInt(this.slots);
    }

    public static WirelessToolDataObject read(ByteBuf buf) {
        final WirelessAnchor network = WirelessAnchor.readFromPacket(buf);
        final String customName = ByteBufUtils.readUTF8String(buf);
        final WirelessAnchor cord = WirelessAnchor.readFromPacket(buf);
        final boolean isConnected = buf.readBoolean();
        final boolean isHub = buf.readBoolean();
        final boolean external = buf.readBoolean();

        final int targetsSize = buf.readInt();
        final List<WirelessAnchor> targets = new ArrayList<>(targetsSize);
        for (int i = 0; i < targetsSize; i++) targets.add(WirelessAnchor.readFromPacket(buf));

        return new WirelessToolDataObject(
                network,
                customName,
                cord,
                isConnected,
                targets,
                AEColor.VALUES[buf.readInt()],
                buf.readInt(),
                isHub,
                external,
                buf.readInt());
    }

    public static void writeAsList(ArrayList<WirelessToolDataObject> list, ByteBuf buf) {
        buf.writeInt(list.size());
        list.forEach(d -> d.write(buf));
    }

    public static ArrayList<WirelessToolDataObject> readAsList(ByteBuf buf) {
        final int size = buf.readInt();
        final ArrayList<WirelessToolDataObject> arrayList = new ArrayList<>(size);
        for (int x = 0; x < size; x++) arrayList.add(WirelessToolDataObject.read(buf));

        return arrayList;
    }
}
