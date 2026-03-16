package appeng.container.implementations;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import appeng.container.guisync.IGuiPacketWritable;
import io.netty.buffer.ByteBuf;

public class ScanState implements IGuiPacketWritable {

    public final String scanData;

    public static final ScanState EMPTY = new ScanState("");

    public ScanState(final String scanData) {
        this.scanData = scanData == null ? "" : scanData;
    }

    public ScanState(final ByteBuf buf) {
        final int len = buf.readInt();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        this.scanData = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        final byte[] bytes = scanData.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScanState o)) return false;
        return Objects.equals(scanData, o.scanData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(scanData);
    }
}
