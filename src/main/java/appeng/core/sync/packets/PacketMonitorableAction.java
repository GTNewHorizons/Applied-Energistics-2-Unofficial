package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;

import appeng.container.ContainerOpenContext;
import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.MonitorableAction;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketMonitorableAction extends AppEngPacket {

    private final MonitorableAction action;
    private final int custom;

    @SuppressWarnings("unused")
    public PacketMonitorableAction(final ByteBuf stream) {
        this.action = MonitorableAction.values()[stream.readInt()];
        this.custom = stream.readInt();
    }

    public PacketMonitorableAction(final MonitorableAction action, final int custom) {
        this.action = action;
        this.custom = custom;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(action.ordinal());
        data.writeInt(custom);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerMEMonitorable container)) return;

        if (action == MonitorableAction.AUTO_CRAFT) {
            final PrimaryGui pGui = container.createPrimaryGui();
            final ContainerOpenContext context = container.getOpenContext();
            if (context != null) {
                final TileEntity te = context.getTile();

                Platform.openGUI(
                        player,
                        te,
                        context.getSide(),
                        GuiBridge.GUI_CRAFTING_AMOUNT,
                        container.getTargetSlotIndex());

                if (player.openContainer instanceof ContainerCraftAmount cca) {
                    if (container.getTargetStack() != null) {
                        cca.setPrimaryGui(pGui);
                        cca.setItemToCraft(container.getTargetStack());
                        cca.setInitialCraftAmount(1);
                    }

                    cca.detectAndSendChanges();
                }
            }
            return;
        }

        container.doMonitorableAction(action, custom, (EntityPlayerMP) player);
    }
}
