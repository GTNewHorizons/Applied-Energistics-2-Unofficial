package appeng.container.implementations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import appeng.container.guisync.IGuiPacketWritable;
import io.netty.buffer.ByteBuf;

public class ReshuffleState implements IGuiPacketWritable {

    public final boolean running;
    public final boolean failed;
    public final boolean cancelled;
    public final boolean complete;
    public final boolean extracting;
    public final int totalItems;
    public final int processedItems;
    public final int progressPercent;
    public final int phaseProcessed;
    public final int phaseTotal;
    public final int typeCount;
    public final String reportData;

    public static final ReshuffleState IDLE = new ReshuffleState(
            false,
            false,
            false,
            false,
            false,
            0,
            0,
            0,
            0,
            0,
            0,
            "");

    public ReshuffleState(boolean running, boolean failed, boolean cancelled, boolean complete, boolean extracting,
            int totalItems, int processedItems, int progressPercent, int phaseProcessed, int phaseTotal, int typeCount,
            String reportData) {
        this.running = running;
        this.failed = failed;
        this.cancelled = cancelled;
        this.complete = complete;
        this.extracting = extracting;
        this.totalItems = totalItems;
        this.processedItems = processedItems;
        this.progressPercent = progressPercent;
        this.phaseProcessed = phaseProcessed;
        this.phaseTotal = phaseTotal;
        this.typeCount = typeCount;
        this.reportData = reportData == null ? "" : reportData;
    }

    public ReshuffleState(final ByteBuf buf) {
        this.running = buf.readBoolean();
        this.failed = buf.readBoolean();
        this.cancelled = buf.readBoolean();
        this.complete = buf.readBoolean();
        this.extracting = buf.readBoolean();
        this.totalItems = buf.readInt();
        this.processedItems = buf.readInt();
        this.progressPercent = buf.readInt();
        this.phaseProcessed = buf.readInt();
        this.phaseTotal = buf.readInt();
        this.typeCount = buf.readInt();
        final int len = buf.readInt();
        final byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        this.reportData = new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        buf.writeBoolean(running);
        buf.writeBoolean(failed);
        buf.writeBoolean(cancelled);
        buf.writeBoolean(complete);
        buf.writeBoolean(extracting);
        buf.writeInt(totalItems);
        buf.writeInt(processedItems);
        buf.writeInt(progressPercent);
        buf.writeInt(phaseProcessed);
        buf.writeInt(phaseTotal);
        buf.writeInt(typeCount);
        final byte[] bytes = reportData.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public List<String> getReportLines() {
        if (reportData.isEmpty()) return new ArrayList<>();
        return Arrays.asList(reportData.split("\n", -1));
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReshuffleState o)) return false;
        return running == o.running && failed == o.failed
                && cancelled == o.cancelled
                && complete == o.complete
                && extracting == o.extracting
                && totalItems == o.totalItems
                && processedItems == o.processedItems
                && progressPercent == o.progressPercent
                && phaseProcessed == o.phaseProcessed
                && phaseTotal == o.phaseTotal
                && typeCount == o.typeCount
                && Objects.equals(reportData, o.reportData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                running,
                failed,
                cancelled,
                complete,
                extracting,
                totalItems,
                processedItems,
                progressPercent,
                phaseProcessed,
                phaseTotal,
                typeCount,
                reportData);
    }
}
