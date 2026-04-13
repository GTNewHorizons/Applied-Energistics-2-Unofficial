package appeng.container.implementations;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import appeng.container.guisync.IGuiPacketWritable;
import io.netty.buffer.ByteBuf;

public class HealthState implements IGuiPacketWritable {

    public final String healthData;

    public static final HealthState EMPTY = new HealthState("");

    public HealthState(final String healthData) {
        this.healthData = healthData == null ? "" : healthData;
    }

    public HealthState(final ByteBuf buf) {
        final int len = buf.readInt();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        this.healthData = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        final byte[] bytes = healthData.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HealthState o)) return false;
        return Objects.equals(healthData, o.healthData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(healthData);
    }
}
