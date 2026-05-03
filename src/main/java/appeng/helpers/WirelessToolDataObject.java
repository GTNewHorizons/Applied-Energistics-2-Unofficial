package appeng.helpers;

import java.util.ArrayList;
import java.util.List;

import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class WirelessToolDataObject {

    public final DimensionalCoord network;
    public final String customName;
    public final DimensionalCoord cord;
    public boolean isConnected;
    public final List<DimensionalCoord> targets;
    public final AEColor color;
    public final int channels;
    public final boolean isHub;
    public final int slots;

    public WirelessToolDataObject(DimensionalCoord network, String name, DimensionalCoord cord, boolean isConnected,
            List<DimensionalCoord> targets, AEColor color, int channels, boolean isHub, int slots) {
        this.network = network;
        this.customName = name;
        this.cord = cord;
        this.isConnected = isConnected;
        this.targets = targets;
        this.color = color;
        this.channels = channels;
        this.isHub = isHub;
        this.slots = slots;
    }

    public void write(ByteBuf buf) {
        this.network.writeToPacket(buf);
        ByteBufUtils.writeUTF8String(buf, this.customName);
        this.cord.writeToPacket(buf);
        buf.writeBoolean(this.isConnected);
        buf.writeBoolean(this.isHub);

        buf.writeInt(this.targets.size());
        this.targets.forEach((dc) -> dc.writeToPacket(buf));

        buf.writeInt(this.color.ordinal());
        buf.writeInt(this.channels);
        buf.writeInt(this.slots);
    }

    public static WirelessToolDataObject read(ByteBuf buf) {
        final DimensionalCoord network = DimensionalCoord.readFromPacket(buf);
        final String customName = ByteBufUtils.readUTF8String(buf);
        final DimensionalCoord coord = DimensionalCoord.readFromPacket(buf);
        final boolean isConnected = buf.readBoolean();
        final boolean isHub = buf.readBoolean();

        final int targetsSize = buf.readInt();
        final List<DimensionalCoord> targets = new ArrayList<>(targetsSize);
        for (int i = 0; i < targetsSize; i++) targets.add(DimensionalCoord.readFromPacket(buf));

        return new WirelessToolDataObject(
                network,
                customName,
                coord,
                isConnected,
                targets,
                AEColor.values()[buf.readInt()],
                buf.readInt(),
                isHub,
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
