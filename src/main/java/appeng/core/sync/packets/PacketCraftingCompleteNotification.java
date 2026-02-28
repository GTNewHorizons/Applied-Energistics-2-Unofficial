package appeng.core.sync.packets;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;

import org.apache.commons.lang3.time.DurationFormatUtils;

import appeng.api.storage.data.IAEStack;
import appeng.client.render.notification.Notification;
import appeng.client.render.notification.NotificationManager;
import appeng.core.localization.GuiText;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.me.cluster.implementations.CraftingCPUCluster.CraftNotification;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingCompleteNotification extends AppEngPacket {

    private final IAEStack<?> stack;
    private final long numsOfOutput;
    private final long elapsedTime;

    public PacketCraftingCompleteNotification(final ByteBuf stream) throws IOException {
        this.stack = Platform.readStackByte(stream);
        this.numsOfOutput = stream.readLong();
        this.elapsedTime = stream.readLong();
    }

    public PacketCraftingCompleteNotification(CraftNotification notification) throws IOException {
        this(notification.getFinalOutput(), notification.getOutputsCount(), notification.getElapsedTime());
    }

    public PacketCraftingCompleteNotification(IAEStack<?> stack, long numsOfOutput, long elapsedTime)
            throws IOException {
        this.stack = stack;
        this.numsOfOutput = numsOfOutput;
        this.elapsedTime = elapsedTime;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        Platform.writeStackByte(this.stack, data);

        data.writeLong(this.numsOfOutput);
        data.writeLong(this.elapsedTime);

        this.configureWrite(data);
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        String elapsedTimeText = DurationFormatUtils.formatDuration(
                TimeUnit.MILLISECONDS.convert(this.elapsedTime, TimeUnit.NANOSECONDS),
                GuiText.ETAFormat.getLocal());

        String notificationDescription = StatCollector.translateToLocalFormatted(
                "chat.appliedenergistics2.CraftComplete.tip",
                this.numsOfOutput,
                this.stack.getDisplayName(),
                elapsedTimeText);

        NotificationManager.getGuiNotification().queueNotification(
                new Notification(
                        this.stack,
                        StatCollector.translateToLocal("chat.appliedenergistics2.CraftComplete"),
                        notificationDescription));
    }
}
